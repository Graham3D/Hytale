package com.inigmasgames.hytalerpg.combat.status;

import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Gear input to the existing source-owned periodic and control authorities. */
public final class GearStatusRuntime {
    private GearStatusRuntime() { }

    /** Keep valid armor contributions and the exact item committed for this damaging action. */
    public static GearEffectSnapshot sourceScoped(GearEffectSnapshot valid, UUID contributingItem) {
        if(valid==null||contributingItem==null)throw new IllegalArgumentException("STATUS_SOURCE_ITEM_REQUIRED");
        if(valid.items().stream().noneMatch(item->item.identity().equals(contributingItem)))
            return GearEffectSnapshot.EMPTY;
        return new GearEffectSnapshot(valid.items().stream().filter(item->
                item.category()==com.inigmasgames.hytalerpg.gear.GearCatalog.Category.ARMOR
                        ||item.identity().equals(contributingItem)).toList());
    }

    public static double increasedDps(GearEffectSnapshot gear, PeriodicStatusRuntime.Kind kind) {
        String matching = switch (kind) {
            case BURN -> "WA-065";
            case POISON -> "WA-066";
            case BLEED -> "WA-067";
        };
        return (gear.value("WA-013") + gear.value(matching)) / 100.0;
    }

    /** The same PeriodicStatusRuntime used by skills owns admission, stacking and every tick. */
    public static <C,T> String applyPeriodic(PeriodicStatusRuntime<C,T> runtime,
            PeriodicStatusRuntime.Source source, C context, T target, double baseDpsCoefficient,
            double baseStrength, double baseSeconds, int stacks, int sourceCap, double now,
            PeriodicStatusRuntime.Port<C,T> port, GearEffectSnapshot gear) {
        if (gear == null) throw new IllegalArgumentException("GEAR_STATUS_SNAPSHOT_REQUIRED");
        double factor = 1 + increasedDps(gear, source.kind());
        double duration = baseSeconds * (1 + gear.percent("WA-068"));
        return runtime.apply(source, context, target, baseDpsCoefficient * factor,
                baseStrength * factor, duration, stacks, sourceCap, now, port);
    }

    public record Chance(double skill, double gear, double effectiveResistance,
                         double skillOnly, double both, double any) {
        public Chance {
            if (!unit(skill) || !unit(gear) || !unit(effectiveResistance)
                    || !unit(skillOnly) || !unit(both) || !unit(any))
                throw new IllegalArgumentException("INVALID_STATUS_CHANCE");
        }
        public boolean skillSucceeded(double draw) { return draw < skillOnly; }
        public boolean gearSucceeded(double draw) { return draw < both || draw >= skillOnly && draw < any; }
        public boolean succeeded(double draw) { return draw < any; }
    }

    /** D04 union: the item coefficient never scales the existing skill opportunity. */
    public static Chance chance(double skillChance, double gearBaseChance, double itemCoefficient,
                                double existingResistance, GearEffectSnapshot source,
                                GearEffectSnapshot target) {
        if (!unit(skillChance) || !unit(itemCoefficient) || !Double.isFinite(gearBaseChance)
                || gearBaseChance < 0 || !Double.isFinite(existingResistance))
            throw new IllegalArgumentException("INVALID_STATUS_OPPORTUNITY");
        double g = Math.min(1, gearBaseChance) * itemCoefficient;
        double resistance = Math.clamp(existingResistance + target.percent("WA-079")
                - source.percent("WA-064"), 0, .75);
        double a = 1 - resistance;
        return new Chance(skillChance, g, resistance, a * skillChance,
                a * skillChance * g, a * (skillChance + g - skillChance * g));
    }

    private static boolean unit(double n) { return Double.isFinite(n) && n >= 0 && n <= 1; }

    public record ChillHit(UUID actor, String root, String strike, UUID target, boolean hostile,
                           boolean direct, boolean canProc, boolean protectedTarget,
                           double actualHealthLoss, double waterApplied, double itemCoefficient) { }
    public record ChillOutcome(String gate, boolean skillSucceeded, boolean gearSucceeded,
                               StatusService.ChillApplication application) { }

