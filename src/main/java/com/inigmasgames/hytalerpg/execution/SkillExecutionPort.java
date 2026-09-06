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
    SkillExecutionResult executeStrike(SkillExecutionContext context);
    SkillExecutionResult executeMovement(SkillExecutionContext context);
    SkillExecutionResult executeReaction(SkillExecutionContext context);
    SkillExecutionResult executeProjectile(SkillExecutionContext context);

    record Equipment(Item mainHand, Item offHand) { }
    record Item(String itemId, String weaponKind, ItemPowerDescriptor power) { }
    record Validation(boolean accepted, String code) {
        public static Validation pass() { return new Validation(true, "PASS"); }
        public static Validation reject(String code) { return new Validation(false, code); }
    }
}
