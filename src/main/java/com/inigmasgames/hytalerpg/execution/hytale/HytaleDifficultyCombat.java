package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.diagnostics.*;
import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.progress.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Native projection of the durable encounter snapshot. No file IO or second damage/Health owner. */
public final class HytaleDifficultyCombat {
    public static final String HEALTH_KEY="RPG_DIFFICULTY_MAX";
    private record Key(UUID world,UUID enemy){}
    private record OwnedPlate(String previous,String shown){}
    private static final class Entry { final Object ticket=new Object(); volatile EnemyRewardRegistry.Spawn spawn; }
    private final Map<Key,Entry> active=new ConcurrentHashMap<>();
    private final Map<Key,OwnedPlate> plates=new ConcurrentHashMap<>();
    private final Set<String> nameplateWarnings=ConcurrentHashMap.newKeySet();
    private final RpgSkillTracer trace;
    public HytaleDifficultyCombat(RpgSkillTracer trace){this.trace=Objects.requireNonNull(trace);}
    public synchronized Object begin(UUID world,UUID enemy){
        var key=new Key(world,enemy);if(active.size()>=EncounterContributions.MAX_ENCOUNTERS&&!active.containsKey(key))throw new IllegalStateException("DIFFICULTY_PROJECTION_CAPACITY");
        var entry=new Entry();active.put(key,entry);return entry.ticket;
    }
    public void detach(UUID world,UUID enemy){
        var key=new Key(world,enemy);active.remove(key);
        var owned=plates.remove(key);if(owned==null)return;
        var nativeWorld=Universe.get().getWorld(world);if(nativeWorld==null)return;
        try{nativeWorld.execute(()->{
            if(plates.containsKey(key))return; // A newer projection owns this entity now.
            var store=nativeWorld.getEntityStore().getStore();var ref=store.getExternalData().getRefFromUUID(enemy);
            if(ref==null||!ref.isValid())return;
            var current=store.getComponent(ref,Nameplate.getComponentType());
            if(current==null||!owned.shown().equals(current.getText()))return;
            if(owned.previous()==null)store.removeComponent(ref,Nameplate.getComponentType());
            else current.setText(owned.previous());
        });}catch(RuntimeException worldClosing){/* Presentation cannot block encounter teardown. */}
    }
    /** Entity removal needs no native restore; discard presentation ownership immediately. */
    public void forget(UUID world,UUID enemy){var key=new Key(world,enemy);active.remove(key);plates.remove(key);}
    public Optional<EnemyRewardRegistry.Spawn> snapshot(UUID world,UUID enemy){var e=active.get(new Key(world,enemy));return Optional.ofNullable(e==null?null:e.spawn);}
    public List<EnemyRewardRegistry.Spawn> snapshots(UUID world){return active.entrySet().stream().filter(e->e.getKey().world().equals(world)).map(e->e.getValue().spawn).filter(Objects::nonNull).sorted(Comparator.comparing(s->s.enemy().toString())).toList();}
    /** Called on the owning world only, after the journal has durably created/restored this spawn. */
    public void ready(Store<EntityStore> store,UUID enemy,Object ticket,Optional<EnemyRewardRegistry.Spawn> saved,boolean fresh){
        UUID world=store.getExternalData().getWorld().getWorldConfig().getUuid();var key=new Key(world,enemy);var entry=active.get(key);
        if(entry==null||entry.ticket!=ticket)return;
        var ref=store.getExternalData().getRefFromUUID(enemy);
        if(ref==null||!ref.isValid()||HytaleEncounterRewards.excluded(store,ref)||saved.isEmpty()||saved.get().combat()==null){active.remove(key,entry);return;}
        var spawn=saved.get();var npc=store.getComponent(ref,NPCEntity.getComponentType());
        if(!spawn.roleId().equals(npc.getRoleName())){active.remove(key,entry);return;}
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());
        if(stats==null)throw new IllegalStateException("DIFFICULTY_NATIVE_HEALTH_MISSING");
        var healthProjection=store.getComponent(ref,DifficultyHealthProjection.getComponentType());
        double savedHealth=healthProjection==null?Double.NaN:healthProjection.healthFor(spawn);
        projectHealth(stats,spawn.combat(),fresh);
        if(!fresh&&Double.isFinite(savedHealth))stats.setStatValue(DefaultEntityStatTypes.getHealth(),(float)Math.min(savedHealth,stats.get(DefaultEntityStatTypes.getHealth()).getMax()));
        if(healthProjection==null){
            healthProjection=new DifficultyHealthProjection(world,enemy,spawn.registryProfile(),stats.get(DefaultEntityStatTypes.getHealth()).get());
            healthProjection.follow(stats);store.addComponent(ref,DifficultyHealthProjection.getComponentType(),healthProjection);
        }else healthProjection.follow(stats);
        DifficultyNativeProbe.freezeRegenerationInIsolatedProbe(store,ref);
        try{presentNameplate(store,ref,key,npc,spawn);}
        catch(RuntimeException presentationFailure){
            if(nameplateWarnings.add(spawn.roleId()))
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                        "RPG_ENEMY_NAMEPLATE_FAILED role=%s error=%s",spawn.roleId(),presentationFailure.toString());
        }
        entry.spawn=spawn;
        trace.trace(RpgTraceRecord.create(null,RpgTraceEventType.DIFFICULTY_PROFILE_APPLIED,enemy.toString(),Map.of(
                "world",world,"enemy",enemy,"difficulty",spawn.combat().difficulty(),"sourceCombatLevel",spawn.level(),"profile",spawn.registryProfile(),
                "health",stats.get(DefaultEntityStatTypes.getHealth()).get(),"maximum",stats.get(DefaultEntityStatTypes.getHealth()).getMax(),
                "immunities",spawn.combat().resistance().immunities(),"freshSpawn",fresh,"connectedProof",false)));
    }
    private void presentNameplate(Store<EntityStore> store,Ref<EntityStore> ref,Key key,NPCEntity npc,EnemyRewardRegistry.Spawn spawn){
        var allegiance=store.getComponent(ref,WorldSupport.getComponentType());
        if(allegiance==null||allegiance.getDefaultPlayerAttitude()!=Attitude.HOSTILE)return;
        String display=null,keyName=npc.getRole()==null?null:npc.getRole().getNameTranslationKey();
        if(keyName!=null&&!keyName.isBlank())display=I18nModule.get().getMessage("en-US",keyName);
        if(display==null||display.isBlank()){
            var persistent=store.getComponent(ref,PersistentDisplayName.getComponentType());
            if(persistent!=null&&persistent.getDisplayName()!=null)display=persistent.getDisplayName().getRawText();
        }
        if(display==null||display.isBlank()||display.startsWith("server.")||display.contains("_"))return;
        String shown=EnemyNameplateText.format(display,spawn.level());
        var plate=store.getComponent(ref,Nameplate.getComponentType());
        if(plates.size()>=EncounterContributions.MAX_ENCOUNTERS&&!plates.containsKey(key))return;
        String prior=plate==null?null:plate.getText();
        // Older difficulty builds put a raw role ID on immunity plates; never restore that text.
        if(prior!=null&&(prior.startsWith(spawn.roleId()+" [Immune:")||prior.equals(shown)))prior=null;
        var previous=plates.get(key);
        if(plate==null)store.addComponent(ref,Nameplate.getComponentType(),new Nameplate(shown));
        else if(!shown.equals(plate.getText()))plate.setText(shown);
        plates.put(key,new OwnedPlate(previous==null?prior:previous.previous(),shown));
    }
    public static void projectHealth(EntityStatMap stats,EncounterProfileResolver.Resolved profile,boolean fresh){
        int index=DefaultEntityStatTypes.getHealth();var hp=stats.get(index);
        if(hp==null)throw new IllegalStateException("DIFFICULTY_NATIVE_HEALTH_MISSING");
        float before=hp.get(),maximum=hp.getMax();var old=stats.getModifier(index,HEALTH_KEY);
        if(old!=null&&(!(old instanceof StaticModifier m)||m.getTarget()!=Modifier.ModifierTarget.MAX||m.getCalculationType()!=StaticModifier.CalculationType.ADDITIVE))
            throw new IllegalStateException("DIFFICULTY_MODIFIER_OWNERSHIP_CONFLICT");
        double prior=old==null?0:((StaticModifier)old).getAmount();
        if(Math.abs(maximum-prior-profile.nativeHealthBaseline())>.02)throw new IllegalStateException("DIFFICULTY_NATIVE_BASELINE_MISMATCH:"+maximum+":"+profile.nativeHealthBaseline());
        float addition=(float)(profile.maxHealth()-profile.nativeHealthBaseline());
        if(addition!=0||old!=null){stats.putModifier(index,HEALTH_KEY,new StaticModifier(Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.ADDITIVE,addition));stats.update();}
        // Only an untouched new spawn may start full. Reload/reapply never restores a wound.
        stats.setStatValue(index,fresh&&old==null&&before>=maximum?(float)profile.maxHealth():Math.min(before,hp.getMax()));
    }
    public static MonsterResistanceProfile.Channel channel(String cause){return switch(cause){
        case "Fire"->MonsterResistanceProfile.Channel.FIRE;case "Ice"->MonsterResistanceProfile.Channel.COLD;
        case "Lightning"->MonsterResistanceProfile.Channel.LIGHTNING;case "Wind"->MonsterResistanceProfile.Channel.WIND;
        case "RPG_Void"->MonsterResistanceProfile.Channel.VOID;case "RPG_Nature"->MonsterResistanceProfile.Channel.NATURE;
        case "RPG_Necrotic"->MonsterResistanceProfile.Channel.NECROTIC;case "Poison"->MonsterResistanceProfile.Channel.POISON;default->null;};}
    private Entry entry(Store<EntityStore> store,Ref<EntityStore> ref){
        if(ref==null||!ref.isValid())return null;var id=store.getComponent(ref,UUIDComponent.getComponentType());if(id==null)return null;
        var key=new Key(store.getExternalData().getWorld().getWorldConfig().getUuid(),id.getUuid());var value=active.get(key);if(value==null)return null;
        if(HytaleEncounterRewards.excluded(store,ref)||value.spawn!=null&&!value.spawn.roleId().equals(store.getComponent(ref,NPCEntity.getComponentType()).getRoleName())){active.remove(key,value);return null;}
        return value;
    }
    private void trace(Damage damage,EnemyRewardRegistry.Spawn spawn,double before,String operation){
        var metadata=HytaleDamageAdapter.metadata(damage);
        trace.trace(RpgTraceRecord.create(metadata==null?null:metadata.actorId(),RpgTraceEventType.DIFFICULTY_DAMAGE_RESOLVED,metadata==null?spawn.enemy().toString():metadata.correlationId(),Map.of(
                "world",spawn.world(),"enemy",spawn.enemy(),"profile",spawn.registryProfile(),"difficulty",spawn.combat().difficulty(),"sourceCombatLevel",spawn.level(),
                "cause",damage.getCause().getId(),"before",before,"after",damage.getAmount(),"operation",operation,"cancelled",damage.isCancelled())));
    }
    private static final com.hypixel.hytale.server.core.meta.MetaKey<Boolean> OUTGOING=Damage.META_REGISTRY.registerMetaObject(v->false,false,"InigmasGames:DifficultyOutgoing",Codec.BOOLEAN);
    private static final com.hypixel.hytale.server.core.meta.MetaKey<Boolean> INCOMING=Damage.META_REGISTRY.registerMetaObject(v->false,false,"InigmasGames:DifficultyIncoming",Codec.BOOLEAN);
    /** Includes ProjectileSource: native source.getRef() is the shooter, not the projectile carrier. */
    public final class Outgoing extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return EntityStatMap.getComponentType();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getGatherDamageGroup()),new SystemGroupDependency<>(Order.BEFORE,DamageModule.get().getFilterDamageGroup()));}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            try(var span=NativeRpgTickMetrics.enter(store,NativeRpgTickMetrics.Phase.DAMAGE)){
            if(damage.isCancelled()||damage.getAmount()<=0||Boolean.TRUE.equals(damage.getIfPresentMetaObject(OUTGOING)))return;
            damage.putMetaObject(OUTGOING,true);
            var target=entry(store,chunk.getReferenceTo(i));if(target!=null&&target.spawn==null){damage.setCancelled(true);return;}
            if(!(damage.getSource() instanceof Damage.EntitySource source)||HytaleDamageAdapter.metadata(damage)!=null)return;
            var entry=entry(store,source.getRef());if(entry==null)return;
            if(entry.spawn==null){damage.setCancelled(true);return;}
            var cause=damage.getCause();if(cause==null||cause.doesBypassResistances()||channel(cause.getId())==null&&!Set.of("Physical","Projectile").contains(cause.getId()))return;
            double before=damage.getAmount(),amount=before*entry.spawn.combat().difficultyDamageFactor();
            if(!Double.isFinite(amount)||amount>Float.MAX_VALUE){damage.setCancelled(true);return;}
            damage.setAmount((float)amount);trace(damage,entry.spawn,before,"NATIVE_OUTGOING_DIFFICULTY_ONCE");
            }
        }
    }
    /** Native mitigation precedes authored extra resistance; shields/caps see the resulting amount. */
    public final class Resistance extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return Query.and(NPCEntity.getComponentType(),EntityStatMap.getComponentType());}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),
                new SystemDependency<>(Order.BEFORE,SupportDamageSystems.HealthCap.class),new SystemDependency<>(Order.BEFORE,SupportDamageSystems.Shield.class),
                new SystemDependency<>(Order.BEFORE,HytaleDamageLifecycleSystems.Filter.class),new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            try(var span=NativeRpgTickMetrics.enter(store,NativeRpgTickMetrics.Phase.DAMAGE)){
            if(damage.isCancelled()||damage.getAmount()<=0||Boolean.TRUE.equals(damage.getIfPresentMetaObject(INCOMING)))return;
            damage.putMetaObject(INCOMING,true);var entry=entry(store,chunk.getReferenceTo(i));if(entry==null)return;
            if(entry.spawn==null){damage.setCancelled(true);return;}
            var cause=damage.getCause();if(cause==null||cause.doesBypassResistances())return;
            var channel=channel(cause.getId());if(channel==null)return;
            var resistance=entry.spawn.combat().resistance();if(resistance.effective(channel)==0&&!resistance.immunities().contains(channel))return;
            double before=damage.getAmount();var result=resistance.resolve(channel,before);damage.setAmount((float)result.amount());
            if(resistance.immunities().contains(channel))damage.setCancelled(true);
            trace(damage,entry.spawn,before,result.reason());
            }
        }
    }
}
