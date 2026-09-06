package com.inigmasgames.hytalerpg.combat.diagnostics;

import com.inigmasgames.hytalerpg.diagnostics.RpgSkillTracer;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Adds root/instance correlation and preserves the non-authoritative trace failure boundary. */
public final class CombatTrace {
    private final RpgSkillTracer tracer;
    public CombatTrace(RpgSkillTracer tracer) { this.tracer = tracer; }
    public void emit(UUID actor, RpgTraceEventType type, Context context, Map<String, ?> numericDetails) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rootCastId", context.rootCastId());
        details.put("skillInstanceId", context.skillInstanceId());
        details.putAll(numericDetails);
        try { tracer.trace(RpgTraceRecord.create(actor, type, context.correlationId(), details)); }
        catch (Throwable ignored) { /* Diagnostics never change combat outcome. */ }
    }
    public record Context(String rootCastId, String skillInstanceId, String correlationId) {
        public Context {
            if (rootCastId == null || skillInstanceId == null || correlationId == null)
                throw new IllegalArgumentException("Combat trace identifiers are required");
        }
    }
}
