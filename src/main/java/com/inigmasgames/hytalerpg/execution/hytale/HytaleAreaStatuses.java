package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.status.ControlProfile;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.area.AreaWorldPort;
import com.inigmasgames.hytalerpg.execution.strike.StrikeGeometryService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Native status presentation/movement effects for spatial payloads; never contains native damage. */
final class HytaleAreaStatuses {
    private static final List<String> SLOWS = List.of("RPG_Chill_1", "RPG_Chill_2", "RPG_Chill_3", "RPG_Chill_4",
            "RPG_Frozen_Slow", "RPG_Root_Slow");
    static boolean available() {
        return java.util.stream.Stream.concat(SLOWS.stream(), java.util.stream.Stream.of("RPG_Frozen", "RPG_Root"))
                .allMatch(id -> EntityEffect.getAssetMap().getAsset(id) != null);
    }
    static void apply(RpgCombatKernel kernel, SkillExecutionContext context,
            StrikeGeometryService.Candidate<Ref<EntityStore>> target, AreaWorldPort.Payload payload, ControlProfile control,
            Store<EntityStore> store, Ref<EntityStore> owner,
            BiConsumer<RpgTraceEventType, Map<String, ?>> trace,com.inigmasgames.hytalerpg.execution.ChillSourceRegistry sources) {
        if (payload.status().isBlank()) return;
        UUID id = UUID.fromString(target.stableId());
        StatusService.Result result;
        if (payload.status().equals("CHILL")) {
            applyChill(kernel,context,id,control,payload.chillStacks(),trace,sources);
        } else if (payload.status().equals("ROOT") && control.boss()) {
            kernel.statuses().applySlow(id, context.rootCastId(), .35, payload.statusSeconds());
            trace.accept(RpgTraceEventType.STATUS_APPLIED, Map.of("targetId", target.stableId(), "status", "SLOW",
                    "magnitude", .35, "durationSeconds", payload.statusSeconds(), "detail", "AUTHORED_ROOT_SNARE_BOSS_SUBSTITUTE"));
        } else {
            result = kernel.statuses().apply(id, RpgStatusType.valueOf(payload.status()), control, payload.statusSeconds());
            record(result, target.stableId(), trace);
        }
        synchronize(kernel.statuses(), id, target.handle(), store, owner);
    }
    static StatusService.ChillApplication applyChill(RpgCombatKernel kernel,SkillExecutionContext context,UUID target,
            ControlProfile control,int stacks,BiConsumer<RpgTraceEventType,Map<String,?>> trace,com.inigmasgames.hytalerpg.execution.ChillSourceRegistry sources){
        var before=kernel.statuses().inspect(target).active().get(RpgStatusType.CHILL);
        var result=kernel.statuses().applyChill(context.request().actorId(),context.rootCastId(),target,control,stacks,context.compiledPlan().controls().deepFreeze());
        String provenance=sources.observed(context,target,before,kernel.statuses().inspect(target).active().get(RpgStatusType.CHILL),System.nanoTime()/1e9);
        if(provenance.endsWith("BUDGET"))trace.accept(RpgTraceEventType.STATUS_REJECTED,Map.of("targetId",target,"status","CHILL_PROVENANCE","reason",provenance,"nativeChillRetained",true));
        trace.accept(RpgTraceEventType.STATUS_REQUEST,Map.of("targetId",target,"status","CHILL","authoredStacks",stacks,
                "deepFreeze",context.compiledPlan().controls().deepFreeze(),"bonusGate",result.bonusGate(),"authority","RPG_SOURCE_CHILL"));
        for(var application:result.results())record(application,target.toString(),trace);
        return result;
    }
    static void synchronize(StatusService statuses, UUID id, Ref<EntityStore> target, Store<EntityStore> store,
                            Ref<EntityStore> owner) {
        if (!target.isValid()) return;
        EffectControllerComponent controller = store.getComponent(target, EffectControllerComponent.getComponentType());
        if (controller == null) return;
        var states = statuses.inspect(id).active();
        project(controller, target, store, owner, "RPG_Root", states.get(RpgStatusType.ROOT));
        project(controller, target, store, owner, "RPG_Frozen", states.get(RpgStatusType.FROZEN));
        var slow = statuses.strongestSlow(id);
        String selected = slow.magnitude() >= .35 ? "RPG_Root_Slow" : slow.magnitude() >= .30 ? "RPG_Frozen_Slow"
                : slow.magnitude() > 0 ? "RPG_Chill_" + Math.clamp((int) Math.round(slow.magnitude() / .05), 1, 4) : "";
        for (String effect : SLOWS) {
            if (!effect.equals(selected)) remove(controller, target, store, effect);
            else add(controller, target, store, owner, effect, Math.min(.3, slow.remainingSeconds()));
        }
    }
    private static void project(EffectControllerComponent controller, Ref<EntityStore> target, Store<EntityStore> store,
            Ref<EntityStore> owner, String effect, StatusService.StatusView view) {
        if (view == null) remove(controller, target, store, effect);
        else add(controller, target, store, owner, effect, view.remainingSeconds());
    }
    private static void add(EffectControllerComponent controller, Ref<EntityStore> target, Store<EntityStore> store,
                            Ref<EntityStore> owner, String effectId, double duration) {
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
        if (effect == null || duration <= 0 || !controller.addEffect(target, effect, (float) duration,
                OverlapBehavior.OVERWRITE, store, owner)) throw new IllegalStateException("NATIVE_STATUS_REJECTED_" + effectId);
    }
    private static void remove(EffectControllerComponent controller, Ref<EntityStore> target, Store<EntityStore> store, String effect) {
        int index = EntityEffect.getAssetMap().getIndex(effect);
        if (index >= 0) controller.removeEffect(target, index, store);
    }
    private static void record(StatusService.Result result, String target, BiConsumer<RpgTraceEventType, Map<String, ?>> trace) {
        RpgTraceEventType event = switch (result.outcome()) {
            case APPLIED -> RpgTraceEventType.STATUS_APPLIED; case REFRESHED -> RpgTraceEventType.STATUS_REFRESHED;
            case THRESHOLD -> RpgTraceEventType.STATUS_THRESHOLD; case REJECTED -> RpgTraceEventType.STATUS_REJECTED;
        };
        trace.accept(event, Map.of("targetId", target, "status", result.type().name(), "stacks", result.stacks(),
                "durationSeconds", result.remainingSeconds(), "detail", result.detail(), "authority", "RPG_KERNEL",
                "nativeBehaviorVerified", false));
    }
}
