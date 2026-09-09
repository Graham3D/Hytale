package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.Optional;

/** Single owned terminal component. Cancellation is never a detonation trigger. */
public final class ProjectileDetonation {
    private final ProjectileExplosion profile;
    private Vec3 ground;
    private double fuseAt=Double.POSITIVE_INFINITY;
    private boolean claimed,cancelled;
    public ProjectileDetonation(ProjectileExplosion profile){this.profile=java.util.Objects.requireNonNull(profile);}
    public Optional<Vec3> contact(Vec3 point,boolean eligibleEnemy,double now) {
        require(point,now);
        if(!profile.active()||claimed||cancelled)return Optional.empty();
        if(eligibleEnemy||profile.groundFuseSeconds()==0)return claim(point);
        if(ground==null){ground=point;fuseAt=now+profile.groundFuseSeconds();}
        return Optional.empty();
    }
    public Optional<Vec3> tick(Vec3 point,double now,boolean airborneExpired) {
        require(point,now);
        if(!profile.active()||claimed||cancelled)return Optional.empty();
        if(ground!=null)return now+1e-9>=fuseAt?claim(ground):Optional.empty();
        return airborneExpired&&profile.detonateAtAirborneLimit()?claim(point):Optional.empty();
    }
    public boolean grounded(){return ground!=null&&!claimed&&!cancelled;}
    public void cancel(){cancelled=true;}
    private Optional<Vec3> claim(Vec3 point){claimed=true;return Optional.of(point);}
    private static void require(Vec3 point,double now){if(point==null||!Double.isFinite(now))throw new IllegalArgumentException("INVALID_DETONATION_CLOCK");}
}