    public record AppliedHit(UUID actor,String root,String strike,UUID target,boolean hostile,
                             boolean direct,boolean canProc,boolean protectedTarget,
                             double actualHealthLoss,double itemCoefficient,
                             Map<com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel,Double> appliedChannels) {
        public AppliedHit { appliedChannels=Map.copyOf(appliedChannels); }
        public double applied(com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel channel) {
            return appliedChannels.getOrDefault(channel,0d);
        }
    }
    public record Admission(String gate,RpgStatusType type,boolean skillSucceeded,boolean gearSucceeded,
                            StatusService.Result controlResult,String periodicResult) { }
    /** The skill execution owner supplies its existing canonical Burn/Poison/Bleed package and Port. */
    @FunctionalInterface public interface PeriodicAdmission {
        String apply(PeriodicStatusRuntime.Kind kind,AppliedHit hit,GearEffectSnapshot source);
    }
    private static final Map<RpgStatusType,String> GEAR_CHANCES=Map.ofEntries(
            Map.entry(RpgStatusType.BLEED,"WA-053"),Map.entry(RpgStatusType.BURN,"WA-054"),
            Map.entry(RpgStatusType.POISON,"WA-055"),Map.entry(RpgStatusType.CHILL,"WA-056"),
            Map.entry(RpgStatusType.ELECTRIFIED,"WA-057"),Map.entry(RpgStatusType.SLOW,"WA-058"),
            Map.entry(RpgStatusType.STUN,"WA-059"),Map.entry(RpgStatusType.SILENCE,"WA-060"),
            Map.entry(RpgStatusType.BLIND,"WA-061"),Map.entry(RpgStatusType.FEAR,"WA-062"),
            Map.entry(RpgStatusType.ROOT,"WA-063"));
    private static double duration(RpgStatusType type) { return switch(type) {
        case SLOW -> 5;case STUN -> .6;case SILENCE,FEAR -> 1.5;case BLIND -> 2;case ROOT -> 1;
        default -> Double.NaN;
    }; }
    /** One source-owned status opportunity from a completed native hit. Periodic callbacks enter
     * the already registered skill PeriodicStatusRuntime, not a second timer or damage engine. */
    public static Admission admit(StatusService statuses,Contacts contacts,AppliedHit hit,RpgStatusType type,
            ControlProfile control,GearEffectSnapshot source,GearEffectSnapshot target,
            double existingResistance,double skillChance,double draw,double sourceDraw,PeriodicAdmission periodic) {
        return admit(statuses,contacts,hit,type,control,source,source,target,existingResistance,
                skillChance,draw,sourceDraw,periodic);
    }
    /** localSource owns item-only proc chance; globalSource carries valid equipped Status Penetration and DoT stats. */
    public static Admission admit(StatusService statuses,Contacts contacts,AppliedHit hit,RpgStatusType type,
            ControlProfile control,GearEffectSnapshot localSource,GearEffectSnapshot globalSource,
            GearEffectSnapshot target,double existingResistance,double skillChance,double draw,
            double sourceDraw,PeriodicAdmission periodic) {
        return admit(statuses,contacts,hit,type,control,localSource,globalSource,target,
                existingResistance,skillChance,draw,sourceDraw,periodic,"GEAR_BASIC_ATTACK",false);
    }
    public static Admission admit(StatusService statuses,Contacts contacts,AppliedHit hit,RpgStatusType type,
            ControlProfile control,GearEffectSnapshot localSource,GearEffectSnapshot globalSource,
            GearEffectSnapshot target,double existingResistance,double skillChance,double draw,
            double sourceDraw,PeriodicAdmission periodic,String canonicalSkill,boolean targetChannelActive) {
        return admit(statuses,contacts,hit,type,control,localSource,globalSource,target,existingResistance,
                skillChance,draw,sourceDraw,periodic,canonicalSkill,targetChannelActive,1);
    }
    public static Admission admit(StatusService statuses,Contacts contacts,AppliedHit hit,RpgStatusType type,
            ControlProfile control,GearEffectSnapshot localSource,GearEffectSnapshot globalSource,
            GearEffectSnapshot target,double existingResistance,double skillChance,double draw,
            double sourceDraw,PeriodicAdmission periodic,String canonicalSkill,boolean targetChannelActive,int skillStacks) {
        return admit(statuses,contacts,hit,type,control,localSource,globalSource,target,existingResistance,
                skillChance,draw,sourceDraw,periodic,canonicalSkill,targetChannelActive,skillStacks,false);
    }
    public static Admission admit(StatusService statuses,Contacts contacts,AppliedHit hit,RpgStatusType type,
            ControlProfile control,GearEffectSnapshot localSource,GearEffectSnapshot globalSource,
            GearEffectSnapshot target,double existingResistance,double skillChance,double draw,
            double sourceDraw,PeriodicAdmission periodic,String canonicalSkill,boolean targetChannelActive,
            int skillStacks,boolean deepFreeze) {
        if(statuses==null||contacts==null||hit==null||type==null||control==null||localSource==null
                ||globalSource==null||target==null
                ||!unit(draw)||draw==1||!unit(sourceDraw)||sourceDraw==1||!GEAR_CHANCES.containsKey(type))
            throw new IllegalArgumentException("INVALID_STATUS_ADMISSION");
        boolean eligible=hit.hostile()&&hit.direct()&&hit.canProc()&&!hit.protectedTarget()
                &&hit.actualHealthLoss()>0&&unit(hit.itemCoefficient());
        if(type==RpgStatusType.BLEED||type==RpgStatusType.STUN)
            eligible &= hit.applied(com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel.PHYSICAL)>0;
        if(type==RpgStatusType.BURN)
            eligible &= hit.applied(com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel.FIRE)>0;
        if(type==RpgStatusType.POISON)
            eligible &= hit.applied(com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel.PHYSICAL)>0
                    ||hit.applied(com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel.EARTH)>0;
        if(type==RpgStatusType.CHILL)
            eligible &= hit.applied(com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel.WATER)>0;
        if(type==RpgStatusType.ELECTRIFIED)
            eligible &= hit.applied(com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel.LIGHTNING)>0;
        if(!eligible)return new Admission("INELIGIBLE_HIT",type,false,false,null,null);
        if(!contacts.claim(hit.actor(),hit.root(),hit.strike(),hit.target(),type))
            return new Admission("DUPLICATE_CONTACT",type,false,false,null,null);
        double ordinaryChance=localSource.percent(GEAR_CHANCES.get(type));
        double rendingChance=type==RpgStatusType.BLEED?localSource.percent("WA-134"):0;
        double gearChance=ordinaryChance+rendingChance;
        var chance=chance(skillChance,gearChance,hit.itemCoefficient(),existingResistance,globalSource,target);
        boolean skill=chance.skillSucceeded(draw),gear=chance.gearSucceeded(draw);
        if(!skill&&!gear)return new Admission("CHANCE_MISS",type,false,false,null,null);
        if(type==RpgStatusType.BURN||type==RpgStatusType.POISON||type==RpgStatusType.BLEED) {
            if(periodic==null)throw new IllegalStateException("PERIODIC_STATUS_OWNER_REQUIRED");
            String result=periodic.apply(switch(type){case BURN->PeriodicStatusRuntime.Kind.BURN;
                case POISON->PeriodicStatusRuntime.Kind.POISON;default->PeriodicStatusRuntime.Kind.BLEED;},hit,globalSource);
            if(result==null||result.isBlank())throw new IllegalStateException("PERIODIC_STATUS_ADMISSION_MISSING");
            if(!result.equals("APPLIED")&&!result.equals("REFRESHED")&&!result.equals("QUEUED"))
                return new Admission("PERIODIC_REJECTED",type,skill,gear,null,result);
            if(type==RpgStatusType.BLEED&&gear&&rendingChance>0
                    &&sourceDraw<rendingChance/gearChance
                    &&(result.equals("APPLIED")||result.equals("REFRESHED")||result.equals("QUEUED")))
                statuses.suppressHealthRegeneration(hit.target(),hit.actor()+"/"+hit.root(),4);
            return new Admission("ADMITTED",type,skill,gear,null,result);
        }
        StatusService.Result result;
        if(type==RpgStatusType.CHILL) {
            var chill=statuses.applyChill(hit.actor(),hit.root(),hit.target(),control,skill?skillStacks:1,
                    skill&&deepFreeze,Double.NaN,target);
            result=chill.results().getLast();
        } else if(type==RpgStatusType.SLOW)result=statuses.applyStackingSlow(
                hit.actor(),canonicalSkill,hit.target(),control);
        else result=statuses.apply(hit.target(),type,control,duration(type),target,targetChannelActive);
        if(type==RpgStatusType.FEAR&&result.outcome()!=StatusService.Outcome.REJECTED)
            statuses.assignFearSource(hit.target(),hit.actor(),hit.root());
        return new Admission(result.outcome()==StatusService.Outcome.REJECTED?"CONTROL_REJECTED":"ADMITTED",
                type,skill,gear,result,null);
    }

