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
    public static Optional<Boolean> finiteDuration(String skill){
        var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(finiteDuration(p));
    }
    public static Optional<Boolean> affectedArea(String skill){
        var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(affectedArea(p));
    }
    public static Optional<Boolean> reactionWindow(String skill){
        var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(p.reaction()!=null);
    }
    public static Optional<Boolean> damagingMovement(String skill){
        var p=Canonical.PROFILES.all().get(skill);return p==null?Optional.empty():Optional.of(p.movement()!=null&&p.strike()!=null);
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
