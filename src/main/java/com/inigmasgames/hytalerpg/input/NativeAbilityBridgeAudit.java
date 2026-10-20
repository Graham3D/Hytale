package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.FirstClickInteraction;
import com.inigmasgames.hytalerpg.phase00.BuildIdentity;

/** One-shot runtime proof that the packaged native bridge resolves through Hytale's asset store. */
public final class NativeAbilityBridgeAudit {
    public static final String ROOT_ID = "Root_RPG_Ability_Bridge";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private NativeAbilityBridgeAudit() { }

    public static void onRootInteractionsLoaded(LoadedAssetsEvent<String, RootInteraction, ?> event) {
        RootInteraction root = event.getLoadedAssets().get(ROOT_ID);
        if (root == null) return;
        Result result = inspect(root);
        LOGGER.atInfo().log("RPG_NATIVE_BRIDGE_AUDIT revision=%s root=%s exists=true operations=%d operation=%s waitFor=%s operationRemote=%s rootRemote=%s effectFree=%s result=%s",
                BuildIdentity.REVISION, ROOT_ID, result.operationCount(), result.operationType(),
                result.waitFor(), result.operationRemote(), result.rootRemote(), result.effectFree(),
                result.pass() ? "PASS" : "FAIL");
        if (!result.pass()) throw new IllegalStateException("Unsafe or unsynchronized native ability bridge: " + result);
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
