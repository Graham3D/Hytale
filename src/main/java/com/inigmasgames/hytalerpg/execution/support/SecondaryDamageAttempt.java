package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.NativeResult;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Exactly one native submission. An exception is not cancellation or proof of native completion. */
public record SecondaryDamageAttempt(NativeResult completed, double before, double after, RuntimeException failure) {
    public static SecondaryDamageAttempt once(DoubleSupplier health, Supplier<NativeResult> submit) {
        double before=health.getAsDouble();
        try {
            var result=submit.get();
            return new SecondaryDamageAttempt(result,result.healthBefore(),result.healthAfter(),null);
        } catch (RuntimeException failure) {
            double after;
            try {after=health.getAsDouble();} catch(RuntimeException unavailable){after=Double.NaN;failure.addSuppressed(unavailable);}
            return new SecondaryDamageAttempt(null,before,after,failure);
        }
    }
    public boolean transferAccepted() {
        return completed!=null?!completed.cancelled():Double.isFinite(before)&&Double.isFinite(after)&&before>after;
    }
}
