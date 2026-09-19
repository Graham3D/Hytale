package com.inigmasgames.hytalerpg.execution.projectile;

/** Existing native particle bindings; scale is presentation-only and never changes gameplay geometry. */
public record ProjectilePresentation(String castParticle,String projectileParticle,String impactParticle,
                                     double projectileScale,double impactScale) {
    public static final ProjectilePresentation NONE=new ProjectilePresentation("","","",1,1);
    public ProjectilePresentation {
        castParticle=clean(castParticle);projectileParticle=clean(projectileParticle);impactParticle=clean(impactParticle);
        if(!Double.isFinite(projectileScale)||!Double.isFinite(impactScale)||projectileScale<=0||projectileScale>8
                ||impactScale<=0||impactScale>8||castParticle.length()>128||projectileParticle.length()>128||impactParticle.length()>128)
            throw new IllegalArgumentException("INVALID_PROJECTILE_PRESENTATION");
    }
    private static String clean(String value){return value==null?"":value.trim();}
    public boolean authored(){return !castParticle.isBlank()||!projectileParticle.isBlank()||!impactParticle.isBlank();}
}
