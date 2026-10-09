package com.inigmasgames.hytalerpg.gear;

import com.google.gson.*;

/** Frozen per-leaf proc shares. The native damage codec must retain these fields and forward
 * them to NativeGearAttackAcceptance at its exact leaf checkpoint. */
final class NativeActionProcMetadata {
    private NativeActionProcMetadata() {}
    static void mark(JsonElement tree, String selector) {
        mark(tree, selector, 1.0);
    }
    static void mark(JsonElement tree, String selector, double coefficient) {
        if (!Double.isFinite(coefficient) || coefficient <= 0 || coefficient > 1)
            throw new IllegalArgumentException("Invalid authored native proc share");
        if (tree.isJsonArray()) { for (JsonElement child : tree.getAsJsonArray()) mark(child, selector, coefficient); return; }
        if (!tree.isJsonObject()) return;
        JsonObject object = tree.getAsJsonObject();
        if (object.has("Type") && object.get("Type").isJsonPrimitive()
                && (object.get("Type").getAsString().equals(ManagedGearDamageInteraction.TYPE)
                || object.get("Type").getAsString().equals(ManagedCarrierDamageInteraction.TYPE))) {
            if (object.has(NativeGearAttackAcceptance.PROC_SELECTOR)
                    || object.has(NativeGearAttackAcceptance.PROC_COEFFICIENT)) {
                if (!object.has(NativeGearAttackAcceptance.PROC_SELECTOR)
                        || !object.has(NativeGearAttackAcceptance.PROC_COEFFICIENT)
                        || !object.get(NativeGearAttackAcceptance.PROC_SELECTOR).getAsString().equals(selector)
                        || Math.abs(object.get(NativeGearAttackAcceptance.PROC_COEFFICIENT).getAsDouble()
                                - coefficient) > 1e-9)
                    throw new IllegalStateException("Pinned native proc leaf changed: " + selector);
            }
            object.addProperty(NativeGearAttackAcceptance.PROC_SELECTOR, selector);
            object.addProperty(NativeGearAttackAcceptance.PROC_COEFFICIENT, coefficient);
        }
        for (var child : object.entrySet()) mark(child.getValue(), selector, coefficient);
    }
}
