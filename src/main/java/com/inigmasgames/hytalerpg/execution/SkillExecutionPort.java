package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;

/** Hytale-facing capabilities. Pure orchestration never reaches into client state. */
public interface SkillExecutionPort {
    boolean actorAliveAndUsable();
    Equipment equipment();
    NativeResourcePort resources();
    Validation familyPrerequisites(Stage04SkillProfile profile, CompiledSkillPlan plan);
    default CommittedTarget captureTarget(Stage04SkillProfile profile, CompiledSkillPlan plan, SkillExecutionRequest request) { return null; }
    default Validation validateRelease(SkillExecutionContext context) { return Validation.reject("COMMITTED_TARGET_ADAPTER_UNAVAILABLE"); }
    default void abandonRelease(SkillExecutionContext context) { }
    /** Null means no owned active Aura. Stopping one is not a second activation transaction. */
    default SkillExecutionResult stopActiveSupport(Stage04SkillProfile profile){return null;}
    SkillExecutionResult executeStrike(SkillExecutionContext context);
    SkillExecutionResult executeMovement(SkillExecutionContext context);
    SkillExecutionResult executeReaction(SkillExecutionContext context);
    SkillExecutionResult executeProjectile(SkillExecutionContext context);
    default SkillExecutionResult executeArea(SkillExecutionContext context) {
        throw new UnsupportedOperationException("AREA_NATIVE_PORT_UNAVAILABLE");
    }
    default SkillExecutionResult executeConnection(SkillExecutionContext context) {
        throw new UnsupportedOperationException("CONNECTION_NATIVE_PORT_UNAVAILABLE");
    }
    default SkillExecutionResult executeSupport(SkillExecutionContext context) {
        throw new UnsupportedOperationException("SUPPORT_NATIVE_PORT_UNAVAILABLE");
    }

    record Equipment(Item mainHand, Item offHand) { }
    record Item(String itemId, String weaponKind, ItemPowerDescriptor power) { }
    record Validation(boolean accepted, String code) {
        public static Validation pass() { return new Validation(true, "PASS"); }
        public static Validation reject(String code) { return new Validation(false, code); }
    }
}
