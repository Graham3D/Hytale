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
    private static final class Entry { final Object ticket=new Object(); volatile EnemyRewardRegistry.Spawn spawn;
        volatile com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto enemyDisplay;
        volatile EnemyState enemyState; volatile com.inigmasgames.hytalerpg.enemies.EnemyPackRecord enemyPack;
        long lastPackReceipts=-1; }
    public record EnemyState(com.inigmasgames.hytalerpg.enemies.EnemyDescriptor descriptor,
            com.inigmasgames.hytalerpg.enemies.EnemyAffixSnapshot providers){}
    private final Map<Key,Entry> active=new ConcurrentHashMap<>();
    // Shared status APIs identify actors by their native UUID. Values alias the existing projection entry.
    private final Map<UUID,Entry> statusActors=new ConcurrentHashMap<>();
    private final NativeHostileNames names=new NativeHostileNames(this);
    public NativeHostileNames names(){return names;}
    boolean hasColoredPromotedName(UUID world,UUID actor,String text){
        return healthBars.hasColoredName(world,actor,text);
    }
    private final EnemyHealthBarPresentation healthBars=new EnemyHealthBarPresentation();
    public EnemyHealthBarPresentation healthBars(){return healthBars;}
    private final Set<String> nameplateWarnings=ConcurrentHashMap.newKeySet();
    private static final Set<String> nameFallbacks=ConcurrentHashMap.newKeySet();
    private final Set<UUID> quarantinedEnemyWorlds=ConcurrentHashMap.newKeySet();
    private final RpgSkillTracer trace;
    private java.util.function.BiConsumer<UUID,UUID> enemyDisplayChanged=(world,entity)->{};
    private java.util.function.BiConsumer<Store<EntityStore>,com.inigmasgames.hytalerpg.enemies.EnemyPackRecord> enemyPackChanged=(store,pack)->{};
    public void configureEnemyDisplayObserver(java.util.function.BiConsumer<UUID,UUID> observer){enemyDisplayChanged=Objects.requireNonNull(observer);}
    public void configureEnemyPackObserver(java.util.function.BiConsumer<Store<EntityStore>,com.inigmasgames.hytalerpg.enemies.EnemyPackRecord> observer){
        enemyPackChanged=Objects.requireNonNull(observer);
    }
    public Optional<com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto> enemyDisplay(UUID world,UUID entity){
        var entry=active.get(new Key(world,entity));return Optional.ofNullable(entry==null||entry.spawn==null?null:entry.enemyDisplay);
    }
    public Optional<EnemyState> enemyState(UUID world,UUID entity){
        var entry=active.get(new Key(world,entity));return Optional.ofNullable(entry==null||entry.spawn==null?null:entry.enemyState);
    }
    public boolean qaNativeHealthbar(UUID world,UUID entity){return healthBars.qaNativeEnabled(world,entity);}
    public void verifyQaNativeHealthbar(Store<EntityStore> store,Ref<EntityStore> entity,UUID id){
        healthBars.verifyQaNative(store,entity,id);
    }
    /** An uncertain ME writer/runtime result closes the existing native protection and action owners. */
    public void quarantineEnemyWorld(UUID world){quarantinedEnemyWorlds.add(Objects.requireNonNull(world));}
    /** The complete sealed pack, not a nearby-actor query, owns suspension and engagement admission. */
    public void bindEnemyPack(Store<EntityStore> store,com.inigmasgames.hytalerpg.enemies.EnemyDescriptor descriptor,
            com.inigmasgames.hytalerpg.enemies.EnemyPackRecord pack){
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(descriptor.worldId()))
            throw new IllegalStateException("ENEMY_PACK_BIND_THREAD");
        var entry=active.get(new Key(descriptor.worldId(),descriptor.entityId()));
        if(entry==null||entry.spawn==null||entry.enemyState==null||!entry.enemyState.descriptor().equals(descriptor)
                ||pack==null||!pack.packId().equals(descriptor.packId())||!pack.worldId().equals(descriptor.worldId())
                ||pack.generation()!=descriptor.encounterGeneration()||!pack.contains(descriptor.logicalActorId()))
            throw new IllegalStateException("ENEMY_PACK_BIND_IDENTITY");
        entry.enemyPack=pack;entry.lastPackReceipts=pack.deadMemberReceipts().size();
    }
    public Optional<com.inigmasgames.hytalerpg.enemies.EnemyPackRecord> enemyPack(UUID world,UUID entity){
        var entry=active.get(new Key(world,entity));return Optional.ofNullable(entry==null||entry.spawn==null?null:entry.enemyPack);
    }
    /** Capture the existing native difficulty/support and ME direct providers at action acceptance. */
    public NativeEnemyActions.SourceState enemySourceState(Store<EntityStore> store,Ref<EntityStore> source,
            com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects effects){
        if(!store.isInThread()||source==null||!source.isValid()||source.getStore()!=store)
            throw new IllegalStateException("ENEMY_SOURCE_SNAPSHOT_WORLD_THREAD");
        var entry=entry(store,source);
        if(entry==null||entry.spawn==null||entry.enemyState==null||entry.enemyPack==null)
            throw new IllegalStateException("ENEMY_SOURCE_SNAPSHOT_NOT_PUBLISHED");
        var actor=entry.enemyState.descriptor();var pack=entry.enemyPack;
        if(actor.spawnOrigin()!=com.inigmasgames.hytalerpg.enemies.EnemyRewardContext.Origin.QA
                &&quarantinedEnemyWorlds.contains(actor.worldId()))throw new IllegalStateException("ENEMY_SOURCE_WORLD_QUARANTINED");
        if(!actor.entityId().equals(entry.spawn.enemy())||!actor.worldId().equals(entry.spawn.world())
                ||!pack.packId().equals(actor.packId())||!pack.contains(actor.logicalActorId())
                ||pack.state()!=com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.GUARDED
                    &&pack.state()!=com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.RELEASED)
            throw new IllegalStateException("ENEMY_SOURCE_SNAPSHOT_PACK_ADMISSION");
        double now=System.nanoTime()/1e9;
        double nativeSupport=effects.nativeOutgoingFactor(actor.worldId(),actor.entityId(),now);
        double directWeakening=effects.winningStat(actor.worldId(),actor.logicalActorId(),actor.encounterGeneration(),
                com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.Stat.DIRECT_WEAKEN,now);
        return new NativeEnemyActions.SourceState(entry.enemyState.providers(),
                entry.spawn.combat().difficultyDamageFactor(),nativeSupport,directWeakening);
    }
    private EnemyState statusActor(UUID entity){
        var entry=statusActors.get(entity);var state=entry==null?null:entry.enemyState;
        return state!=null&&entry.spawn!=null&&active.get(new Key(state.descriptor().worldId(),entity))==entry?state:null;
    }
    public boolean statusImmune(UUID target,String status){
        var state=statusActor(target);
        return state!=null&&state.providers().stunStaggerImmune()&&(status.equals("STUN")||status.equals("STAGGER"));
    }
    public boolean slowImmune(UUID target){var state=statusActor(target);return state!=null&&state.providers().slowImmune();}
    public boolean allowsExternalMutation(UUID source,UUID target){var state=statusActor(target);return state==null
            ||(state.descriptor().spawnOrigin()==com.inigmasgames.hytalerpg.enemies.EnemyRewardContext.Origin.QA
                ||!quarantinedEnemyWorlds.contains(state.descriptor().worldId()))&&!state.providers().blocksExternalMutation();}
    public boolean blocksConversion(UUID world,UUID target){
        var entry=active.get(new Key(world,target));var state=entry==null?null:entry.enemyState;
        return state!=null&&entry.spawn!=null&&((state.descriptor().spawnOrigin()!=com.inigmasgames.hytalerpg.enemies.EnemyRewardContext.Origin.QA
                &&quarantinedEnemyWorlds.contains(world))||state.providers().blocksConversion());
    }
    private void remove(Key key,Entry expected){
        if(expected!=null&&active.remove(key,expected))statusActors.remove(key.enemy(),expected);
    }
    /** Current native baseline is collected by the armor owner; this supplies only shared rating providers. */
    public NativeEnemyArmor.Physical physicalDefense(Store<EntityStore> store,Ref<EntityStore> target,
            com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects effects){
        return physicalDefense(store,target,effects,true);
    }
    /** The operator view uses the same provider calculation without pruning combat attachments. */
    public NativeEnemyArmor.Physical physicalDefenseForInspection(Store<EntityStore> store,Ref<EntityStore> target,
            com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects effects){
        return physicalDefense(store,target,effects,false);
    }
    private NativeEnemyArmor.Physical physicalDefense(Store<EntityStore> store,Ref<EntityStore> target,
            com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects effects,boolean prune){
        var entry=entry(store,target,prune);var state=entry==null?null:entry.enemyState;
        if(entry==null||entry.spawn==null)return null;
        if(state==null){
            // Ordinary registered NPCs have the same authoritative level and native armor owner.
            double now=System.nanoTime()/1e9;
            double broken=prune?effects.winningStat(entry.spawn.world(),entry.spawn.enemy(),0,
                    com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.Stat.DEFENSE_BREAK,now)
                    :effects.peekWinningStat(entry.spawn.world(),entry.spawn.enemy(),0,
                    com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.Stat.DEFENSE_BREAK,now);
            var progression=entry.spawn.combat().progression();
            double baseline=progression==null?0:progression.defenseRating();
            return baseline==0&&broken==0?null:new NativeEnemyArmor.Physical(entry.spawn.level(),
                    new com.inigmasgames.hytalerpg.combat.defense.DefenseView.Contributions(0,baseline,0,broken),false);
        }
        var actor=state.descriptor();
        double now=System.nanoTime()/1e9;
        double broken=prune?effects.winningStat(actor.worldId(),actor.logicalActorId(),actor.encounterGeneration(),
                com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.Stat.DEFENSE_BREAK,now)
                :effects.peekWinningStat(actor.worldId(),actor.logicalActorId(),actor.encounterGeneration(),
                com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.Stat.DEFENSE_BREAK,now);
        var progression=entry.spawn.combat().progression();
        double baseline=progression==null?0:progression.defenseRating();
        var contributions=state.providers().defenseContributions(new com.inigmasgames.hytalerpg.combat.defense.DefenseView.Contributions(0,baseline,0,broken));
        boolean immune=actor.nativeImmunityChannels().contains("PHYSICAL")
                ||actor.selectedImmunityGrants().stream().anyMatch(grant->grant.channel().equals("PHYSICAL"));
        return new NativeEnemyArmor.Physical(actor.combatLevel(),contributions,immune);
    }
    public NativeEnemyArmor.Elemental elementalDefense(Store<EntityStore> store,Ref<EntityStore> target,Damage damage){
        return elementalDefense(store,target,true);
    }
    public NativeEnemyArmor.Elemental elementalDefenseForInspection(Store<EntityStore> store,Ref<EntityStore> target){
        return elementalDefense(store,target,false);
    }
    private NativeEnemyArmor.Elemental elementalDefense(Store<EntityStore> store,Ref<EntityStore> target,boolean prune){
        var entry=entry(store,target,prune);var state=entry==null?null:entry.enemyState;
        if(entry==null||entry.spawn==null||state==null&&entry.spawn.combat().progression()==null)return null;
        var additions=new EnumMap<MonsterResistanceProfile.Channel,Double>(MonsterResistanceProfile.Channel.class);
        if(state!=null)state.providers().resistanceAdds().forEach((channel,value)->additions.put(MonsterResistanceProfile.Channel.valueOf(channel),value));
        var flags=EnumSet.noneOf(MonsterResistanceProfile.Channel.class);
        if(state!=null)for(String channel:state.descriptor().activeImmuneChannels())if(!channel.equals("PHYSICAL"))flags.add(MonsterResistanceProfile.Channel.valueOf(channel));
        // The saved encounter profile holds birth-time native/authored/affinity floors. No collision-time reroll/reclassification.
        // Current combat owners expose no elemental penetration provider; no unimplemented gear affix is enabled here.
        return new NativeEnemyArmor.Elemental(entry.spawn.combat().resistance(),additions,flags,0);
    }
    /** Dynamic providers cannot change the frozen birth Health factors or refill native Health. */
    public void updateEnemyProviders(Store<EntityStore> store,com.inigmasgames.hytalerpg.enemies.EnemyDescriptor descriptor,
            com.inigmasgames.hytalerpg.enemies.EnemyAffixSnapshot providers){
        if(!store.isInThread())throw new IllegalStateException("ENEMY_PROVIDER_WRONG_WORLD_THREAD");
        if(!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(descriptor.worldId()))throw new IllegalStateException("ENEMY_PROVIDER_WORLD");
        var entry=active.get(new Key(descriptor.worldId(),descriptor.entityId()));var previous=entry==null?null:entry.enemyState;
        if(previous==null||entry.spawn==null||!previous.descriptor().equals(descriptor))throw new IllegalStateException("ENEMY_PROVIDER_BINDING_NOT_READY");
        if(previous.providers().rarityHealthFactor()!=providers.rarityHealthFactor()||previous.providers().maxHealthIncrease()!=providers.maxHealthIncrease()
                ||previous.providers().rarityDirectFactor()!=providers.rarityDirectFactor())throw new IllegalStateException("ENEMY_BIRTH_FACTORS_CHANGED");
        entry.enemyState=new EnemyState(descriptor,providers);
    }
    /** A durable roster may overtake an earlier queued death callback; never project it backwards. */
    public void refreshEnemyPack(Store<EntityStore> store,com.inigmasgames.hytalerpg.enemies.EnemyPackRecord pack){
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(pack.worldId()))
            throw new IllegalStateException("ENEMY_PACK_REFRESH_WORLD");
        for(var member:pack.birthRoster()){
            var key=new Key(pack.worldId(),member.nativeEntityId());var entry=active.get(key);
            if(entry==null||entry.spawn==null||entry.enemyState==null)continue;
            var actor=entry.enemyState.descriptor();
            if(!actor.logicalActorId().equals(member.logicalActorId())||!Objects.equals(actor.packId(),pack.packId())
                    ||actor.encounterGeneration()!=pack.generation())throw new IllegalStateException("ENEMY_PACK_REFRESH_IDENTITY");
            if(entry.enemyPack!=null&&entry.enemyPack.deadMemberReceipts().size()>pack.deadMemberReceipts().size())
                throw new IllegalStateException("ENEMY_PACK_REFRESH_ROLLBACK");
            var previousPack=entry.enemyPack;
            entry.enemyPack=pack;
            // Suspension/resumption changes protection and presentation even when no member died.
            boolean stateChanged=previousPack==null||previousPack.state()!=pack.state()
                    ||previousPack.packboundReleased()!=pack.packboundReleased();
            if(!stateChanged&&entry.lastPackReceipts>=pack.deadMemberReceipts().size())continue;
            var ref=store.getExternalData().getRefFromUUID(member.nativeEntityId());
            if(ref==null||!ref.isValid())continue;
            var next=entry.enemyState.providers().withPackProtection(actor,pack,
                    com.inigmasgames.hytalerpg.enemies.EnemyBalance.forRevision(actor.balanceRevision()));
            if(!next.equals(entry.enemyState.providers()))updateEnemyProviders(store,actor,next);
            var previous=entry.enemyDisplay;
            if(previous!=null){
                var nativeStatuses=new TreeSet<String>();
                for(var tag:previous.activeDefenseTags())if(tag.sourceKind()==com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto.SourceKind.STATUS_IMMUNITY
                        &&!tag.sourceId().equals("STUN_STAGGER")&&!tag.sourceId().equals("SLOW_MOVEMENT"))nativeStatuses.add(tag.sourceId());
                var npc=store.getComponent(ref,NPCEntity.getComponentType());
                boolean invulnerable=store.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.Invulnerable.getComponentType())!=null
                        ||npc!=null&&npc.getRole()!=null&&npc.getRole().isInvulnerable();
                var shield=store.getComponent(ref,com.inigmasgames.hytalerpg.enemies.EnemyShieldProjection.getComponentType());
                double remaining=shield==null?0:shield.snapshot().remaining();
                var display=com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto.project(actor,
                        com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.canonical(),next,pack,
                        Math.addExact(previous.stateRevision(),1),previous.name(),previous.baseRoleDisplayName(),
                        nativeStatuses,invulnerable,remaining);
                publishEnemyDisplay(store,actor,display,true);
            }
            entry.lastPackReceipts=pack.deadMemberReceipts().size();
        }
        enemyPackChanged.accept(store,pack);
    }
    /** Dynamic ME providers change only the current view; accepted strikes retain their older snapshot. */
    public void refreshEnemyEngagement(Store<EntityStore> store,com.inigmasgames.hytalerpg.enemies.EnemyDescriptor actor,
            com.inigmasgames.hytalerpg.enemies.EnemyAffixSnapshot next){
        var key=new Key(actor.worldId(),actor.entityId());var entry=active.get(key);
        if(!store.isInThread()||entry==null||entry.enemyPack==null||entry.enemyState==null
                ||!entry.enemyState.descriptor().equals(actor)||entry.spawn==null)
            throw new IllegalStateException("ENEMY_ENGAGEMENT_PROJECTION_BINDING");
        var prior=entry.enemyState.providers();
        if(prior.equals(next))return;
        updateEnemyProviders(store,actor,next);
        var previous=entry.enemyDisplay;
        if(previous==null)return;
        var ref=store.getExternalData().getRefFromUUID(actor.entityId());
        if(ref==null||!ref.isValid())return;
        var nativeStatuses=new TreeSet<String>();
        for(var tag:previous.activeDefenseTags())if(tag.sourceKind()==com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto.SourceKind.STATUS_IMMUNITY
                &&!tag.sourceId().equals("STUN_STAGGER")&&!tag.sourceId().equals("SLOW_MOVEMENT"))nativeStatuses.add(tag.sourceId());
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        boolean invulnerable=store.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.Invulnerable.getComponentType())!=null
                ||npc!=null&&npc.getRole()!=null&&npc.getRole().isInvulnerable();
        var shield=store.getComponent(ref,com.inigmasgames.hytalerpg.enemies.EnemyShieldProjection.getComponentType());
        double remaining=shield==null?0:shield.snapshot().remaining();
        var display=com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto.project(actor,
                com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.canonical(),next,entry.enemyPack,
                Math.addExact(previous.stateRevision(),1),previous.name(),previous.baseRoleDisplayName(),
                nativeStatuses,invulnerable,remaining);
        publishEnemyDisplay(store,actor,display,false);
    }
    /** Same nameplate writer as ordinary difficulty. Called after durable birth/rebind or authoritative state change. */
    public void publishEnemyDisplay(Store<EntityStore> store,com.inigmasgames.hytalerpg.enemies.EnemyDescriptor descriptor,
            com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto display,boolean urgentProtectionChange){
        if(!store.isInThread())throw new IllegalStateException("ENEMY_DISPLAY_WRONG_WORLD_THREAD");
        UUID world=store.getExternalData().getWorld().getWorldConfig().getUuid();var key=new Key(world,descriptor.entityId());var entry=active.get(key);
        if(entry==null||entry.spawn==null||!world.equals(descriptor.worldId())||!descriptor.logicalActorId().equals(display.logicalActorId())
                ||!display.worldId().equals(world)||display.generation()!=descriptor.encounterGeneration()||display.descriptorRevision()!=descriptor.descriptorRevision()
                ||!display.currentHealthRef().nativeEntityId().equals(descriptor.entityId())||!entry.spawn.roleId().equals(descriptor.nativeRoleId())
                ||entry.spawn.level()!=display.combatLevel())throw new IllegalStateException("ENEMY_DISPLAY_BINDING_NOT_READY");
        var previous=entry.enemyDisplay;
        if(previous!=null&&(!display.logicalActorId().equals(previous.logicalActorId())||display.generation()<previous.generation()||display.generation()==previous.generation()
                &&(display.stateRevision()<previous.stateRevision()||display.descriptorRevision()!=previous.descriptorRevision()
                ||display.stateRevision()==previous.stateRevision()&&!display.equals(previous))))
            throw new IllegalStateException("ENEMY_DISPLAY_STALE_REVISION");
        var ref=store.getExternalData().getRefFromUUID(descriptor.entityId());
        if(ref==null||!ref.isValid()||!HytaleSupportSystem.alive(store,ref))return;
        var npc=store.getComponent(ref,NPCEntity.getComponentType());if(npc==null||!npc.getRoleName().equals(descriptor.nativeRoleId()))return;
        entry.enemyDisplay=display;
        boolean anchored=false;
        try{anchored=healthBars.presentPromotedAnchors(store,ref,descriptor.entityId(),display);}
        catch(RuntimeException failure){warnPresentation(npc.getRoleName()+" native anchors",failure);}
        try{presentNameplate(store,ref,key,npc,entry.spawn,anchored);}
        catch(RuntimeException failure){
            if(anchored)healthBars.removePromotedAnchors(store,descriptor.entityId());
            warnPresentation(npc.getRoleName(),failure);
        }
          // Promoted names use per-viewer mounted glyph packets; affixes retain native Nameplate anchors.
        // Only the native Healthbar follows the damaging viewer's three-second window.
        if(urgentProtectionChange)notifyEnemyDisplay(world,descriptor.entityId());
    }
    public HytaleDifficultyCombat(RpgSkillTracer trace){
        this.trace=Objects.requireNonNull(trace);
        healthBars.configureNativeNameRestore(names::request);
    }
    public synchronized Object begin(UUID world,UUID enemy){
        var key=new Key(world,enemy);if(active.size()>=EncounterContributions.MAX_ENCOUNTERS&&!active.containsKey(key))throw new IllegalStateException("DIFFICULTY_PROJECTION_CAPACITY");
        var entry=new Entry();var previous=active.put(key,entry);if(previous!=null)statusActors.remove(enemy,previous);return entry.ticket;
    }
    public void detach(UUID world,UUID enemy){
        var key=new Key(world,enemy);remove(key,active.get(key));
        boolean qaNative=healthBars.qaNativeEnabled(world,enemy);
        healthBars.forget(world,enemy);
        notifyEnemyDisplay(world,enemy);
        var nativeWorld=Universe.get().getWorld(world);if(nativeWorld==null)return;
        try{nativeWorld.execute(()->{
            var store=nativeWorld.getEntityStore().getStore();var ref=store.getExternalData().getRefFromUUID(enemy);
            if(ref==null||!ref.isValid())return;
            if(qaNative)healthBars.restoreDetachedQaNative(store,ref);
            names.request(store,ref); // Recompose current state; never restore an obsolete blank plate.
        });}catch(RuntimeException worldClosing){/* Presentation cannot block encounter teardown. */}
    }
    public void forget(UUID world,UUID enemy){var key=new Key(world,enemy);remove(key,active.get(key));names.forget(world,enemy);healthBars.forget(world,enemy);notifyEnemyDisplay(world,enemy);}
    private void notifyEnemyDisplay(UUID world,UUID enemy){
        try{enemyDisplayChanged.accept(world,enemy);}
        catch(RuntimeException failure){warnPresentation("target-card",failure);}
    }
    private void warnPresentation(String role,RuntimeException failure){
        if(nameplateWarnings.add(role))com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                "RPG_ENEMY_NAMEPLATE_FAILED role=%s error=%s",role,failure.toString());
    }
    public Optional<EnemyRewardRegistry.Spawn> snapshot(UUID world,UUID enemy){var e=active.get(new Key(world,enemy));return Optional.ofNullable(e==null?null:e.spawn);}
    public List<EnemyRewardRegistry.Spawn> snapshots(UUID world){return active.entrySet().stream().filter(e->e.getKey().world().equals(world)).map(e->e.getValue().spawn).filter(Objects::nonNull).sorted(Comparator.comparing(s->s.enemy().toString())).toList();}
    /** Called on the owning world only, after the journal has durably created/restored this spawn. */
    public void ready(Store<EntityStore> store,UUID enemy,Object ticket,Optional<EnemyRewardRegistry.Spawn> saved,boolean fresh){
        ready(store,enemy,ticket,saved,fresh,null,null);
    }
    public void ready(Store<EntityStore> store,UUID enemy,Object ticket,Optional<EnemyRewardRegistry.Spawn> saved,boolean fresh,
            com.inigmasgames.hytalerpg.enemies.EnemyDescriptor descriptor,com.inigmasgames.hytalerpg.enemies.EnemyAffixSnapshot providers){
        if(!store.isInThread())throw new IllegalStateException("DIFFICULTY_PROJECTION_WRONG_WORLD_THREAD");
        UUID world=store.getExternalData().getWorld().getWorldConfig().getUuid();var key=new Key(world,enemy);var entry=active.get(key);
        if(entry==null||entry.ticket!=ticket)return;
        var ref=store.getExternalData().getRefFromUUID(enemy);
        if(ref==null||!ref.isValid()||HytaleEncounterRewards.excluded(store,ref)||saved.isEmpty()||saved.get().combat()==null){remove(key,entry);return;}
        var spawn=saved.get();var npc=store.getComponent(ref,NPCEntity.getComponentType());
        if(!spawn.roleId().equals(npc.getRoleName())){remove(key,entry);return;}
        if((descriptor==null)!=(providers==null)||descriptor==null&&spawn.enemyRewards()!=null)
            throw new IllegalStateException("ENEMY_HEALTH_DESCRIPTOR_REQUIRED");
        if(descriptor!=null&&(!descriptor.worldId().equals(world)||!descriptor.entityId().equals(enemy)||!descriptor.nativeRoleId().equals(spawn.roleId())
                ||descriptor.combatLevel()!=spawn.level()||descriptor.difficulty()!=spawn.combat().difficulty()
                ||!descriptor.immutableRewardContext().equals(spawn.enemyRewards())))throw new IllegalStateException("ENEMY_HEALTH_DESCRIPTOR_MISMATCH");
        if(descriptor!=null){
            var previous=statusActors.putIfAbsent(enemy,entry);
            if(previous!=null&&previous!=entry)throw new IllegalStateException("ENEMY_NATIVE_UUID_ALREADY_BOUND");
        }
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());
        if(stats==null)throw new IllegalStateException("DIFFICULTY_NATIVE_HEALTH_MISSING");
        var healthProjection=store.getComponent(ref,DifficultyHealthProjection.getComponentType());
        double savedHealth=healthProjection==null?Double.NaN:healthProjection.healthFor(spawn);
        if(npc.getRole()==null)throw new IllegalStateException("DIFFICULTY_NATIVE_ROLE_MISSING");
        projectHealth(stats,spawn.combat(),providers,fresh,npc.getRole().getInitialMaxHealth());
        if(!fresh&&Double.isFinite(savedHealth))stats.setStatValue(DefaultEntityStatTypes.getHealth(),(float)Math.min(savedHealth,stats.get(DefaultEntityStatTypes.getHealth()).getMax()));
        if(healthProjection==null){
            healthProjection=new DifficultyHealthProjection(world,enemy,spawn.registryProfile(),stats.get(DefaultEntityStatTypes.getHealth()).get());
            healthProjection.follow(stats);store.addComponent(ref,DifficultyHealthProjection.getComponentType(),healthProjection);
        }else healthProjection.follow(stats);
        DifficultyNativeProbe.freezeRegenerationInIsolatedProbe(store,ref);
        try{presentNameplate(store,ref,key,npc,spawn,false);}
        catch(RuntimeException presentationFailure){warnPresentation(spawn.roleId(),presentationFailure);}
        entry.enemyState=descriptor==null?null:new EnemyState(descriptor,providers);
        entry.spawn=spawn;
        trace.trace(RpgTraceRecord.create(null,RpgTraceEventType.DIFFICULTY_PROFILE_APPLIED,enemy.toString(),Map.of(
                "world",world,"enemy",enemy,"difficulty",spawn.combat().difficulty(),"sourceCombatLevel",spawn.level(),"profile",spawn.registryProfile(),
                "health",stats.get(DefaultEntityStatTypes.getHealth()).get(),"maximum",stats.get(DefaultEntityStatTypes.getHealth()).getMax(),
                "immunities",spawn.combat().resistance().immunities(),"freshSpawn",fresh,"connectedProof",false)));
    }
    private void presentNameplate(Store<EntityStore> store,Ref<EntityStore> ref,Key key,NPCEntity npc,
                                  EnemyRewardRegistry.Spawn spawn,boolean anchored){
        var allegiance=store.getComponent(ref,WorldSupport.getComponentType());
        if(allegiance!=null&&allegiance.getDefaultPlayerAttitude()==Attitude.HOSTILE)healthBars.hideByDefault(store,ref,key.enemy());
        names.request(store,ref);
    }
    public void presentNativeHostileName(Store<EntityStore> store,Ref<EntityStore> ref){names.request(store,ref);}
    /** One native display-name resolution path for ordinary plates and frozen ME birth names. */
    public static String nativeDisplayName(Store<EntityStore> store,Ref<EntityStore> ref,NPCEntity npc){
        var personal=store.getComponent(ref,PersistentDisplayName.getComponentType());
        if(personal!=null&&personal.getDisplayName()!=null){
            String text=personal.getDisplayName().getRawText();if(text!=null&&!text.isBlank())return text;
        }
        String display=null,keyName=npc.getRole()==null?null:npc.getRole().getNameTranslationKey();
        if(keyName!=null&&!keyName.isBlank()&&I18nModule.get()!=null)display=I18nModule.get().getMessage("en-US",keyName);
        if(display==null||display.isBlank()||display.startsWith("server.")||display.contains("_")){
            var persistent=store.getComponent(ref,PersistentDisplayName.getComponentType());
            if(persistent!=null&&persistent.getDisplayName()!=null)display=persistent.getDisplayName().getRawText();
        }
        if(display==null||display.isBlank()||display.startsWith("server.")||display.contains("_")){
            if(nameFallbacks.add(npc.getRoleName()))com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                    "RPG_ENEMY_NAME_FALLBACK role=%s nativeKey=%s",npc.getRoleName(),keyName);
            return npc.getRoleName().replace('_',' ');
        }
        return display;
    }
    public static void projectHealth(EntityStatMap stats,EncounterProfileResolver.Resolved profile,boolean fresh){
        projectHealth(stats,profile,null,fresh);
    }
    /** Compose ME into the existing difficulty modifier, not another modifier or Health store. */
    public static void projectHealth(EntityStatMap stats,EncounterProfileResolver.Resolved profile,
            com.inigmasgames.hytalerpg.enemies.EnemyAffixSnapshot providers,boolean fresh){
        projectHealth(stats,profile,providers,fresh,profile.nativeHealthBaseline());
    }
    /** The frozen profile is the target, not evidence of the SDK's resolved variant chassis.
     * Validate the actual Role baseline so unrelated modifiers still fail closed. */
    public static void projectHealth(EntityStatMap stats,EncounterProfileResolver.Resolved profile,
            com.inigmasgames.hytalerpg.enemies.EnemyAffixSnapshot providers,boolean fresh,double nativeBaseline){
        if(!Double.isFinite(nativeBaseline)||nativeBaseline<=0)
            throw new IllegalStateException("DIFFICULTY_NATIVE_ROLE_BASELINE_INVALID");
        double resolvedMaximum=providers==null?profile.maxHealth():providers.projectedMaximumHealth(profile.maxHealth());
        int index=DefaultEntityStatTypes.getHealth();var hp=stats.get(index);
        if(hp==null)throw new IllegalStateException("DIFFICULTY_NATIVE_HEALTH_MISSING");
        float before=hp.get(),maximum=hp.getMax();var old=stats.getModifier(index,HEALTH_KEY);
        if(old!=null&&(!(old instanceof StaticModifier m)||m.getTarget()!=Modifier.ModifierTarget.MAX||m.getCalculationType()!=StaticModifier.CalculationType.ADDITIVE))
            throw new IllegalStateException("DIFFICULTY_MODIFIER_OWNERSHIP_CONFLICT");
        double prior=old==null?0:((StaticModifier)old).getAmount();
        if(Math.abs(maximum-prior-nativeBaseline)>.02)throw new IllegalStateException("DIFFICULTY_NATIVE_BASELINE_MISMATCH:"+maximum+":"+nativeBaseline);
        float addition=(float)(resolvedMaximum-nativeBaseline);
        if(addition!=0||old!=null){stats.putModifier(index,HEALTH_KEY,new StaticModifier(Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.ADDITIVE,addition));stats.update();}
        // Only an untouched new spawn may start full. Reload/reapply never restores a wound.
        stats.setStatValue(index,fresh&&old==null&&before>=maximum?(float)resolvedMaximum:Math.min(before,hp.getMax()));
    }
    public static MonsterResistanceProfile.Channel channel(String cause){
        var mapping=com.inigmasgames.hytalerpg.combat.damage.DamageChannels.canonical().resolve(cause,null);
        return mapping.channel()==null||mapping.channel()==com.inigmasgames.hytalerpg.combat.damage.DamageChannels.Channel.PHYSICAL
                ?null:MonsterResistanceProfile.Channel.valueOf(mapping.channel().name());
    }
    /** Frozen direct-hit penetration for the same channel handled by the NPC resistance filter. */
    public static double penetration(Damage damage){
        var gear=HytaleDamageAdapter.gearHit(damage);
        if(gear==null||HytaleDamageAdapter.eligibleResistanceChannel(damage)!=gear.channel())return 0;
        return gear.hit().penetration(gear.channel());
    }
    private Entry entry(Store<EntityStore> store,Ref<EntityStore> ref){return entry(store,ref,true);}
    private Entry entry(Store<EntityStore> store,Ref<EntityStore> ref,boolean prune){
        if(ref==null||!ref.isValid())return null;var id=store.getComponent(ref,UUIDComponent.getComponentType());if(id==null)return null;
        var key=new Key(store.getExternalData().getWorld().getWorldConfig().getUuid(),id.getUuid());var value=active.get(key);if(value==null)return null;
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        if(HytaleEncounterRewards.excluded(store,ref)||value.spawn!=null&&(npc==null||!value.spawn.roleId().equals(npc.getRoleName()))){
            if(prune)remove(key,value);return null;
        }
        return value;
    }
    public boolean blocksIncoming(Store<EntityStore> store,Ref<EntityStore> target){
        if(target==null||!target.isValid()||target.getStore()!=store)return true;
        var staging=EnemyStaging.getComponentType();
        if(staging!=null&&store.getComponent(target,staging)!=null)return true;
        var identity=com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.getComponentType();
        if(identity!=null&&store.getComponent(target,identity)!=null){
            var id=store.getComponent(target,UUIDComponent.getComponentType());
            var state=id==null?null:enemyState(store.getExternalData().getWorld().getWorldConfig().getUuid(),id.getUuid()).orElse(null);
            if(quarantinedEnemyWorlds.contains(store.getExternalData().getWorld().getWorldConfig().getUuid())
                    &&(state==null||state.descriptor().spawnOrigin()!=com.inigmasgames.hytalerpg.enemies.EnemyRewardContext.Origin.QA))return true;
            if(state==null)return true;
        }
        var entry=entry(store,target);
        return entry!=null&&(entry.spawn==null||entry.enemyState!=null&&entry.enemyState.providers().blocksExternalMutation());
    }
    /** Admission precedes armor, shields and damage credit, including bypass/environmental packets. */
    public final class IncomingProtection extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return NPCEntity.getComponentType();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getGatherDamageGroup()),
                new SystemGroupDependency<>(Order.BEFORE,DamageModule.get().getFilterDamageGroup()));}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            if(!damage.isCancelled()&&blocksIncoming(store,chunk.getReferenceTo(i)))damage.setCancelled(true);
        }
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
            if(!(damage.getSource() instanceof Damage.EntitySource source)||HytaleDamageAdapter.metadata(damage)!=null
                    ||NativeDamageLeafReceipts.sourceFactorsResolved(damage))return;
            var entry=entry(store,source.getRef());if(entry==null)return;
            if(entry.spawn==null){damage.setCancelled(true);return;}
            var cause=damage.getCause();if(cause==null||cause.doesBypassResistances()||channel(cause.getId())==null&&!Set.of("Physical","Projectile").contains(cause.getId()))return;
            double before=damage.getAmount(),amount=before*entry.spawn.combat().difficultyDamageFactor();
            // Passive-only QA roles have no wrapped native strike. The same native outgoing owner
            // applies their rarity baseline; wrapped strikes already carry resolved source factors.
            if(entry.enemyState!=null&&entry.enemyState.descriptor().spawnOrigin()==
                    com.inigmasgames.hytalerpg.enemies.EnemyRewardContext.Origin.QA)
                amount*=entry.enemyState.providers().rarityDirectFactor();
            if(!Double.isFinite(amount)||amount>Float.MAX_VALUE){damage.setCancelled(true);return;}
            damage.setAmount((float)amount);trace(damage,entry.spawn,before,"NATIVE_OUTGOING_DIFFICULTY_ONCE");
            }
        }
    }
    /** Legacy ordinary encounters retain their pass. ME composes resistance at the native armor boundary. */
    public final class Resistance extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return Query.and(NPCEntity.getComponentType(),EntityStatMap.getComponentType());}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),
                new SystemDependency<>(Order.BEFORE,SupportDamageSystems.HealthCap.class),new SystemDependency<>(Order.BEFORE,SupportDamageSystems.Shield.class),
                new SystemDependency<>(Order.BEFORE,HytaleDamageLifecycleSystems.Filter.class),new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            try(var span=NativeRpgTickMetrics.enter(store,NativeRpgTickMetrics.Phase.DAMAGE)){
            if(blocksIncoming(store,chunk.getReferenceTo(i))){damage.setCancelled(true);return;}
            if(damage.isCancelled()||damage.getAmount()<=0||Boolean.TRUE.equals(damage.getIfPresentMetaObject(INCOMING)))return;
            damage.putMetaObject(INCOMING,true);var entry=entry(store,chunk.getReferenceTo(i));if(entry==null)return;
            if(entry.spawn==null){damage.setCancelled(true);return;}
            if(entry.enemyState!=null||entry.spawn.combat().progression()!=null)return;
            var cause=damage.getCause();if(cause==null||cause.doesBypassResistances())return;
            var channel=channel(cause.getId());if(channel==null)return;
            var resistance=entry.spawn.combat().resistance();if(resistance.effective(channel)==0&&!resistance.immune(channel))return;
            double before=damage.getAmount();var result=resistance.resolve(channel,before,penetration(damage));damage.setAmount((float)result.amount());
            if(resistance.immune(channel))damage.setCancelled(true);
            trace(damage,entry.spawn,before,result.reason());
            }
        }
    }
}
