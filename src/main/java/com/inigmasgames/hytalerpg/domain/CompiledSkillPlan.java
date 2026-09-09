package com.inigmasgames.hytalerpg.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure compiler output. Stage 01B does not execute this plan. */
public record CompiledSkillPlan(
        int schemaVersion,
        SkillSlot skillSlot,
        SkillId skillId,
        String planHash,
        String finalFamily,
        Set<String> finalTags,
        List<PassiveId> passiveOrder,
        Map<PassiveId, List<LinkNodeId>> graphRoutes,
        List<String> targetingModifiers,
        List<String> geometryModifiers,
        List<String> multiplicity,
        List<String> continuation,
        List<String> resourceCooldownModifiers,
        List<String> powerModifiers,
        KernelModifiers kernelModifiers,
        List<String> triggerHooks,
        String vfxRecipeId,
        String soundRecipeId,
        SafetyBudgets safetyBudgets,
        boolean degraded,
        List<String> degradedReasons) {
    public static final int CURRENT_SCHEMA = 38;
    public boolean impactOnlyOnSecondary(){return geometryModifiers.contains("IMPACT_SCOPE=SECONDARY_ONLY");}
    public boolean retaliation(){return passiveOrder.stream().anyMatch(p->p.value().equals("retaliation"));}
    public String conditionalRepeat(){return passiveOrder.stream().map(PassiveId::value).filter(p->p.equals("critical_trigger")||p.equals("kill_trigger")).findFirst().orElse("");}
    public boolean orbit(){return passiveOrder.stream().anyMatch(p->p.value().equals("orbit"));}
    public PositionModifiers positions(){return PositionModifiers.from(passiveOrder);}
    public boolean positionOnlyOnSecondary(){return geometryModifiers.contains("POSITION_SCOPE=SECONDARY_ONLY");}
    public StrikeModifiers strikes(){return StrikeModifiers.from(passiveOrder);}
    public ResourceModifiers resources(){return ResourceModifiers.from(passiveOrder);}
    public ControlModifiers controls(){return ControlModifiers.from(passiveOrder);}
    public DotModifiers dots(){return DotModifiers.from(passiveOrder);}
    public PulseModifiers pulses(){return PulseModifiers.from(passiveOrder);}
    public ZoneModifiers zones(){return ZoneModifiers.from(passiveOrder);}
    public GeometryModifiers geometry(){return GeometryModifiers.from(passiveOrder);}
    public HitConditionModifiers hitConditions(){return HitConditionModifiers.from(passiveOrder);}
    public CompiledSkillPlan {
        finalTags = Set.copyOf(finalTags);
        passiveOrder = List.copyOf(passiveOrder);
        graphRoutes = Map.copyOf(graphRoutes);
        targetingModifiers = List.copyOf(targetingModifiers);
        geometryModifiers = List.copyOf(geometryModifiers);
        multiplicity = List.copyOf(multiplicity);
        continuation = List.copyOf(continuation);
        resourceCooldownModifiers = List.copyOf(resourceCooldownModifiers);
        powerModifiers = List.copyOf(powerModifiers);
        triggerHooks = List.copyOf(triggerHooks);
        degradedReasons = List.copyOf(degradedReasons);
        if (kernelModifiers == null) kernelModifiers = KernelModifiers.NONE;
    }

    /** Typed subset consumed by the Stage 02 kernel. Descriptive operations remain for later executors. */
    public record KernelModifiers(double scalablePayloadIncreased, double resourceCostMultiplier,
                                  double cooldownRecoveryBonus) {
        public static final KernelModifiers NONE = new KernelModifiers(0.0, 1.0, 0.0);
        public KernelModifiers {
            if (!Double.isFinite(scalablePayloadIncreased) || scalablePayloadIncreased < 0.0)
                throw new IllegalArgumentException("scalablePayloadIncreased must be finite and non-negative");
            if (!Double.isFinite(resourceCostMultiplier) || resourceCostMultiplier < 0.0)
                throw new IllegalArgumentException("resourceCostMultiplier must be finite and non-negative");
            if (!Double.isFinite(cooldownRecoveryBonus) || cooldownRecoveryBonus < 0.0)
                throw new IllegalArgumentException("cooldownRecoveryBonus must be finite and non-negative");
        }
    }

    public record SafetyBudgets(int maxGeneration, int maxSpawnedEffects, int maxTriggeredSecondaries,
                                int maxLiveSummons, int maxLiveProjectiles, int maxPersistentFields,
                                int maxActiveAuras, int passiveSpawnCost) {
        public static SafetyBudgets baseline(int passiveSpawnCost) {
            return new SafetyBudgets(3, 48, 16, 8, 24, 8, 4, passiveSpawnCost);
        }
    }
    /** Typed release/geometry contract derived from the compiler's validated, deduplicated passive order. */
    public ExecutionModifiers executionModifiers() { return ExecutionModifiers.from(passiveOrder); }
    public ProjectileModifiers projectileModifiers() { return ProjectileModifiers.from(passiveOrder); }
    public SupportModifiers supportModifiers(){return SupportModifiers.from(passiveOrder);}
    public SummonModifiers summonModifiers(){return SummonModifiers.from(passiveOrder);}
    public FoundationModifiers foundationModifiers(){return FoundationModifiers.from(passiveOrder);}
    public HitProcModifiers hitProcs(){return HitProcModifiers.from(passiveOrder);}
    public boolean proliferation(){return passiveOrder.stream().anyMatch(p->p.value().equals("proliferation"));}
    public record ProjectileModifiers(int pierce,int fork,int chain,int returning,int ricochet,boolean volley,boolean barrage,boolean homing,
            boolean accelerant,boolean ballistics,boolean shrapnel,boolean splinterburst) {
        public static ProjectileModifiers from(List<PassiveId> order) {
            Set<String> ids=order.stream().map(PassiveId::value).collect(java.util.stream.Collectors.toSet());
            return new ProjectileModifiers(ids.contains("piercing")?2:0,ids.contains("fork")?1:0,
                    ids.contains("chain")?2:0,ids.contains("return")?1:0,ids.contains("ricochet")?2:0,
                    ids.contains("volley"),ids.contains("barrage"),ids.contains("homing"),ids.contains("accelerant"),ids.contains("ballistics"),
                    ids.contains("shrapnel"),ids.contains("splinterburst"));
        }
        public double speedFactor(){return (accelerant?1.4:1)*(ballistics?.65:1);}
        public double distanceFactor(){return accelerant?1.2:1;}
        public int batchSize(){return volley?3:1;}
        public int batchCount(){return barrage?3:1;}
        public int rootLaunches(boolean echo){return batchSize()*(echo?2:batchCount());}
        public List<Double> payloadLess(){
            var less=new java.util.ArrayList<Double>();if(volley)less.add(.25);if(barrage)less.add(.40);if(homing)less.add(.10);return List.copyOf(less);
        }
    }
    public boolean radiusOnlyOnShrapnel(){return executionModifiers().expandedRadius()&&projectileModifiers().shrapnel()&&!finalTags.contains("HAS_RADIUS");}
    public boolean radiusOnlyOnShockwave(){return geometryModifiers.contains("SHOCKWAVE_RADIUS_MULTIPLIER=1.25");}
    public boolean radiusOnlyOnSecondary(){return radiusOnlyOnShrapnel()||radiusOnlyOnShockwave()||geometryModifiers.contains("SHATTER_RADIUS_MULTIPLIER=1.25");}
    public boolean concentrationOnlyOnSecondary(){return powerModifiers.contains("CONCENTRATION_SCOPE=SECONDARY_ONLY");}
    public record ExecutionModifiers(double radiusFactor, double delaySeconds, double echoDelaySeconds,
                                     double echoMagnitude, boolean expandedRadius,int barrageBatches,double barrageInterval) {
        public ExecutionModifiers(double radiusFactor,double delaySeconds,double echoDelaySeconds,double echoMagnitude,boolean expandedRadius) {
            this(radiusFactor,delaySeconds,echoDelaySeconds,echoMagnitude,expandedRadius,1,0);
        }
        public static ExecutionModifiers from(List<PassiveId> order) {
            boolean radius=order.stream().anyMatch(p->p.value().equals("expanded_radius"));
            boolean delay=order.stream().anyMatch(p->p.value().equals("skill_delay"));
            boolean echo=order.stream().anyMatch(p->p.value().equals("echo"));
            boolean barrage=order.stream().anyMatch(p->p.value().equals("barrage"));
            return new ExecutionModifiers(radius?1.25:1,delay?2:0,echo?.45:0,echo?.7:1,radius,barrage?3:1,barrage?.18:0);
        }
        public boolean scheduled() { return delaySeconds>0 || echoDelaySeconds>0 || barrageBatches>1; }
    }
}
