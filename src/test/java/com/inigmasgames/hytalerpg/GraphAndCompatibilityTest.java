package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.LinkEdge;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.links.ValidationCode;
import com.inigmasgames.hytalerpg.progress.RpgPlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GraphAndCompatibilityTest {
    @Test void representativeCompatibilityGatesAreExplicit() {
        var bundle = Stage01BTestSupport.bundle();
        RpgPlayerState state = RpgPlayerState.create(UUID.randomUUID());
        state.skill(SkillSlot.SKILL01, new SkillId("fire_bolt"));
        state.passive(PassiveSlot.PASSIVE01, new PassiveId("expanded_radius"));
        var fireBoltArea = bundle.graph().getCompatibility(state, PassiveSlot.PASSIVE01, SkillSlot.SKILL01);
        assertFalse(fireBoltArea.accepted());
        assertEquals(ValidationCode.MISSING_CAPABILITY, fireBoltArea.code());

        state.skill(SkillSlot.SKILL01, new SkillId("frost_nova"));
        assertTrue(bundle.graph().getCompatibility(state, PassiveSlot.PASSIVE01, SkillSlot.SKILL01).accepted());

        state.skill(SkillSlot.SKILL01, new SkillId("quick_slash"));
        state.passive(PassiveSlot.PASSIVE01, new PassiveId("fork"));
        var slashFork = bundle.graph().getCompatibility(state, PassiveSlot.PASSIVE01, SkillSlot.SKILL01);
        assertFalse(slashFork.accepted());
        assertEquals(ValidationCode.WRONG_FAMILY, slashFork.code());
    }

    @Test void directJointAndChainedJointRoutesValidate() {
        var bundle = Stage01BTestSupport.bundle();
        RpgPlayerState state = baseFireBolt();
        state.passive(PassiveSlot.PASSIVE01, new PassiveId("fork"));

        state.linkEdges(List.of(LinkEdge.create(LinkNodeId.PASSIVE01, LinkNodeId.SKILL01)));
        assertTrue(bundle.graph().validate(state).valid());

        state.linkEdges(List.of(
                LinkEdge.create(LinkNodeId.JOINT01, LinkNodeId.SKILL01),
                LinkEdge.create(LinkNodeId.PASSIVE01, LinkNodeId.JOINT01)));
        assertTrue(bundle.graph().validate(state).valid());
        assertEquals(List.of(LinkNodeId.JOINT01, LinkNodeId.SKILL01),
                bundle.graph().validate(state).routes().get(PassiveSlot.PASSIVE01));

        state.linkEdges(List.of(
                LinkEdge.create(LinkNodeId.JOINT02, LinkNodeId.SKILL01),
                LinkEdge.create(LinkNodeId.JOINT01, LinkNodeId.JOINT02),
                LinkEdge.create(LinkNodeId.PASSIVE01, LinkNodeId.JOINT01)));
        assertTrue(bundle.graph().validate(state).valid());
        assertEquals(List.of(LinkNodeId.JOINT01, LinkNodeId.JOINT02, LinkNodeId.SKILL01),
                bundle.graph().validate(state).routes().get(PassiveSlot.PASSIVE01));
    }

    @Test void illegalRelationshipsCyclesAndJointCapacityAreRejected() {
        var bundle = Stage01BTestSupport.bundle();
        RpgPlayerState state = baseFireBolt();
        state.linkEdges(List.of(LinkEdge.create(LinkNodeId.SKILL01, LinkNodeId.PASSIVE01)));
        assertEquals(ValidationCode.ILLEGAL_NODE_RELATIONSHIP, bundle.graph().validate(state).firstIssue().code());

        state.linkEdges(List.of(
                LinkEdge.create(LinkNodeId.JOINT01, LinkNodeId.JOINT02),
                LinkEdge.create(LinkNodeId.JOINT02, LinkNodeId.JOINT01)));
        assertTrue(bundle.graph().validate(state).issues().stream().anyMatch(issue -> issue.code() == ValidationCode.CYCLIC_GRAPH));

        state.passive(PassiveSlot.PASSIVE01, new PassiveId("potency"));
        state.passive(PassiveSlot.PASSIVE02, new PassiveId("efficiency"));
        state.passive(PassiveSlot.PASSIVE03, new PassiveId("long_reach"));
        state.linkEdges(List.of(
                LinkEdge.create(LinkNodeId.JOINT01, LinkNodeId.SKILL01),
                LinkEdge.create(LinkNodeId.PASSIVE01, LinkNodeId.JOINT01),
                LinkEdge.create(LinkNodeId.PASSIVE02, LinkNodeId.JOINT01),
                LinkEdge.create(LinkNodeId.PASSIVE03, LinkNodeId.JOINT01)));
        assertTrue(bundle.graph().validate(state).valid(), "three Passive inputs are canonical Joint capacity");
        state.passive(PassiveSlot.PASSIVE04, new PassiveId("fork"));
        state.linkEdges(List.of(
                LinkEdge.create(LinkNodeId.JOINT01, LinkNodeId.SKILL01),
                LinkEdge.create(LinkNodeId.PASSIVE01, LinkNodeId.JOINT01),
                LinkEdge.create(LinkNodeId.PASSIVE02, LinkNodeId.JOINT01),
                LinkEdge.create(LinkNodeId.PASSIVE03, LinkNodeId.JOINT01),
                LinkEdge.create(LinkNodeId.PASSIVE04, LinkNodeId.JOINT01)));
        assertTrue(bundle.graph().validate(state).issues().stream().anyMatch(issue -> issue.code() == ValidationCode.JOINT_INPUT_CAPACITY));

        state.linkEdges(List.of(LinkEdge.create(LinkNodeId.JOINT01, LinkNodeId.SKILL01),
                LinkEdge.create(LinkNodeId.JOINT01, LinkNodeId.SKILL02)));
        assertTrue(bundle.graph().validate(state).issues().stream()
                .anyMatch(issue -> issue.code() == ValidationCode.SOURCE_OUTPUT_CAPACITY));
    }

    @Test void sixPassiveGlobalBudgetCanSpecializeOneSkill() {
        var bundle = Stage01BTestSupport.bundle();
        RpgPlayerState state = baseFireBolt();
        String[] ids = {"potency", "efficiency", "long_reach", "fork", "chain", "return"};
        List<LinkEdge> edges = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            PassiveSlot slot = PassiveSlot.values()[index];
            state.passive(slot, new PassiveId(ids[index]));
            edges.add(LinkEdge.create(LinkNodeId.valueOf(slot.name()), LinkNodeId.SKILL01));
        }
        state.linkEdges(edges);
        assertTrue(bundle.graph().validate(state).valid());
        assertEquals(6, bundle.graph().validate(state).routes().size());
        assertThrows(IllegalArgumentException.class, () -> LinkNodeId.parse("passive07"));
    }

    @Test void everyCanonicalSkillPassiveAssessmentReturnsATypedVerdict() {
        var bundle = Stage01BTestSupport.bundle();
        var compatibility = new com.inigmasgames.hytalerpg.links.CompatibilityService();
        long assessments = 0;
        for (var skill : bundle.catalog().skills()) {
            for (var passive : bundle.catalog().passives()) {
                var result = compatibility.assess(skill, passive);
                assertNotNull(result.code());
                assertFalse(result.message().isBlank());
                assessments++;
            }
        }
        assertEquals(87L * 66L, assessments);
    }

    private static RpgPlayerState baseFireBolt() {
        RpgPlayerState state = RpgPlayerState.create(UUID.randomUUID());
        state.skill(SkillSlot.SKILL01, new SkillId("fire_bolt"));
        return state;
    }
}
