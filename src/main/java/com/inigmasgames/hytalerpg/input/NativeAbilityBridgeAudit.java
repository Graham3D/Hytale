package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.FirstClickInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.operation.JumpOperation;
import com.hypixel.hytale.server.core.modules.interaction.interaction.operation.Operation;
import com.inigmasgames.hytalerpg.phase00.BuildIdentity;

/** One-shot runtime proof that the packaged native bridge resolves through Hytale's asset store. */
public final class NativeAbilityBridgeAudit {
    public static final String ROOT_ID = "Root_RPG_Ability_Bridge";
    public static final String SNIPE_ROOT_ID = "Root_RPG_Snipe_Release";
    public static final String HEALING_ROOT_ID = "Root_RPG_Healing_Beam_Held";
    public static final String FIREBALL_ROOT_ID = "Root_RPG_Fireball_Charge";
    public static String rootForItem(String item) {
        return "RPG_Ability_Healing_Beam".equals(item)?HEALING_ROOT_ID:"RPG_Ability_Snipe".equals(item) ? SNIPE_ROOT_ID :
                "RPG_Ability_Fireball".equals(item)?FIREBALL_ROOT_ID:ROOT_ID;
    }
    private static final class Log { static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass(); }

    private NativeAbilityBridgeAudit() { }

    public static void onRootInteractionsLoaded(LoadedAssetsEvent<String, RootInteraction, ?> event) {
        RootInteraction root = event.getLoadedAssets().get(ROOT_ID);
        if (root == null) return;
        Result result = inspect(root);
        Log.LOGGER.atInfo().log("RPG_NATIVE_BRIDGE_AUDIT revision=%s root=%s exists=true operations=%d operation=%s waitFor=%s operationRemote=%s rootRemote=%s effectFree=%s result=%s",
                BuildIdentity.REVISION, ROOT_ID, result.operationCount(), result.operationType(),
                result.waitFor(), result.operationRemote(), result.rootRemote(), result.effectFree(),
                result.pass() ? "PASS" : "FAIL");
        if (!result.pass()) throw new IllegalStateException("Unsafe or unsynchronized native ability bridge: " + result);
        RootInteraction fireball=event.getLoadedAssets().get(FIREBALL_ROOT_ID);
        if(fireball==null)throw new IllegalStateException("Missing Fireball charging root");
        NativeFireballChargeInteraction charge=null;int charges=0,releases=0,unexpected=0;
        for(int i=0;i<fireball.getOperationMax();i++){
            Operation operation=unwrap(fireball.getOperation(i));
            if(operation instanceof NativeFireballChargeInteraction candidate){charge=candidate;charges++;}
            else if(operation instanceof NativeSkillActivationInteraction)releases++;
            else if(!(operation instanceof JumpOperation))unexpected++;
        }
        boolean fireballPass=charges==1&&releases==1&&unexpected==0&&charge!=null
                &&charge.getWaitForDataFrom()==WaitForDataFrom.Client&&charge.needsRemoteSync()
                &&fireball.needsRemoteSync();
        Log.LOGGER.atInfo().log("RPG_FIREBALL_CHARGE_AUDIT revision=%s root=%s operations=%d charge=%s waitFor=%s rootRemote=%s effectFree=true result=%s connectedProof=false",
                BuildIdentity.REVISION,FIREBALL_ROOT_ID,fireball.getOperationMax(),charge==null?"NONE":charge.getClass().getSimpleName(),
                charge==null?WaitForDataFrom.None:charge.getWaitForDataFrom(),fireball.needsRemoteSync(),fireballPass?"PASS":"FAIL");
        if(!fireballPass)throw new IllegalStateException("Unsafe Fireball charging root");
    }

    private static Operation unwrap(Operation operation){
        for(int depth=0;operation instanceof Operation.NestedOperation nested;depth++){
            if(depth>=8)throw new IllegalStateException("FIREBALL_OPERATION_NESTING_LIMIT");
            operation=nested.inner();
        }
        return operation;
    }

    public static Result inspect(RootInteraction root) {
        int operationCount = root == null ? 0 : root.getOperationMax();
        Object operation = operationCount >= 1 ? root.getOperation(0) : null;
        boolean firstClick = operation instanceof FirstClickInteraction;
        WaitForDataFrom waitFor = firstClick
                ? ((FirstClickInteraction) operation).getWaitForDataFrom() : WaitForDataFrom.None;
        boolean operationRemote = firstClick && ((FirstClickInteraction) operation).needsRemoteSync();
        boolean rootRemote = root != null && root.needsRemoteSync();
        boolean callback = operationCount == 2 && root.getOperation(1) instanceof NativeSkillActivationInteraction activation
                && activation.getWaitForDataFrom() == WaitForDataFrom.Server && activation.needsRemoteSync();
        // Exact whitelist: FirstClick + server dispatch callback, no native damage/stat/cost/cooldown operation.
        boolean effectFree = firstClick && callback;
        return new Result(operationCount, operation == null ? "" : operation.getClass().getSimpleName()
                + (callback ? "+NativeSkillActivationInteraction" : ""),
                waitFor, operationRemote, rootRemote, effectFree);
    }

    public record Result(int operationCount, String operationType, WaitForDataFrom waitFor,
                         boolean operationRemote, boolean rootRemote, boolean effectFree) {
        public boolean pass() {
            return operationCount == 2 && "FirstClickInteraction+NativeSkillActivationInteraction".equals(operationType)
                    && waitFor == WaitForDataFrom.Client && operationRemote && rootRemote && effectFree;
        }
    }
}