    /** One real status admission for a completed Water hit, including any authored skill stacks. */
    public static ChillOutcome applyChill(StatusService statuses, Contacts contacts, ChillHit hit,
            ControlProfile control, GearEffectSnapshot source, GearEffectSnapshot target,
            double existingStatusResistance, double skillChance, int skillStacks, double draw) {
        if(statuses==null||contacts==null||hit==null||control==null||source==null||target==null
                ||!unit(draw) || draw==1 || skillStacks<1 || skillStacks>5)
            throw new IllegalArgumentException("INVALID_CHILL_OPPORTUNITY");
        if(!hit.hostile()||!hit.direct()||!hit.canProc()||hit.protectedTarget()
                ||hit.actualHealthLoss()<=0||hit.waterApplied()<=0)return new ChillOutcome("INELIGIBLE_HIT",false,false,null);
        if(!contacts.claim(hit.actor(),hit.root(),hit.strike(),hit.target(),RpgStatusType.CHILL))
            return new ChillOutcome("DUPLICATE_CONTACT",false,false,null);
        Chance chance=chance(skillChance,source.percent("WA-056"),hit.itemCoefficient(),
                existingStatusResistance,source,target);
        boolean skill=chance.skillSucceeded(draw),item=chance.gearSucceeded(draw);
        if(!skill&&!item)return new ChillOutcome("CHANCE_MISS",false,false,null);
        var applied=statuses.applyChill(hit.actor(),hit.root(),hit.target(),control,
                skill?skillStacks:1,false,Double.NaN,target);
        return new ChillOutcome("ADMITTED",skill,item,applied);
    }

    /** One target/status opportunity per authored root, including pellets and channel components. */
    public static final class Contacts {
        private final java.util.Map<Key,Long> accepted = new java.util.HashMap<>();
        private record Key(UUID actor, String root, UUID target, RpgStatusType status) { }
        public synchronized boolean claim(UUID actor, String root, String strike, UUID target, RpgStatusType status) {
            if (actor == null || root == null || root.isBlank() || strike == null || strike.isBlank()
                    || target == null || status == null) throw new IllegalArgumentException("INVALID_STATUS_CONTACT");
            long now=System.nanoTime();accepted.values().removeIf(end->end<=now);
            var key=new Key(actor,root,target,status);
            if(accepted.containsKey(key)||accepted.size()>=65536)return false;
            accepted.put(key,now+120_000_000_000L);return true;
        }
        public synchronized void forget(UUID actor) { accepted.keySet().removeIf(key -> key.actor.equals(actor)); }
        public synchronized boolean contains(UUID actor,String root,UUID target,RpgStatusType status){
            return accepted.containsKey(new Key(actor,root,target,status));
        }
    }
}
