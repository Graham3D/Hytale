package com.inigmasgames.hytalerpg.execution.projectile;

/** Authored motion contract. Native physics transports the carrier; these values remain server authority. */
public record ProjectileMotion(String mode,double horizontalSpeed,double gravity,double minimumTravelSeconds,
                               double maximumTravelSeconds,double safetyLifetimeSeconds) {
    public static final ProjectileMotion LINEAR=new ProjectileMotion("LINEAR",0,0,0,0,0);
    public ProjectileMotion {
        mode=mode==null||mode.isBlank()?"LINEAR":mode;
        for(double value:new double[]{horizontalSpeed,gravity,minimumTravelSeconds,maximumTravelSeconds,safetyLifetimeSeconds})
            if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("INVALID_PROJECTILE_MOTION");
        if(!java.util.Set.of("LINEAR","TIMED_BALLISTIC").contains(mode)
                ||mode.equals("LINEAR")&&(horizontalSpeed!=0||gravity!=0||minimumTravelSeconds!=0||maximumTravelSeconds!=0||safetyLifetimeSeconds!=0)
                ||mode.equals("TIMED_BALLISTIC")&&(horizontalSpeed<=0||gravity<=0||minimumTravelSeconds<=0
                    ||maximumTravelSeconds<minimumTravelSeconds||safetyLifetimeSeconds<=0||safetyLifetimeSeconds>.5))
            throw new IllegalArgumentException("INVALID_PROJECTILE_MOTION");
    }
    public boolean timedBallistic(){return mode.equals("TIMED_BALLISTIC");}
}
