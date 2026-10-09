package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCalculatorSystems;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DamageCalculator;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.hytalerpg.combat.power.NativeItemPowerRegistry;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

/** Extends the native damage leaf, retaining collision, protection, effects and native channel submissions. */
public final class ManagedGearDamageInteraction extends com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafInteraction {
    public static final String TYPE="RPG_GearDamage";
    private boolean offhand;
    private String procSelector;
    private Double procCoefficient;
    public static boolean noProcChild(GearCombatEffects.Hit hit) { return hit != null && hit.rootId().endsWith("/WA141/NoProc"); }
    private static final GearBindings BINDINGS=new GearBindings();
    private static final NativeItemPowerRegistry POWERS=NativeItemPowerRegistry.loadProduction();
    private static final ThreadLocal<Invocation> CURRENT=new ThreadLocal<>();
    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID,java.util.ArrayDeque<Pending>> PENDING=new java.util.concurrent.ConcurrentHashMap<>();
    private static final class Pending {
        final GearCombatEffects.Hit hit;
        final java.util.Set<String> remaining;
        final InteractionType type;
        final long createdNanos=System.nanoTime();
        boolean started;
        Pending(GearCombatEffects.Hit hit,java.util.Set<String> causes,InteractionType type){
            this.hit=hit;this.remaining=new java.util.HashSet<>(causes);this.type=type;
        }
    }
    private record Invocation(ManagedGearDamageInteraction leaf,InteractionContext context,GearEffectSnapshot effects,String root) {}
    static double samplePower(GearInstance gear,String strike){
        var range=GearCombatEffects.physical(gear);
        return GearPower.sample(range.minimum(),range.maximum(),strike);
    }
    /** Consumed by the native offhand calculator after its context and utility checks. */
    static GearCombatEffects.Hit offhandContact(NativeTwinAssaultGate.Commit commit,int chainId,
            java.util.UUID actor,GearInstance heldMain,NativeGearAttackAcceptance.Chain acceptance,String root) {
        if(commit==null||acceptance==null||heldMain==null||root==null||root.isBlank()
                ||commit.chainId()!=chainId||!commit.actor().equals(actor)||!acceptance.actor().equals(actor)
                ||!commit.mainId().equals(heldMain.identity())||!acceptance.item().equals(heldMain.identity())
                ||commit.offhand().identity().equals(heldMain.identity())
                ||!commit.offhand().baseId().equals(heldMain.baseId())
                ||!commit.snapshot().revision().equals(acceptance.snapshot().revision())
                ||!commit.snapshot().items().equals(acceptance.snapshot().items())
                ||commit.snapshot().forItem(heldMain.identity()).empty()
                ||commit.snapshot().forItem(commit.offhand().identity()).empty())return null;
        var offhand=commit.offhand();
        String strike=offhand.identity()+"/"+root;
        return GearCombatEffects.attack(commit.snapshot(),offhand.identity(),strike+"/WA141/NoProc",
                samplePower(offhand,strike),.4,true,false,0,null,
                acceptance.baselineCritChance(),acceptance.baselineCritMultiplier(),false,acceptance.origin());
    }
    /** Call from the player's existing world/disconnect cleanup owner. */
    public static void forget(java.util.UUID actor){PENDING.remove(actor);}
    public static int pending(java.util.UUID actor){var queue=PENDING.get(actor);if(queue==null)return 0;
        synchronized(queue){return queue.size();}}
    static void register(java.util.UUID actor,GearCombatEffects.Hit hit,java.util.Set<String> causes,InteractionType type){
        if(causes.isEmpty())return;
        var queue=PENDING.computeIfAbsent(actor,ignored->new java.util.ArrayDeque<>());
        synchronized(queue){
            long now=System.nanoTime();
            while(!queue.isEmpty()&&now-queue.peekFirst().createdNanos>60_000_000_000L){
                queue.removeFirst();
                System.err.println("MANAGED_GEAR_HIT_EXPIRED actor="+actor);
            }
            if(queue.size()>=64)throw new IllegalStateException("MANAGED_GEAR_HIT_LEDGER_FULL");
            queue.addLast(new Pending(hit,causes,type));
        }
    }
    /** The first native component carries DamageSequence; later components consume the same queued contact. */
    public static GearCombatEffects.Hit claim(Damage damage,java.util.UUID actor){
        var queue=PENDING.get(actor);if(queue==null||damage.getCause()==null)return null;
        synchronized(queue){
            var pending=queue.peekFirst();if(pending==null)return null;
            String cause=damage.getCause().getId();
            if(!pending.remaining.contains(cause)||damage.getIfPresentMetaObject(Damage.INTERACTION_TYPE)!=pending.type)return null;
            if(!pending.started){
                var witness=damage.getIfPresentMetaObject(DamageCalculatorSystems.DAMAGE_SEQUENCE);
                if(witness==null||!(witness.getDamageCalculator() instanceof Calculator))return null;
                pending.started=true;
            }
            pending.remaining.remove(cause);
            if(pending.remaining.isEmpty()){queue.removeFirst();if(queue.isEmpty())PENDING.remove(actor,queue);}
            return pending.hit;
        }
    }
    public static final BuilderCodec<ManagedGearDamageInteraction> CODEC=BuilderCodec.builder(
            ManagedGearDamageInteraction.class,ManagedGearDamageInteraction::new,DamageEntityInteraction.CODEC)
            .append(new KeyedCodec<>("Offhand",Codec.BOOLEAN),(leaf,value)->leaf.offhand=value,leaf->leaf.offhand).add()
            .append(new KeyedCodec<>(NativeGearAttackAcceptance.PROC_SELECTOR,Codec.STRING),(leaf,value)->leaf.procSelector=value,leaf->leaf.procSelector).add()
            .append(new KeyedCodec<>(NativeGearAttackAcceptance.PROC_COEFFICIENT,Codec.DOUBLE),(leaf,value)->leaf.procCoefficient=value,leaf->leaf.procCoefficient).add()
            .afterDecode(ManagedGearDamageInteraction::bind).build();
    java.util.Map<String,String> procVariables(java.util.Map<String,String> inherited) {
        if(procSelector==null&&procCoefficient==null)return inherited;
        if(procSelector==null||procCoefficient==null)throw new IllegalStateException("INCOMPLETE_NATIVE_PROC_METADATA");
        return java.util.Map.of(NativeGearAttackAcceptance.PROC_SELECTOR,procSelector,
                NativeGearAttackAcceptance.PROC_COEFFICIENT,Double.toString(procCoefficient));
    }
    private void bind() {
        if(damageCalculator==null) { if(id!=null) throw new IllegalArgumentException("Gear damage calculator missing");return; }
        bindCalculators(ManagedGearDamageInteraction::adapt);
    }
    private static DamageCalculator adapt(DamageCalculator original) {
        if(original==null) return null;
        var calculator=Calculator.CODEC.decode(DamageCalculator.CODEC.encode(original,new ExtraInfo()),new ExtraInfo());
        calculator.validate();return calculator;
    }
    public static final class GearAngled extends AngledDamage {
        static final BuilderCodec<GearAngled> CODEC=BuilderCodec.builder(GearAngled.class,GearAngled::new,AngledDamage.CODEC)
                .afterDecode(value->value.damageCalculator=adapt(value.damageCalculator)).build();
    }
    public static final class GearTargeted extends TargetedDamage {
        static final BuilderCodec<GearTargeted> CODEC=BuilderCodec.builder(GearTargeted.class,GearTargeted::new,TargetedDamage.CODEC)
                .afterDecode(value->value.damageCalculator=adapt(value.damageCalculator)).build();
    }
    @Override protected void tick0(boolean first,float dt,InteractionType type,InteractionContext context,CooldownHandler cooldowns) {
        var previous=CURRENT.get();
        var effects=GearNativeItems.effects(context.getOwningEntity(),context.getCommandBuffer()).snapshot();
        String root=context.getChain().getChainId()+"/"+getId()+"/"+context.getOperationCounter();
        CURRENT.set(new Invocation(this,context,effects,root));
        try { super.tick0(first,dt,type,context,cooldowns); }
        finally { if(previous==null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    @Override protected void simulateTick0(boolean first,float dt,InteractionType type,InteractionContext context,CooldownHandler cooldowns) {}
    public static final class Calculator extends DamageCalculator {
        static final BuilderCodec<Calculator> CODEC=BuilderCodec.builder(Calculator.class,Calculator::new,DamageCalculator.CODEC).build();
        void validate() {
            if(type!=Type.ABSOLUTE || baseDamageRaw==null || baseDamageRaw.size()!=1
                    || !(baseDamageRaw.containsKey("Physical") || baseDamageRaw.containsKey("Projectile"))
                    || baseDamageRaw.values().doubleStream().anyMatch(v->v<0)
                    || sequentialModifierStep!=0) throw new IllegalArgumentException("Unsupported native managed weapon calculator");
        }
        @Override public Object2FloatMap<DamageCause> calculateDamage(double runtime) {
            var call=CURRENT.get();if(call==null) throw new IllegalStateException("Gear calculation outside native strike");
            var launch=ManagedGearProjectile.snapshot(call.context());
            if(call.leaf().offhand && launch!=null)return new Object2FloatOpenHashMap<>();
            if(launch==null && call.context().getEntity()!=call.context().getOwningEntity())
                return new Object2FloatOpenHashMap<>(); // Never substitute the shooter's current item for a missing launch snapshot.
            if(launch==null && !GearNativeItems.canUse(call.context().getHeldItem(),call.context().getOwningEntity(),call.context().getCommandBuffer()))
                return new Object2FloatOpenHashMap<>();
            var gear=launch==null?GearNativeItems.read(call.context().getHeldItem()):launch.gear;
            if(gear==null) throw new IllegalStateException("Managed damage requires immutable gear identity");
            var heldMain=gear;
            var twin=call.leaf().offhand?NativeTwinAssaultGate.committed(call.context()):null;
            var acceptance=launch==null?NativeGearAcceptedContext.require(call.context(),gear):launch.acceptance;
            if(acceptance==null)return new Object2FloatOpenHashMap<>();
            if(call.leaf().offhand){
                var player=call.context().getCommandBuffer().getComponent(call.context().getOwningEntity(),PlayerRef.getComponentType());
                if(twin==null||player==null||!twin.actor().equals(player.getUuid())||!twin.mainId().equals(gear.identity())
                        ||twin.offhand().identity().equals(gear.identity())
                        ||!twin.offhand().baseId().equals(gear.baseId())
                        ||twin.snapshot().forItem(twin.offhand().identity()).empty())
                    return new Object2FloatOpenHashMap<>();
                gear=twin.offhand();
            }
            var binding=BINDINGS.require(gear.baseId());var baseline=POWERS.find(binding.nativeItemId()).orElseThrow();
            var range=GearCombatEffects.physical(gear);double minimum=range.minimum(),maximum=range.maximum();
            String strike=gear.identity()+"/"+call.root();
            double power=launch==null?samplePower(gear,strike):launch.power;
            // Native action coefficients survive. Native RandomPercentageModifier is deliberately never sampled.
            String cause=baseDamageRaw.containsKey("Projectile")?"Projectile":"Physical";
            double coefficient=call.leaf().offhand?.4:baseDamageRaw.getFloat(cause)/baseline.basePower();
            var effects=twin!=null?twin.snapshot():acceptance.snapshot();
            if(effects.forItem(gear.identity()).empty())return new Object2FloatOpenHashMap<>();
            var transform=call.context().getCommandBuffer().getComponent(call.context().getOwningEntity(),
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            var at=transform==null?null:transform.getPosition();
            var origin=launch==null?acceptance.origin():launch.origin();
            var hit=call.leaf().offhand?offhandContact(twin,call.context().getChain().getChainId(),
                    acceptance.actor(),heldMain,acceptance,call.root()):GearCombatEffects.attack(effects,gear.identity(),
                    launch==null?strike:launch.rootId(),power,
                    coefficient,true,false,0,null,acceptance.baselineCritChance(),acceptance.baselineCritMultiplier(),!call.leaf().offhand,origin);
            if(hit==null)return new Object2FloatOpenHashMap<>();
            if(!call.leaf().offhand){
                var proc=call.leaf().procVariables(call.context().getInteractionVars());
                String selector=call.leaf().procSelector==null?call.leaf().getId():call.leaf().procSelector;
                if(GearCombatEffects.needsNativeEnvelope(hit)){
                    double authored=NativeGearAttackAcceptance.coefficient(selector,proc);
                    double itemOnly=NativeGearAttackAcceptance.selectedAreaCoefficient(call.context(),
                            acceptance.world(),acceptance.actor(),hit,authored);
                    hit=NativeGearAttackAcceptance.commit(acceptance.world(),acceptance.actor(),hit,
                            itemOnly,launch==null,selector);
                }
            }
            var player=call.context().getCommandBuffer().getComponent(call.context().getOwningEntity(),PlayerRef.getComponentType());
            if(player!=null&&GearQaTrace.active(player.getUuid())){
                var contributions=new java.util.ArrayList<java.util.Map<String,Object>>();
                for(var affix:gear.affixes()){
                    String id=affix.familyId();boolean local=java.util.Set.of("WA-001","WA-002","WA-157","WA-158").contains(id);
                    boolean envelope=id.matches("WA-00[5-7]|WA-01[017-9]|WA-0[2-4][0-9]|WA-05[0-2]");
                    contributions.add(java.util.Map.of("id",id,"value",affix.value(),"appliedToAttack",local||envelope,
                            "reason",local?"resolved in local physical range":envelope?
                                    "captured in immutable attack envelope; target gate may reject":"not a native attack modifier"));
                }
                GearQaTrace.record(player.getUuid(),"ROOT_ATTACK_POWER",java.util.Map.ofEntries(
                        java.util.Map.entry("itemIdentity",gear.identity().toString()),java.util.Map.entry("root",strike),java.util.Map.entry("cause",cause),
                        java.util.Map.entry("intrinsicMin",gear.intrinsicStats().getOrDefault("physicalMin",0d)),
                        java.util.Map.entry("intrinsicMax",gear.intrinsicStats().getOrDefault("physicalMax",0d)),
                        java.util.Map.entry("resolvedMin",minimum),java.util.Map.entry("resolvedMax",maximum),java.util.Map.entry("sampledPower",power),
                        java.util.Map.entry("nativeCoefficient",coefficient),java.util.Map.entry("submittedPower",power*coefficient),
                        java.util.Map.entry("channels",hit.amounts().toString()),java.util.Map.entry("critical",hit.critical()),
                        java.util.Map.entry("affixes",contributions),
                        java.util.Map.entry("sourceRole",call.leaf().offhand?"WA141_OFFHAND":"MAINHAND"),
                        java.util.Map.entry("procChecks",call.leaf().offhand?"NoProc child":"authoritative item receipt budget")));
            }
            var result=new Object2FloatOpenHashMap<DamageCause>();
            for(var entry:hit.amounts().entrySet()){
                if(entry.getValue()<=0)continue;
                String nativeId=entry.getKey()==GearCombatEffects.Channel.PHYSICAL?cause:GearCombatEffects.nativeCause(entry.getKey());
                var nativeCause=DamageCause.getAssetMap().getAsset(nativeId);
                if(nativeCause==null)throw new IllegalStateException("Missing native gear channel "+nativeId);
                result.put(nativeCause,entry.getValue().floatValue());
            }
            boolean itemBolt=hit.snapshot().forItem(hit.itemId()).value("WA-145")>0
                    ||hit.snapshot().forItem(hit.itemId()).value("WA-146")>0;
            if(player!=null&&!result.isEmpty()&&(GearCombatEffects.needsNativeEnvelope(hit)||itemBolt)){
                var causes=new java.util.HashSet<String>();for(var nativeCause:result.keySet())causes.add(nativeCause.getId());
                register(player.getUuid(),hit,causes,call.context().getChain().getType());
            }
            return result;
        }
    }
}
