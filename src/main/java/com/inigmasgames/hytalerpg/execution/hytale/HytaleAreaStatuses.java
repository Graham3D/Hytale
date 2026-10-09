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
    static com.inigmasgames.hytalerpg.progress.ControlEvidence observed(Store<EntityStore> store,Ref<EntityStore> target) {
        var controller=target==null||!target.isValid()?null:store.getComponent(target,EffectControllerComponent.getComponentType());
        if(controller==null)return new com.inigmasgames.hytalerpg.progress.ControlEvidence(false,0);
        boolean hard=java.util.stream.Stream.of("RPG_Root","RPG_Frozen","Stun").anyMatch(id->present(controller,id));
        double slow=0;double[] strengths={.05,.10,.15,.20,.30,.35};
        for(int i=0;i<SLOWS.size();i++)if(present(controller,SLOWS.get(i)))slow=Math.max(slow,strengths[i]);
        return new com.inigmasgames.hytalerpg.progress.ControlEvidence(hard,slow);
    }
    static com.inigmasgames.hytalerpg.progress.ControlEvidence observed(StatusService statuses,
            Store<EntityStore> store,Ref<EntityStore> target){
        var nativeView=observed(store,target);
        if(target==null||!target.isValid())return nativeView;
        var id=store.getComponent(target,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
        if(id==null)return nativeView;
        double exact=statuses.strongestSlow(id.getUuid(),
                com.inigmasgames.hytalerpg.gear.GearNativeItems.recipientEffects(target,store)).magnitude();
        return new com.inigmasgames.hytalerpg.progress.ControlEvidence(nativeView.immobilized(),Math.max(nativeView.slow(),exact));
    }
    private static boolean present(EffectControllerComponent controller,String id){
        var effect=EntityEffect.getAssetMap().getAsset(id);return effect!=null&&controller.hasEffect(effect);
    }
    static boolean available() {
        return java.util.stream.Stream.concat(SLOWS.stream(), java.util.stream.Stream.of("RPG_Frozen", "RPG_Root",
                        "RPG_Chill_Icon_1","RPG_Chill_Icon_2","RPG_Chill_Icon_3","RPG_Chill_Icon_4",
                        "RPG_Affix_Stun","RPG_Affix_Silence","RPG_Affix_Blind"))
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
            applyChill(kernel,context,id,control,payload.chillStacks(),trace,sources,
                    com.inigmasgames.hytalerpg.gear.GearNativeItems.recipientEffects(target.handle(),store));
        } else if (payload.status().equals("ROOT") && control.boss()) {
            kernel.statuses().applySlow(id, context.rootCastId(), .35, payload.statusSeconds());
            trace.accept(RpgTraceEventType.STATUS_APPLIED, Map.of("targetId", target.stableId(), "status", "SLOW",
                    "magnitude", .35, "durationSeconds", payload.statusSeconds(), "detail", "AUTHORED_ROOT_SNARE_BOSS_SUBSTITUTE"));
        } else {
            result = kernel.statuses().apply(id, RpgStatusType.valueOf(payload.status()), control, payload.statusSeconds(),
                    com.inigmasgames.hytalerpg.gear.GearNativeItems.recipientEffects(target.handle(),store));
            record(result, target.stableId(), trace);
        }
        synchronize(kernel.statuses(), id, target.handle(), store, owner);
    }
    static StatusService.ChillApplication applyChill(RpgCombatKernel kernel,SkillExecutionContext context,UUID target,
            ControlProfile control,int stacks,BiConsumer<RpgTraceEventType,Map<String,?>> trace,com.inigmasgames.hytalerpg.execution.ChillSourceRegistry sources){
        return applyChill(kernel,context,target,control,stacks,trace,sources,
                com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.EMPTY);
    }
    static StatusService.ChillApplication applyChill(RpgCombatKernel kernel,SkillExecutionContext context,UUID target,
            ControlProfile control,int stacks,BiConsumer<RpgTraceEventType,Map<String,?>> trace,
            com.inigmasgames.hytalerpg.execution.ChillSourceRegistry sources,
            com.inigmasgames.hytalerpg.gear.GearEffectSnapshot targetGear){
        var before=kernel.statuses().inspect(target).active().get(RpgStatusType.CHILL);
        var result=kernel.statuses().applyChill(context.request().actorId(),context.rootCastId(),target,control,stacks,
                context.compiledPlan().controls().deepFreeze(),Double.NaN,targetGear);
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
        // Presentation follows actual Chill stacks, independent of the strongest-only movement channel.
        var chill=states.get(RpgStatusType.CHILL);
        for(int stack=1;stack<=4;stack++)project(controller,target,store,owner,"RPG_Chill_Icon_"+stack,
                chill!=null&&chill.stacks()==stack?chill:null);
        project(controller, target, store, owner, "RPG_Root", states.get(RpgStatusType.ROOT));
        project(controller, target, store, owner, "RPG_Frozen", states.get(RpgStatusType.FROZEN));
        project(controller,target,store,owner,"RPG_Affix_Stun",states.get(RpgStatusType.STUN));
        project(controller,target,store,owner,"RPG_Affix_Silence",states.get(RpgStatusType.SILENCE));
        project(controller,target,store,owner,"RPG_Affix_Blind",states.get(RpgStatusType.BLIND));
        var slow = statuses.strongestSlow(id);
        // Native speed effects have discrete strengths. NPC steering and player MovementManager
        // each receive the exact strongest value from their single owner instead.
        for(String effect:SLOWS)remove(controller,target,store,effect);
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
