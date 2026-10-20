package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.diagnostics.RpgSkillTracer;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.links.ValidationCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CompilerAndTransactionTest {
    @Test void requiredFireBoltForkSequenceCompilesCanonicalChildren() {
        var bundle = Stage01BTestSupport.bundle();
        UUID player = UUID.randomUUID();
        assertTrue(bundle.service().equipSkill(player, SkillSlot.SKILL02, new SkillId("fire_bolt")).success());
        assertTrue(bundle.service().equipPassive(player, PassiveSlot.PASSIVE06, new PassiveId("fork")).success());
        assertTrue(bundle.service().link(player, LinkNodeId.PASSIVE06, LinkNodeId.SKILL02).success());
        var plan = bundle.service().compile(player).plans().get(SkillSlot.SKILL02);
        assertEquals("PROJECTILE", plan.finalFamily());
        assertEquals(2, bundle.catalog().passive(new PassiveId("fork")).orElseThrow().childProjectileCount());
        assertTrue(plan.continuation().contains("FORK(children=2,angles=-20/+20,depth=1)"));
    }

    @Test void compilerIsIdempotentAndOrderingDoesNotFollowInsertionOrder() {
        var first = Stage01BTestSupport.bundle();
        UUID player = UUID.randomUUID();
        first.service().equipSkill(player, SkillSlot.SKILL01, new SkillId("fire_bolt"));
        first.service().equipPassive(player, PassiveSlot.PASSIVE01, new PassiveId("chain"));
        first.service().equipPassive(player, PassiveSlot.PASSIVE02, new PassiveId("fork"));
        first.service().link(player, LinkNodeId.PASSIVE01, LinkNodeId.SKILL01);
        first.service().link(player, LinkNodeId.PASSIVE02, LinkNodeId.SKILL01);
        String hash1 = first.service().compile(player).plans().get(SkillSlot.SKILL01).planHash();
        String hash2 = first.service().compile(player).plans().get(SkillSlot.SKILL01).planHash();
        assertEquals(hash1, hash2);
        assertEquals(java.util.List.of(new PassiveId("fork"), new PassiveId("chain")),
                first.service().compile(player).plans().get(SkillSlot.SKILL01).passiveOrder());
    }

    @Test void failedRelinkRollsBackExistingValidFireBoltRouteAndTracesRejection() {
        var bundle = Stage01BTestSupport.bundle();
        UUID player = UUID.randomUUID();
        bundle.service().equipSkill(player, SkillSlot.SKILL02, new SkillId("fire_bolt"));
        bundle.service().equipSkill(player, SkillSlot.SKILL01, new SkillId("quick_slash"));
        bundle.service().equipPassive(player, PassiveSlot.PASSIVE06, new PassiveId("fork"));
        assertTrue(bundle.service().link(player, LinkNodeId.PASSIVE06, LinkNodeId.SKILL02).success());
        long revision = bundle.service().getLoadout(player).state().revision;
        var rejected = bundle.service().link(player, LinkNodeId.PASSIVE06, LinkNodeId.SKILL01);
        assertFalse(rejected.success());
        assertEquals(ValidationCode.WRONG_FAMILY, rejected.code());
        var loadout = bundle.service().getLoadout(player);
        assertEquals(revision, loadout.state().revision);
        assertEquals(LinkNodeId.SKILL02, loadout.routes().get(PassiveSlot.PASSIVE06).getLast());
        var tracer = (Stage01BTestSupport.RecordingTracer) bundle.tracer();
        assertTrue(tracer.records.stream().anyMatch(record -> record.eventType() == RpgTraceEventType.LINK_REJECTED
                && record.details().get("failureCode").equals("WRONG_FAMILY")));
        String rejectedCorrelation = tracer.records.stream()
                .filter(record -> record.eventType() == RpgTraceEventType.LINK_REJECTED).reduce((a, b) -> b).orElseThrow().correlationId();
        assertTrue(tracer.records.stream().anyMatch(record -> record.eventType() == RpgTraceEventType.LINK_REQUEST
                && record.correlationId().equals(rejectedCorrelation)));
    }

    @Test void equipAndUnequipFlowUsesOneTransactionalAuthority() {
        var bundle = Stage01BTestSupport.bundle();
        UUID player = UUID.randomUUID();
        bundle.service().equipSkill(player, SkillSlot.SKILL01, new SkillId("fire_bolt"));
        bundle.service().equipPassive(player, PassiveSlot.PASSIVE01, new PassiveId("fork"));
        bundle.service().link(player, LinkNodeId.PASSIVE01, LinkNodeId.SKILL01);
        assertTrue(bundle.service().unequipPassive(player, PassiveSlot.PASSIVE01).success());
        assertTrue(bundle.service().getLoadout(player).routes().isEmpty());
        assertTrue(bundle.service().getLoadout(player).state().passive(PassiveSlot.PASSIVE01).isEmpty());
        assertTrue(bundle.service().unequipSkill(player, SkillSlot.SKILL01).success());
        assertTrue(bundle.service().getLoadout(player).state().skill(SkillSlot.SKILL01).isEmpty());
    }

    @Test void persistenceFailureLeavesCachedStateUnchangedAndTraceFailureIsNonAuthoritative() {
        var repository = new Stage01BTestSupport.InMemoryRepository();
        RpgSkillTracer failingTracer = record -> { throw new IllegalStateException("trace down"); };
        var bundle = Stage01BTestSupport.bundle(repository, failingTracer);
        UUID player = UUID.randomUUID();
        assertTrue(bundle.service().equipSkill(player, SkillSlot.SKILL01, new SkillId("fire_bolt")).success());
        repository.failSave = true;
        var failed = bundle.service().equipPassive(player, PassiveSlot.PASSIVE01, new PassiveId("fork"));
        assertFalse(failed.success());
        assertEquals(ValidationCode.PERSISTENCE_FAILURE, failed.code());
        assertTrue(bundle.service().getLoadout(player).state().passive(PassiveSlot.PASSIVE01).isEmpty());
    }

    @Test void unknownUnlinkedContentProducesExplicitDegradedPlanWithoutCrash() {
        var repository = new Stage01BTestSupport.InMemoryRepository();
        UUID player = UUID.randomUUID();
        var state = com.inigmasgames.hytalerpg.progress.RpgPlayerState.create(player);
        state.skill(SkillSlot.SKILL01, new SkillId("removed_skill"));
        repository.states.put(player, state);
        var bundle = Stage01BTestSupport.bundle(repository, new Stage01BTestSupport.RecordingTracer());
        var plan = bundle.service().compile(player).plans().get(SkillSlot.SKILL01);
        assertTrue(plan.degraded());
        assertTrue(plan.degradedReasons().getFirst().contains("missing"));
    }
}
