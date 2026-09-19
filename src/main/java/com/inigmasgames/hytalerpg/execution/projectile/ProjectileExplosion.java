package com.inigmasgames.hytalerpg.execution.projectile;

/** Finite impact component; radius never changes carrier collision size. */
public record ProjectileExplosion(double radius,double coefficient,double burnRadius,double groundFuseSeconds,
                                  boolean detonateAtAirborneLimit,boolean continuationBeforeDetonation) {
    public static final ProjectileExplosion NONE=new ProjectileExplosion(0,0,0,0,false,false);
    public ProjectileExplosion(double radius,double coefficient,double burnRadius,double groundFuseSeconds,
                               boolean detonateAtAirborneLimit){this(radius,coefficient,burnRadius,groundFuseSeconds,detonateAtAirborneLimit,false);}
    public ProjectileExplosion {
        for(double n:new double[]{radius,coefficient,burnRadius,groundFuseSeconds})
            if(!Double.isFinite(n)||n<0)throw new IllegalArgumentException("INVALID_PROJECTILE_EXPLOSION");
        if(radius>64||burnRadius>radius||groundFuseSeconds>10||(radius==0)!=(coefficient==0)
                ||radius==0&&(groundFuseSeconds>0||detonateAtAirborneLimit||continuationBeforeDetonation))
            throw new IllegalArgumentException("INVALID_PROJECTILE_EXPLOSION");
    }
    public boolean active(){return radius>0;}
}
