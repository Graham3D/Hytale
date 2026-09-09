package com.inigmasgames.hytalerpg.execution.strike;

import com.inigmasgames.hytalerpg.execution.SkillExecutionPort;
import java.util.function.Supplier;

/** Shared initial/delayed strike admission. Empty geometry is a paid miss, not a missing target.
 * The bounded query still rejects overflow before admission; victims are resolved again on release. */
public final class StrikeCastPrerequisites {
    private StrikeCastPrerequisites() { }
    public static SkillExecutionPort.Validation check(Supplier<? extends StrikeGeometryService.QueryResult<?>> query) {
        try { query.get(); return SkillExecutionPort.Validation.pass(); }
        catch (IllegalStateException failure) {
            if ("STRIKE_QUERY_OVERFLOW".equals(failure.getMessage()) || "STRIKE_TARGET_CAP_OVERFLOW".equals(failure.getMessage()))
                return SkillExecutionPort.Validation.reject(failure.getMessage());
            throw failure;
        }
    }
}
