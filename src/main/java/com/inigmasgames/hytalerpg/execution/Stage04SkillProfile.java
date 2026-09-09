package com.inigmasgames.hytalerpg.execution;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Data-only runtime configuration consumed by shared family executors. */
public record Stage04SkillProfile(
        String skillId,
        Family family,
        Set<String> secondaryFamilies,
        Set<String> allowedMainHandKinds,
        Set<String> requiredOffHandKinds,
        String resourceType,
        double resourceCost,
        double cooldownSeconds,
        double windupSeconds,
        String basePowerSource,
        double innateBasePower,
        String scaling,
        Strike strike,
        Movement movement,
        Reaction reaction,
        Projectile projectile,
        com.inigmasgames.hytalerpg.execution.area.AreaSkillProfile area,
        com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile connection,
        com.inigmasgames.hytalerpg.execution.support.SupportProfile support,
        com.inigmasgames.hytalerpg.execution.summon.SummonProfile summon,
        com.inigmasgames.hytalerpg.execution.summon.SummonActionProfile summonAction,
        com.inigmasgames.hytalerpg.execution.summon.ConversionProfile conversion,
        com.inigmasgames.hytalerpg.execution.summon.SelectiveCageProfile cage) {

    public Stage04SkillProfile(String skillId,Family family,Set<String> secondaryFamilies,Set<String> allowedMainHandKinds,
            Set<String> requiredOffHandKinds,String resourceType,double resourceCost,double cooldownSeconds,double windupSeconds,
            String basePowerSource,double innateBasePower,String scaling,Strike strike,Movement movement,Reaction reaction,Projectile projectile,
            com.inigmasgames.hytalerpg.execution.area.AreaSkillProfile area,
            com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile connection,
            com.inigmasgames.hytalerpg.execution.support.SupportProfile support,
            com.inigmasgames.hytalerpg.execution.summon.SummonProfile summon,
            com.inigmasgames.hytalerpg.execution.summon.SummonActionProfile summonAction,
            com.inigmasgames.hytalerpg.execution.summon.ConversionProfile conversion) {
        this(skillId,family,secondaryFamilies,allowedMainHandKinds,requiredOffHandKinds,resourceType,resourceCost,cooldownSeconds,
                windupSeconds,basePowerSource,innateBasePower,scaling,strike,movement,reaction,projectile,area,connection,support,summon,summonAction,conversion,null);
    }

    public Stage04SkillProfile(String skillId,Family family,Set<String> secondaryFamilies,Set<String> allowedMainHandKinds,
            Set<String> requiredOffHandKinds,String resourceType,double resourceCost,double cooldownSeconds,double windupSeconds,
            String basePowerSource,double innateBasePower,String scaling,Strike strike,Movement movement,Reaction reaction,Projectile projectile,
            com.inigmasgames.hytalerpg.execution.area.AreaSkillProfile area,
            com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile connection,
            com.inigmasgames.hytalerpg.execution.support.SupportProfile support,
            com.inigmasgames.hytalerpg.execution.summon.SummonProfile summon,
            com.inigmasgames.hytalerpg.execution.summon.SummonActionProfile summonAction) {
        this(skillId,family,secondaryFamilies,allowedMainHandKinds,requiredOffHandKinds,resourceType,resourceCost,cooldownSeconds,
                windupSeconds,basePowerSource,innateBasePower,scaling,strike,movement,reaction,projectile,area,connection,support,summon,summonAction,null);
    }

    public Stage04SkillProfile(String skillId,Family family,Set<String> secondaryFamilies,Set<String> allowedMainHandKinds,
            Set<String> requiredOffHandKinds,String resourceType,double resourceCost,double cooldownSeconds,double windupSeconds,
            String basePowerSource,double innateBasePower,String scaling,Strike strike,Movement movement,Reaction reaction,Projectile projectile,
            com.inigmasgames.hytalerpg.execution.area.AreaSkillProfile area,
            com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile connection,
            com.inigmasgames.hytalerpg.execution.support.SupportProfile support,
            com.inigmasgames.hytalerpg.execution.summon.SummonProfile summon) {
        this(skillId,family,secondaryFamilies,allowedMainHandKinds,requiredOffHandKinds,resourceType,resourceCost,cooldownSeconds,
                windupSeconds,basePowerSource,innateBasePower,scaling,strike,movement,reaction,projectile,area,connection,support,summon,null);
    }

    public Stage04SkillProfile(String skillId,Family family,Set<String> secondaryFamilies,Set<String> allowedMainHandKinds,
            Set<String> requiredOffHandKinds,String resourceType,double resourceCost,double cooldownSeconds,double windupSeconds,
            String basePowerSource,double innateBasePower,String scaling,Strike strike,Movement movement,Reaction reaction,Projectile projectile,
            com.inigmasgames.hytalerpg.execution.area.AreaSkillProfile area,
            com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile connection,
            com.inigmasgames.hytalerpg.execution.support.SupportProfile support) {
        this(skillId,family,secondaryFamilies,allowedMainHandKinds,requiredOffHandKinds,resourceType,resourceCost,cooldownSeconds,
                windupSeconds,basePowerSource,innateBasePower,scaling,strike,movement,reaction,projectile,area,connection,support,null);
    }

    public Stage04SkillProfile(String skillId,Family family,Set<String> secondaryFamilies,Set<String> allowedMainHandKinds,
            Set<String> requiredOffHandKinds,String resourceType,double resourceCost,double cooldownSeconds,double windupSeconds,
            String basePowerSource,double innateBasePower,String scaling,Strike strike,Movement movement,Reaction reaction,Projectile projectile,
            com.inigmasgames.hytalerpg.execution.area.AreaSkillProfile area,
            com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile connection) {
        this(skillId,family,secondaryFamilies,allowedMainHandKinds,requiredOffHandKinds,resourceType,resourceCost,cooldownSeconds,
                windupSeconds,basePowerSource,innateBasePower,scaling,strike,movement,reaction,projectile,area,connection,null);
    }

    public Stage04SkillProfile(String skillId,Family family,Set<String> secondaryFamilies,Set<String> allowedMainHandKinds,
            Set<String> requiredOffHandKinds,String resourceType,double resourceCost,double cooldownSeconds,double windupSeconds,
            String basePowerSource,double innateBasePower,String scaling,Strike strike,Movement movement,Reaction reaction,Projectile projectile,
            com.inigmasgames.hytalerpg.execution.area.AreaSkillProfile area) {
        this(skillId,family,secondaryFamilies,allowedMainHandKinds,requiredOffHandKinds,resourceType,resourceCost,cooldownSeconds,
                windupSeconds,basePowerSource,innateBasePower,scaling,strike,movement,reaction,projectile,area,null);
    }

    /** Retained source compatibility for the Stage 04/05 fixtures and consumers. */
    public Stage04SkillProfile(String skillId, Family family, Set<String> secondaryFamilies,
            Set<String> allowedMainHandKinds, Set<String> requiredOffHandKinds, String resourceType,
            double resourceCost, double cooldownSeconds, double windupSeconds, String basePowerSource,
            double innateBasePower, String scaling, Strike strike, Movement movement, Reaction reaction,
            Projectile projectile) {
        this(skillId, family, secondaryFamilies, allowedMainHandKinds, requiredOffHandKinds, resourceType,
                resourceCost, cooldownSeconds, windupSeconds, basePowerSource, innateBasePower, scaling,
                strike, movement, reaction, projectile, null);
    }

    public Stage04SkillProfile {
        secondaryFamilies = Set.copyOf(secondaryFamilies == null ? Set.of() : secondaryFamilies);
        allowedMainHandKinds = Set.copyOf(allowedMainHandKinds == null ? Set.of() : allowedMainHandKinds);
        requiredOffHandKinds = Set.copyOf(requiredOffHandKinds == null ? Set.of() : requiredOffHandKinds);
        if (skillId == null || skillId.isBlank() || family == null || !finite(resourceCost, cooldownSeconds, windupSeconds, innateBasePower)
                || resourceCost < 0.0 || cooldownSeconds < 0.0 || windupSeconds < 0.0 || innateBasePower < 0)
            throw new IllegalArgumentException("Invalid runtime skill profile");
    }

    public boolean hasFamily(Family candidate) {
        return family == candidate || secondaryFamilies.contains(candidate.name());
    }

    public double damageCoefficient() {
        if (strike != null) return strike.coefficient();
        if (projectile != null) return projectile.coefficient()>0?projectile.coefficient():projectile.details().explosion().coefficient();
        if (area != null) return area.coefficient();
        if (connection != null) return connection.coefficient();
        if (summon != null) return summon.coefficient();
        if (summonAction != null) return summonAction.coefficient();
        if (cage != null) return cage.coefficient();
        return 0.0;
    }

    public Map<String, Double> authoredStatuses() {
        if(connection!=null&&!connection.details().status().isBlank())return Map.of(connection.details().status(),connection.details().statusSeconds());
        if (strike != null && !strike.statusId().isBlank())
            return Map.of(strike.statusId(), strike.statusSeconds());
        if (projectile != null && !projectile.statusId().isBlank())
            return Map.of(projectile.statusId(), projectile.statusSeconds());
        return Map.of();
    }

    public enum Family { STRIKE, MOVEMENT, REACTION, PROJECTILE, BURST, CONE, TRAP, GROUND_ZONE, WALL, OVERHEAD, BOMBARDMENT, LINE,BEAM,ORB,ORBIT,DIRECT_TARGET,AURA,BARRIER,BUFF,SUMMON,CORPSE }
    public enum Geometry { ARC, LINE, ASSIST_CONE, RADIUS }
    public enum MovementKind { DASH, LEAP }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
    public String activationGate() {
        if(cage!=null)return com.inigmasgames.hytalerpg.execution.summon.SelectiveCageProfile.BLOCKED_BOUNDARY;
        if(reaction!=null&&reaction.nativeHeld())return "NATIVE_GUARD_HELD_ITEM_RELEASE_ROUTE_UNVERIFIED";
        return projectile==null?"":projectile.details().nativeCapabilityGate();
    }

    public record StrikeDetails(String element, double height, double actionLockSeconds, double movementFactor,
                               com.inigmasgames.hytalerpg.combat.damage.VictimCoefficient victimCoefficient,boolean finisher) {
        public StrikeDetails(String element,double height,double actionLockSeconds,double movementFactor){
            this(element,height,actionLockSeconds,movementFactor,com.inigmasgames.hytalerpg.combat.damage.VictimCoefficient.NONE,false);
        }
        public static final StrikeDetails DEFAULT = new StrikeDetails("PHYSICAL", 2.5, 0, 1);
        public StrikeDetails {
            victimCoefficient=victimCoefficient==null?com.inigmasgames.hytalerpg.combat.damage.VictimCoefficient.NONE:victimCoefficient;
            if(finisher&&victimCoefficient!=com.inigmasgames.hytalerpg.combat.damage.VictimCoefficient.NONE)throw new IllegalArgumentException("Conflicting authored strike conditions");
            if (!Set.of("PHYSICAL", "FIRE", "NECROTIC", "VOID").contains(element)
                    || !finite(height, actionLockSeconds, movementFactor) || height <= 0 || height > 64
                    || actionLockSeconds < 0 || actionLockSeconds > 10 || movementFactor <= 0 || movementFactor > 1)
                throw new IllegalArgumentException("Invalid strike element/geometry/cadence");
            if (movementFactor != 1 && actionLockSeconds == 0)
                throw new IllegalArgumentException("Strike movement restriction requires an owned action window");
        }
    }

    public record Strike(Geometry geometry, double range, double angleDegrees, double lineHalfWidth,
                         int repeats, double repeatIntervalSeconds, int targetCap,
                         double coefficient, String statusId, double statusSeconds, StrikeDetails details) {
        public Strike(Geometry geometry, double range, double angleDegrees, double lineHalfWidth,
                      int repeats, double repeatIntervalSeconds, int targetCap, double coefficient,
                      String statusId, double statusSeconds) {
            this(geometry, range, angleDegrees, lineHalfWidth, repeats, repeatIntervalSeconds,
                    targetCap, coefficient, statusId, statusSeconds, StrikeDetails.DEFAULT);
        }
        public Strike {
            details = details == null ? StrikeDetails.DEFAULT : details;
            if (geometry == null || !finite(range, angleDegrees, lineHalfWidth, repeatIntervalSeconds, coefficient, statusSeconds)
                    || range < 0.0 || angleDegrees < 0.0 || angleDegrees > 360 || lineHalfWidth < 0.0
                    || repeats < 1 || repeats > 256 || repeatIntervalSeconds < 0.0 || targetCap < 1 || targetCap > 256
                    || coefficient < 0.0 || statusSeconds < 0)
                throw new IllegalArgumentException("Invalid strike profile");
            if (details.actionLockSeconds() > 0 && details.actionLockSeconds() < (repeats - 1) * repeatIntervalSeconds)
                throw new IllegalArgumentException("Strike action lock ends before authored hits");
            statusId = statusId == null ? "" : statusId;
        }
        public Strike withRange(double value) {
            return new Strike(geometry, value, angleDegrees, lineHalfWidth, repeats, repeatIntervalSeconds,
                    targetCap, coefficient, statusId, statusSeconds, details);
        }
    }

    public record MovementDetails(boolean groundTarget,double travelSpeed,double pathWidth,boolean stopAtFirstEnemy,double knockback) {
        public static final MovementDetails DEFAULT=new MovementDetails(false,0,0,false,0);
        public MovementDetails {
            if(!finite(travelSpeed,pathWidth,knockback)||travelSpeed<0||travelSpeed>100||pathWidth<0||pathWidth>8||knockback<0||knockback>8
                    ||stopAtFirstEnemy&&pathWidth==0||knockback>0&&!stopAtFirstEnemy)throw new IllegalArgumentException("Invalid movement contact policy");
        }
        public boolean pathDamage(){return pathWidth>0;}
    }
    public record Movement(MovementKind kind, double maxDistance, double minimumDurationSeconds,
                           double maximumDurationSeconds, double apexHeight, double landingRadius,MovementDetails details) {
        public Movement(MovementKind kind,double maxDistance,double minimumDurationSeconds,double maximumDurationSeconds,double apexHeight,double landingRadius){
            this(kind,maxDistance,minimumDurationSeconds,maximumDurationSeconds,apexHeight,landingRadius,MovementDetails.DEFAULT);
        }
        public Movement {
            details=details==null?MovementDetails.DEFAULT:details;
            if (kind == null || !finite(maxDistance, minimumDurationSeconds, maximumDurationSeconds, apexHeight, landingRadius)
                    || maxDistance < 0.0 || minimumDurationSeconds < 0.0
                    || maximumDurationSeconds < minimumDurationSeconds || apexHeight < 0.0 || landingRadius < 0.0)
                throw new IllegalArgumentException("Invalid movement profile");
        }
    }

    public record Reaction(double windowSeconds, List<String> qualifyingSignals,boolean nativeHeld) {
        public Reaction(double windowSeconds,List<String> qualifyingSignals){this(windowSeconds,qualifyingSignals,false);}
        public Reaction {
            if (!Double.isFinite(windowSeconds) || (nativeHeld?windowSeconds!=0:windowSeconds<=0)) throw new IllegalArgumentException("Reaction window must be finite; native held has no RPG timer");
            qualifyingSignals = List.copyOf(qualifyingSignals == null ? List.of() : qualifyingSignals);
        }
    }

    public record Projectile(String configId, double speed, double maxDistance, double radius,
                             double gravity, int targetCap, double coefficient,
                             String statusId, double statusSeconds, double periodicCoefficient,
                             int periodicTicks, double periodicIntervalSeconds,
                             String ammoItemId, int ammoQuantity, boolean fullyCharged,
                             double knockbackDistance,
                             Map<String, String> configIdsByWeaponKind,
                             Map<String, Double> speedsByWeaponKind,double independentLifetimeSeconds,
                             ProjectileDetails details) {
        public Projectile(String configId,double speed,double maxDistance,double radius,double gravity,int targetCap,double coefficient,
                String statusId,double statusSeconds,double periodicCoefficient,int periodicTicks,double periodicIntervalSeconds,
                String ammoItemId,int ammoQuantity,boolean fullyCharged,double knockbackDistance,Map<String,String> configIdsByWeaponKind,
                Map<String,Double> speedsByWeaponKind,double independentLifetimeSeconds) {
            this(configId,speed,maxDistance,radius,gravity,targetCap,coefficient,statusId,statusSeconds,periodicCoefficient,periodicTicks,
                    periodicIntervalSeconds,ammoItemId,ammoQuantity,fullyCharged,knockbackDistance,configIdsByWeaponKind,speedsByWeaponKind,
                    independentLifetimeSeconds,null);
        }
        public Projectile(String configId,double speed,double maxDistance,double radius,double gravity,int targetCap,double coefficient,
                String statusId,double statusSeconds,double periodicCoefficient,int periodicTicks,double periodicIntervalSeconds,
                String ammoItemId,int ammoQuantity,boolean fullyCharged,double knockbackDistance,Map<String,String> configIdsByWeaponKind,
                Map<String,Double> speedsByWeaponKind) {
            this(configId,speed,maxDistance,radius,gravity,targetCap,coefficient,statusId,statusSeconds,periodicCoefficient,periodicTicks,
                    periodicIntervalSeconds,ammoItemId,ammoQuantity,fullyCharged,knockbackDistance,configIdsByWeaponKind,speedsByWeaponKind,0);
        }
        public Projectile {
            configId = configId == null ? "" : configId;
            statusId = statusId == null ? "" : statusId;
            ammoItemId = ammoItemId == null ? "" : ammoItemId;
            details = details == null ? new ProjectileDetails("PHYSICAL", statusId.equals("CHILL") ? 1 : 0, 0, Set.of()) : details;
            if (statusId.equals("CHILL") != (details.chillStacks() > 0)
                    || details.bossRootSlow() > 0 && (!statusId.equals("ROOT") || statusSeconds <= 0))
                throw new IllegalArgumentException("Projectile control details must match the authored payload");
            configIdsByWeaponKind = Map.copyOf(configIdsByWeaponKind == null ? Map.of() : configIdsByWeaponKind);
            speedsByWeaponKind = Map.copyOf(speedsByWeaponKind == null ? Map.of() : speedsByWeaponKind);
            if (configId.isBlank() || !Double.isFinite(speed)||!Double.isFinite(maxDistance)||!Double.isFinite(radius)
                    || !Double.isFinite(independentLifetimeSeconds)||independentLifetimeSeconds<0 || speed <= 0.0 || maxDistance <= 0.0 || radius <= 0.0
                    || !finite(gravity, coefficient, statusSeconds, periodicCoefficient, periodicIntervalSeconds, knockbackDistance)
                    || targetCap < 1 || targetCap > 64 || gravity < 0 || coefficient < 0.0
                    || statusSeconds < 0.0 || periodicCoefficient < 0.0 || periodicTicks < 0
                    || periodicIntervalSeconds < 0.0 || ammoQuantity < 0 || knockbackDistance < 0.0
                    || speedsByWeaponKind.values().stream().anyMatch(value -> value == null || !Double.isFinite(value) || value <= 0.0))
                throw new IllegalArgumentException("Invalid projectile profile");
            if ((ammoItemId.isBlank()) != (ammoQuantity == 0))
                throw new IllegalArgumentException("Projectile ammunition ID and quantity must be declared together");
            if ((periodicTicks == 0) != (periodicCoefficient == 0.0 || periodicIntervalSeconds == 0.0))
                throw new IllegalArgumentException("Projectile periodic payload must be fully declared or absent");
        }
        public double maximumLifetimeSeconds() { return capLifetime(maxDistance/speed); }
        public String configIdFor(String weaponKind) {
            return configIdsByWeaponKind.getOrDefault(weaponKind, configId);
        }
        public double speedFor(String weaponKind) {
            return speedsByWeaponKind.getOrDefault(weaponKind, speed);
        }
        public double maximumLifetimeSeconds(String weaponKind) { return capLifetime(maxDistance/speedFor(weaponKind)); }
        public double capLifetime(double computed){
            if (independentLifetimeSeconds > 0 && (details.pattern().ballisticAim() || details.pattern().homingTurnDegrees() > 0))
                return independentLifetimeSeconds;
            return independentLifetimeSeconds>0?Math.min(computed,independentLifetimeSeconds):computed;
        }
        public boolean requiresAmmo() { return ammoQuantity > 0; }
        public boolean hasPeriodicStatus() { return periodicTicks > 0; }
    }

    /** Typed payload authority, independent of the cosmetic native carrier or its collision transport. */
    public record ProjectileDetails(String element, int chillStacks, double bossRootSlow, Set<String> bossSlowOptInRoles,
                                    com.inigmasgames.hytalerpg.execution.projectile.ProjectilePattern pattern,
                                    com.inigmasgames.hytalerpg.execution.projectile.ProjectileExplosion explosion,
                                    String nativeCapabilityGate) {
        public ProjectileDetails(String element,int chillStacks,double bossRootSlow,Set<String> roles) {
            this(element,chillStacks,bossRootSlow,roles,null,null,"");
        }
        public ProjectileDetails {
            pattern = pattern == null ? com.inigmasgames.hytalerpg.execution.projectile.ProjectilePattern.SINGLE : pattern;
            explosion = explosion == null ? com.inigmasgames.hytalerpg.execution.projectile.ProjectileExplosion.NONE : explosion;
            nativeCapabilityGate = nativeCapabilityGate == null ? "" : nativeCapabilityGate;
            if (!Set.of("", "NATIVE_BOW_MAX_RANGE_UNVERIFIED").contains(nativeCapabilityGate))
                throw new IllegalArgumentException("Unknown projectile capability gate");
            bossSlowOptInRoles = Set.copyOf(bossSlowOptInRoles == null ? Set.of() : bossSlowOptInRoles);
            if (!Set.of("PHYSICAL", "FIRE", "COLD", "ARCANE", "VOID", "NECROTIC").contains(element)
                    || chillStacks < 0 || chillStacks > 5 || !Double.isFinite(bossRootSlow)
                    || bossRootSlow < 0 || bossRootSlow > 1 || bossSlowOptInRoles.size() > 256
                    || bossSlowOptInRoles.stream().anyMatch(role -> role == null || role.isBlank())
                    || bossRootSlow == 0 && !bossSlowOptInRoles.isEmpty())
                throw new IllegalArgumentException("Invalid projectile element/control policy");
        }
        public boolean allowsBossSlow(String role) { return bossRootSlow > 0 && bossSlowOptInRoles.contains(role); }
    }
}
