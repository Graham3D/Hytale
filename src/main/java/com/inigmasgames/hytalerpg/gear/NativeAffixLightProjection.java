package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import java.util.Objects;

/** WA-155 projection into the actor's normal native light component. */
public final class NativeAffixLightProjection {
    private NativeAffixLightProjection() {}
    // ProtocolCodecs.COLOR_LIGHT uses Codec.BYTE for authored assets (signed -128..127).
    // The renderer's usable positive ceiling and metres/unit must be measured on this client.
    public record Calibration(double metresPerUnit,int maximumNativeRadius) {
        public Calibration { if(!(metresPerUnit>0) || !Double.isFinite(metresPerUnit)
                ||maximumNativeRadius<1||maximumNativeRadius>127)
            throw new IllegalArgumentException("Invalid native light calibration"); }
        /** Main-owner input; fail closed until both renderer observations are supplied. */
        public static Calibration fromSystemProperties() {
            var scale=System.getProperty("rpg.gear.lightMetresPerNativeUnit");
            var ceiling=System.getProperty("rpg.gear.lightMaximumNativeRadius");
            if(scale==null||ceiling==null)throw new IllegalStateException("WA-155 renderer calibration missing");
            return new Calibration(Double.parseDouble(scale),Integer.parseInt(ceiling));
        }
    }
    public record Result(double targetMetres, int effectiveNativeRadius) {}
    public record Observation(int nativeRadius,double observedMetres) {
        public Observation { if(nativeRadius<1||nativeRadius>127||!Double.isFinite(observedMetres)
                ||observedMetres<=0)throw new IllegalArgumentException("Invalid light observation"); }
    }
    /** Three measured radii test the assumed linear mapping; ceiling is a separate client observation. */
    public static Calibration fit(Observation a,Observation b,Observation c,int observedCeiling,
                                  double maximumErrorMetres) {
        if(a.nativeRadius()==b.nativeRadius()||a.nativeRadius()==c.nativeRadius()
                ||b.nativeRadius()==c.nativeRadius()||!(maximumErrorMetres>0)
                ||!Double.isFinite(maximumErrorMetres))throw new IllegalArgumentException("Distinct light samples required");
        var samples=new Observation[]{a,b,c};double dot=0,squares=0;
        for(var sample:samples){dot+=sample.nativeRadius()*sample.observedMetres();
            squares+=(double)sample.nativeRadius()*sample.nativeRadius();}
        double scale=dot/squares;
        for(var sample:samples)if(Math.abs(sample.observedMetres()-sample.nativeRadius()*scale)>maximumErrorMetres)
            throw new IllegalArgumentException("Client radius is not linear within measured tolerance");
        return new Calibration(scale,observedCeiling);
    }

    public static Result project(DynamicLight actorLight, ColorLight normalHeldLight,
                                 GearEffectSnapshot validEquipment, Calibration calibration) {
        Objects.requireNonNull(actorLight);Objects.requireNonNull(normalHeldLight);
        Objects.requireNonNull(validEquipment);Objects.requireNonNull(calibration);
        int baseline=Byte.toUnsignedInt(normalHeldLight.radius);
        double bonus=validEquipment.total(GearEffectSnapshot.Operator.LIGHT_RADIUS);
        if (!Double.isFinite(bonus) || bonus<0) throw new IllegalArgumentException("Invalid light bonus");
        double target=Math.min(baseline,calibration.maximumNativeRadius())*calibration.metresPerUnit()+bonus;
        int radius=bonus==0?baseline:Math.max(baseline,Math.min(calibration.maximumNativeRadius(),
                Math.max(baseline+1,(int)Math.round(target/calibration.metresPerUnit()))));
        var applied=new ColorLight(normalHeldLight);
        applied.radius=(byte)radius;
        if (!applied.equals(actorLight.getColorLight())) actorLight.setColorLight(applied);
        return new Result(target,Math.min(radius,calibration.maximumNativeRadius()));
    }
}
