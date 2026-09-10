package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.operation.JumpOperation;
import com.hypixel.hytale.server.core.modules.interaction.interaction.operation.Operation;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import java.util.*;

/** Resolved native structure only; connected hold/release and visuals still require owner QA. */
public final class NativeSnipeReleaseAudit {
    private NativeSnipeReleaseAudit() {}
    public static Map<String,Object> requireAssets() {
        var root=RootInteraction.getAssetMap().getAsset(NativeAbilityBridgeAudit.SNIPE_ROOT_ID);
        if(root==null||!root.needsRemoteSync()||!(unwrap(root.getOperation(0)) instanceof ChargingInteraction charge))
            throw new IllegalStateException("SNIPE_NATIVE_CHARGING_ROOT_MISSING:"+(root==null?"null":
                    "remote="+root.needsRemoteSync()+",count="+root.getOperationMax()+",first="+root.getOperation(0).getClass().getName()));
        var packet=(com.hypixel.hytale.protocol.ChargingInteraction)charge.toPacket();
        requirePacket(packet);
        int callbacks=0,charges=0;
        var operations=new ArrayList<String>();
        for(int i=0;i<root.getOperationMax();i++) {
            var op=unwrap(root.getOperation(i)); operations.add(op.getClass().getSimpleName());
            if(op instanceof NativeSkillActivationInteraction)callbacks++;
            else if(op instanceof ChargingInteraction)charges++;
            else if(!(op instanceof JumpOperation))throw new IllegalStateException("SNIPE_UNEXPECTED_NATIVE_OPERATION:"+op.getClass().getSimpleName());
        }
        if(callbacks!=1||charges!=1)throw new IllegalStateException("SNIPE_RELEASE_CALLBACK_COUNT");
        var animations=ItemPlayerAnimations.getAssetMap().getAsset("Shortbow");
        for(String action:List.of("ShootChargingHold","ShootCharged")) {
            var a=animations==null?null:animations.getAnimations().get(action);
            if(a==null||a.firstPerson==null||a.thirdPerson==null||a.looping!=action.equals("ShootChargingHold"))
                throw new IllegalStateException("SNIPE_NATIVE_ANIMATION_MISSING:"+action);
        }
        return Map.of("root",root.getId(),"operations",operations,"fullyDrawnImmediately",true,
                "releaseThresholdSeconds",0,"nativeGameplayBranches",false,"connectedProof",false);
    }
    private static Operation unwrap(Operation op) {
        // The native compiler wraps branch operations with their resolved labels.
        for(int depth=0;op instanceof Operation.NestedOperation nested;depth++) {
            if(depth>=8)throw new IllegalStateException("SNIPE_OPERATION_NESTING_LIMIT");
            op=nested.inner();
        }
        return op;
    }
    public static void requirePacket(com.hypixel.hytale.protocol.ChargingInteraction p) {
        if(p.waitForDataFrom!=WaitForDataFrom.Client||!p.allowIndefiniteHold||p.displayProgress||!p.cancelOnOtherClick||!p.failOnDamage
                ||p.chargedNext==null||!p.chargedNext.keySet().equals(Set.of(0f))||p.failed!=Integer.MIN_VALUE
                ||p.forks!=null&&!p.forks.isEmpty()||p.effects==null
                ||!"Shortbow".equals(p.effects.itemPlayerAnimationsId)||!"ShootChargingHold".equals(p.effects.itemAnimationId)
                ||!p.effects.clearAnimationOnFinish)
            throw new IllegalStateException("SNIPE_NATIVE_RELEASE_CONTRACT_INVALID:wait="+p.waitForDataFrom+",hold="+p.allowIndefiniteHold+
                    ",progress="+p.displayProgress+",cancel="+p.cancelOnOtherClick+",damage="+p.failOnDamage+",next="+p.chargedNext+
                    ",failed="+p.failed+",forks="+p.forks+",effects="+(p.effects==null?"null":
                    p.effects.itemPlayerAnimationsId+"/"+p.effects.itemAnimationId+"/clear="+p.effects.clearAnimationOnFinish));
    }
}
