package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.SkillExecutionRequest;
import com.inigmasgames.hytalerpg.execution.SkillExecutionResult;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfile;
import com.inigmasgames.hytalerpg.execution.SkillExecutionPort.Validation;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TeleportCastLifecycleTest {
    private static class Harness extends Stage09SupportRuntimeTest.Harness {
        int projectiles;
        Harness() {
            super("teleport");
            assertTrue(bundle.service().equipSkill(actor, SkillSlot.SKILL02, new SkillId("charged_bolt")).success());
        }
        @Override public Validation familyPrerequisites(Stage04SkillProfile p, CompiledSkillPlan plan) {
            return valid.equals("PASS") ? Validation.pass() : Validation.reject(valid);
        }
        @Override public SkillExecutionResult executeMovement(SkillExecutionContext value) {
            context = value;
            return SkillExecutionResult.committed("TELEPORT_PENDING_NATIVE", 0, 5);
        }
        @Override public SkillExecutionResult executeProjectile(SkillExecutionContext value) {
            projectiles++;
            return SkillExecutionResult.committed("PROJECTILE_DISPATCHED", 1, 0);
        }
        SkillExecutionResult bolt() {
            return execution.request(new SkillExecutionRequest(actor, SkillSlot.SKILL02,
                    "test", 2, "teleport-lifecycle", Vec3.FORWARD), this);
        }
    }

    @Test void pendingTeleportBlocksThenNativeCompletionReleasesOtherSkillsAndCooldownRemainsSpent() {
        var h = new Harness();
        var first = h.cast();
        assertEquals(SkillExecutionResult.Status.COMMITTED, first.status(), first.code());
        assertEquals("INCOMPATIBLE_ACTIVE_STATE", h.bolt().code());
        assertEquals("COOLDOWN_ACTIVE", h.cast().code());
        assertEquals(0, h.projectiles);
        double paidMana = h.mana;
        h.execution.terminate(h.context, "MOVEMENT_COMPLETE");
        h.execution.terminate(h.context, "MOVEMENT_COMPLETE");
        assertFalse(h.execution.pendingCast(h.actor));
        assertEquals(paidMana, h.mana);
        assertEquals(SkillExecutionResult.Status.COMMITTED, h.bolt().status());
        assertEquals(1, h.projectiles);
        assertEquals("COOLDOWN_ACTIVE", h.cast().code());
        // Remove only the test fixture's cooldown debt to exercise the next Teleport activation.
        assertTrue(h.kernel.cooldowns().clear(h.actor, "teleport"));
        var retry = h.cast();
        assertEquals(SkillExecutionResult.Status.COMMITTED, retry.status(), retry.code());
        h.execution.terminate(h.context, "MOVEMENT_COMPLETE");
    }

    @Test void nativeFailureOrDisconnectReleasesCastWithoutRefundingPaidRoot() {
        var failed = new Harness();
        assertEquals(SkillExecutionResult.Status.COMMITTED, failed.cast().status());
        double paid = failed.mana;
        failed.execution.terminate(failed.context, "NATIVE_TELEPORT_FAILED");
        assertEquals(paid, failed.mana);
        assertEquals(SkillExecutionResult.Status.COMMITTED, failed.bolt().status());

        var disconnected = new Harness();
        assertEquals(SkillExecutionResult.Status.COMMITTED, disconnected.cast().status());
        double spent = disconnected.mana;
        assertTrue(disconnected.execution.cancel(disconnected.actor, "PLAYER_DISCONNECT"));
        assertFalse(disconnected.execution.pendingCast(disconnected.actor));
        assertEquals(spent, disconnected.mana);
        assertEquals(SkillExecutionResult.Status.COMMITTED, disconnected.bolt().status());
    }

    @Test void rejectedTargetDoesNotLockChargedBolt() {
        var h = new Harness();
        h.valid = "OUT_OF_RANGE_OR_ELEVATION";
        assertFalse(h.cast().committed());
        assertFalse(h.execution.pendingCast(h.actor));
        h.valid = "PASS";
        assertEquals(SkillExecutionResult.Status.COMMITTED, h.bolt().status());
    }

    @Test void nativeDispatchExceptionTerminatesPaidCastAndPreservesOtherSkills() {
        var h = new Harness() {
            @Override public SkillExecutionResult executeMovement(SkillExecutionContext value) {
                context = value;
                throw new IllegalStateException("NATIVE_TELEPORT_QUEUE_FAILED");
            }
        };
        assertEquals(SkillExecutionResult.Status.TERMINATED, h.cast().status());
        double paid = h.mana;
        assertFalse(h.execution.pendingCast(h.actor));
        assertEquals("COOLDOWN_ACTIVE", h.cast().code());
        assertEquals(paid, h.mana);
        assertEquals(SkillExecutionResult.Status.COMMITTED, h.bolt().status());
    }
}
