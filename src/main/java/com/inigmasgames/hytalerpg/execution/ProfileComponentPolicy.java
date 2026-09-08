package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile;
import com.inigmasgames.hytalerpg.execution.support.SupportProfile;
import java.util.Optional;
import java.util.Set;

/** Runtime component evidence refines imported catalog summary tags; never grants global tags. */
public final class ProfileComponentPolicy {
    private ProfileComponentPolicy(){}
    private static final class Canonical {static final Stage04SkillProfiles PROFILES=Stage04SkillProfiles.loadCanonical(RpgCatalog.loadCanonical());}
    public static Optional<Boolean> conditionalRepeat(String skill,boolean critical){var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(conditionalRepeat(p,critical));}
    public static boolean conditionalRepeat(Stage04SkillProfile p,boolean critical){
        return p.damageCoefficient()>0&&p.movement()==null&&p.reaction()==null&&p.summon()==null&&p.summonAction()==null&&p.conversion()==null&&p.cage()==null&&p.support()==null
                &&!(p.connection()!=null&&p.connection().channel())&&(!critical||directDamage(p));
    }
    public static Optional<Boolean> discreteStrike(String skill,boolean excludeAuthoredSequence){
        var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(discreteStrike(p,excludeAuthoredSequence));
    }
    public static Optional<Boolean> directDamage(String skill){var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(directDamage(p));}
    public static boolean directDamage(Stage04SkillProfile p){return p.damageCoefficient()>0&&p.summon()==null&&p.summonAction()==null&&p.cage()==null
            &&!(p.area()!=null&&p.area().periodic())&&!(p.connection()!=null&&(p.connection().channel()||p.connection().kind()==ConnectionProfile.Kind.ORB));}
    public static Optional<Boolean> frontalStrike(String skill){
        var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(discreteStrike(p,false)&&p.strike().geometry()!=Stage04SkillProfile.Geometry.RADIUS);
    }
    public static boolean discreteStrike(Stage04SkillProfile p,boolean excludeAuthoredSequence){
        return p.family()==Stage04SkillProfile.Family.STRIKE&&p.strike()!=null&&p.strike().coefficient()>0
                &&(!excludeAuthoredSequence||p.strike().repeats()==1);
    }
    public static Optional<Boolean> finiteUpfront(String skill){var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(finiteUpfront(p));}
    public static boolean finiteUpfront(Stage04SkillProfile p){
        return Set.of("MANA","STAMINA").contains(p.resourceType())&&p.resourceCost()>0
                &&!(p.connection()!=null&&p.connection().channel())
                &&!(p.support()!=null&&(p.support().aura()||p.support().upkeepPerSecond()>0));
    }
    public static Optional<Boolean> chillPayload(String skill){var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(chillPayload(p));}
    public static boolean chillPayload(Stage04SkillProfile p){
        return p.projectile()!=null&&p.projectile().statusId().equals("CHILL")
                ||p.area()!=null&&(p.area().status().equals("CHILL")&&p.area().chillStacks()>0||p.area().alternatingIceStone())
                ||p.support()!=null&&p.support().chillInterval()>0;
    }
    public static Optional<Boolean> dotPayload(String skill,String status){var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(dotPayload(p,status));}
    public static boolean dotPayload(Stage04SkillProfile p,String status){
        return p.area()!=null&&p.area().status().equals(status)&&p.area().statusSeconds()>0
                ||p.projectile()!=null&&p.projectile().hasPeriodicStatus()&&p.projectile().statusId().equals(status);
    }
    public static boolean periodicPulse(String skill){var p=Canonical.PROFILES.all().get(skill);return p!=null&&periodicPulse(p);}
    public static boolean periodicPulse(Stage04SkillProfile p){
        return p.area()!=null&&!p.area().trap()&&(p.area().periodic()||p.family()==Stage04SkillProfile.Family.GROUND_ZONE&&p.area().impactCount()>1)
                ||p.connection()!=null&&Set.of(ConnectionProfile.Kind.BEAM,ConnectionProfile.Kind.DRAIN,ConnectionProfile.Kind.ORB).contains(p.connection().kind())
                ||p.support()!=null&&(p.support().damageInterval()>0||p.support().chillInterval()>0);
    }
    public static boolean mobileZone(String skill){
        var p=Canonical.PROFILES.all().get(skill);return p!=null&&mobileZone(p);
    }
    public static boolean mobileZone(Stage04SkillProfile p){
        var a=p.area();
        // Warned/stratified impacts own a particular terrain placement; moving it after
        // warning would invalidate the telegraph. No collision wall, corpse or trap seam.
        return p.family()==Stage04SkillProfile.Family.GROUND_ZONE&&a!=null
                &&a.geometry()==com.inigmasgames.hytalerpg.execution.area.AreaGeometry.Kind.DISC
                &&a.lifetimeSeconds()>0&&(a.periodic()||a.impactCount()>1)
                &&!a.stratified()&&!a.trap()&&a.warningSeconds()==0&&a.overheadHeight()==0&&a.armingSeconds()==0;
    }
    public static Optional<Boolean> finiteDuration(String skill){
        var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(finiteDuration(p));
    }
    public static Optional<Boolean> affectedArea(String skill){
        var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(affectedArea(p));
    }
    public static Optional<Boolean> enemyPosition(String skill){var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(enemyPosition(p));}
    public static boolean cascade(String skill){var p=Canonical.PROFILES.all().get(skill);return p!=null&&cascade(p);}
    public static boolean orbit(String skill){var p=Canonical.PROFILES.all().get(skill);return p!=null&&com.inigmasgames.hytalerpg.execution.connection.OrbitConversionProfiles.eligible(p);}
    public static int baseOrbitCount(String skill){var p=Canonical.PROFILES.require(skill);return p.connection()!=null&&p.connection().kind()==ConnectionProfile.Kind.ORBIT?Math.min(3,p.connection().details().bladeCount()):1;}
    public static boolean cascade(Stage04SkillProfile p){return p.area()!=null&&p.area().geometry()==com.inigmasgames.hytalerpg.execution.area.AreaGeometry.Kind.DISC&&p.area().placementRange()>0&&!p.area().trap();}
    public static boolean aftermath(String skill){var p=Canonical.PROFILES.all().get(skill);return p!=null&&aftermath(p);}
    public static boolean aftermath(Stage04SkillProfile p){return p.area()!=null&&p.area().lifetimeSeconds()>0&&(p.area().periodic()||p.area().impactCount()>1||p.area().trap())
            ||p.connection()!=null&&Set.of(ConnectionProfile.Kind.ORB,ConnectionProfile.Kind.ORBIT).contains(p.connection().kind());}
    public static double baseAreaRadius(String skill){var p=Canonical.PROFILES.require(skill);if(!cascade(p))throw new IllegalArgumentException("NO_CASCADE_AREA");return p.area().radius();}
    public static boolean enemyPosition(Stage04SkillProfile p){
        return p.area()!=null||p.cage()!=null||p.summonAction()!=null&&p.summonAction().radius()>0
                ||p.support()!=null&&p.support().radius()>0&&(p.support().damageInterval()>0||p.support().chillInterval()>0)
                ||p.strike()!=null&&affectedArea(p)
                ||p.connection()!=null&&Set.of(ConnectionProfile.Kind.WAVE,ConnectionProfile.Kind.LINE,ConnectionProfile.Kind.BEAM,ConnectionProfile.Kind.ORB,ConnectionProfile.Kind.ORBIT).contains(p.connection().kind());
    }
    public static Optional<Boolean> reactionWindow(String skill){
        var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(p.reaction()!=null);
    }
    public static Optional<Boolean> damagingMovement(String skill){
        var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(p.movement()!=null&&p.strike()!=null);
    }
    public static Optional<Boolean> impact(String skill){
        var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(impact(p));
    }
    public static boolean impact(Stage04SkillProfile p){
        return p.strike()!=null&&p.strike().statusId().equals("STAGGER")&&p.strike().statusSeconds()>0
                ||p.projectile()!=null&&(p.projectile().knockbackDistance()>0||p.projectile().statusId().equals("STAGGER")&&p.projectile().statusSeconds()>0)
                ||p.area()!=null&&(p.area().displacement()>0||p.area().status().equals("STAGGER")&&p.area().statusSeconds()>0)
                ||p.connection()!=null&&p.connection().details().status().equals("STAGGER")&&p.connection().details().statusSeconds()>0;
    }
    public static Optional<Boolean> resolvingWidth(String skill){
        var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(resolvingWidth(p));
    }
    public static boolean resolvingWidth(Stage04SkillProfile p){
        return p.connection()!=null&&Set.of(ConnectionProfile.Kind.WAVE,ConnectionProfile.Kind.LINE,ConnectionProfile.Kind.BEAM).contains(p.connection().kind());
    }
    public static boolean affectedArea(Stage04SkillProfile p){
        return p.area()!=null||p.cage()!=null||p.summonAction()!=null&&p.summonAction().radius()>0
                ||p.support()!=null&&p.support().radius()>0||p.movement()!=null&&p.movement().landingRadius()>0
                ||p.strike()!=null&&(p.strike().geometry()==Stage04SkillProfile.Geometry.RADIUS||p.strike().targetCap()>1&&p.strike().geometry()!=Stage04SkillProfile.Geometry.ASSIST_CONE)
                ||p.connection()!=null&&Set.of(ConnectionProfile.Kind.WAVE,ConnectionProfile.Kind.LINE,ConnectionProfile.Kind.BEAM,
                    ConnectionProfile.Kind.ORB,ConnectionProfile.Kind.ORBIT).contains(p.connection().kind());
    }
    public static boolean finiteDuration(Stage04SkillProfile p){
        return p.summon()!=null||p.cage()!=null||p.summonAction()!=null&&p.summonAction().duration()>0
                ||p.support()!=null&&p.support().durationSeconds()>0&&p.support().kind()!=SupportProfile.Kind.FEAR&&p.support().kind()!=SupportProfile.Kind.TAUNT
                ||p.connection()!=null&&Set.of(ConnectionProfile.Kind.ORB,ConnectionProfile.Kind.ORBIT).contains(p.connection().kind())
                ||p.area()!=null&&(p.area().lifetimeSeconds()>0&&(p.area().trap()||p.area().periodic()||p.area().impactCount()>1)
                    ||periodicStatus(p.area().status())&&p.area().statusSeconds()>0)
                ||p.projectile()!=null&&p.projectile().hasPeriodicStatus()
                ||p.strike()!=null&&periodicStatus(p.strike().statusId())&&p.strike().statusSeconds()>0;
    }
    public static boolean periodicStatus(String status){return Set.of("BURN","POISON","BLEED").contains(status);}
}
