package com.inigmasgames.hytalerpg.input;

import com.inigmasgames.hytalerpg.diagnostics.RpgSkillTracer;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceRecord;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutOperations;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stage-03 activation boundary. Executors intentionally do not exist until later stages. */
public final class RpgSkillActivationService {
    private final RpgLoadoutOperations loadouts;
    private final RpgSkillTracer tracer;

    public RpgSkillActivationService(RpgLoadoutOperations loadouts, RpgSkillTracer tracer) {
        this.loadouts = loadouts; this.tracer = tracer;
    }

    public Result request(HytaleAbilitySkillInputAdapter.Request request) {
        var state = loadouts.getLoadout(request.player()).state();
        var equipped = state.skill(request.slot());
        String rootCastId = "input-" + request.chainId();
        String instanceId = equipped.map(id -> id.value() + '-' + request.slot().externalId()).orElse("empty-" + request.slot().externalId());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rootCastId", rootCastId);
        details.put("skillInstanceId", instanceId);
        details.put("correlationId", request.correlationId());
        details.put("action", request.action());
        details.put("skillSlot", request.slot().externalId());
        details.put("skillId", equipped.map(id -> id.value()).orElse(""));
        trace(request, RpgTraceEventType.SKILL_ACTIVATION_REQUEST, details);
        Reason reason = equipped.isEmpty() ? Reason.EMPTY_SLOT : Reason.EXECUTOR_NOT_IMPLEMENTED;
        details.put("validationResult", "REJECTED");
        details.put("failureCode", reason.name());
        trace(request, RpgTraceEventType.SKILL_ACTIVATION_REJECTED, details);
        return new Result(false, reason, request.slot().externalId(), request.correlationId());
    }

    private void trace(HytaleAbilitySkillInputAdapter.Request request, RpgTraceEventType event,
                       Map<String, ?> details) {
        try { tracer.trace(RpgTraceRecord.create(request.player(), event, request.correlationId(), details)); }
        catch (Throwable ignored) { }
    }

    public enum Reason { EMPTY_SLOT, EXECUTOR_NOT_IMPLEMENTED }
    public record Result(boolean accepted, Reason reason, String skillSlot, String correlationId) { }
}
