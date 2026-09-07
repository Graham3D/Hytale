package com.inigmasgames.hytalerpg.ui.skilltree;

import com.inigmasgames.hytalerpg.domain.LinkEdge;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.progress.RpgPlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Fixed R016 presentation topology; it supplies no gameplay compatibility rules. */
public final class StaticSkillTreeLayout {
    public static final List<LinkNodeId> CONTENT_NODES = List.of(
            LinkNodeId.SKILL01, LinkNodeId.SKILL02, LinkNodeId.SKILL03,
            LinkNodeId.PASSIVE01, LinkNodeId.PASSIVE02, LinkNodeId.PASSIVE03,
            LinkNodeId.PASSIVE04, LinkNodeId.PASSIVE05, LinkNodeId.PASSIVE06);
    public static final List<LinkNodeId> JOINTS = List.of(LinkNodeId.JOINT01, LinkNodeId.JOINT02);
    public static final List<Adjacency> ADJACENCY = List.of(
            new Adjacency(LinkNodeId.PASSIVE01, LinkNodeId.JOINT01),
            new Adjacency(LinkNodeId.PASSIVE02, LinkNodeId.JOINT01),
            new Adjacency(LinkNodeId.PASSIVE03, LinkNodeId.JOINT01),
            new Adjacency(LinkNodeId.JOINT01, LinkNodeId.SKILL01),
            new Adjacency(LinkNodeId.PASSIVE04, LinkNodeId.JOINT02),
            new Adjacency(LinkNodeId.PASSIVE05, LinkNodeId.JOINT02),
            new Adjacency(LinkNodeId.JOINT02, LinkNodeId.SKILL02),
            new Adjacency(LinkNodeId.PASSIVE06, LinkNodeId.SKILL03));
    private static final Map<PassiveSlot, SkillSlot> PARENTS = Map.of(
            PassiveSlot.PASSIVE01, SkillSlot.SKILL01,
            PassiveSlot.PASSIVE02, SkillSlot.SKILL01,
            PassiveSlot.PASSIVE03, SkillSlot.SKILL01,
            PassiveSlot.PASSIVE04, SkillSlot.SKILL02,
            PassiveSlot.PASSIVE05, SkillSlot.SKILL02,
            PassiveSlot.PASSIVE06, SkillSlot.SKILL03);

    public SkillSlot parentSkill(PassiveSlot slot) { return PARENTS.get(slot); }

    /** Materializes only complete occupied routes so every persisted candidate remains valid. */
    public List<LinkEdge> authoritativeEdges(RpgPlayerState state) {
        List<LinkEdge> result = new ArrayList<>();
        appendJointBranch(result, state, SkillSlot.SKILL01, LinkNodeId.JOINT01,
                List.of(PassiveSlot.PASSIVE01, PassiveSlot.PASSIVE02, PassiveSlot.PASSIVE03));
        appendJointBranch(result, state, SkillSlot.SKILL02, LinkNodeId.JOINT02,
                List.of(PassiveSlot.PASSIVE04, PassiveSlot.PASSIVE05));
        if (state.skill(SkillSlot.SKILL03).isPresent() && state.passive(PassiveSlot.PASSIVE06).isPresent())
            result.add(LinkEdge.create(LinkNodeId.PASSIVE06, LinkNodeId.SKILL03));
        return List.copyOf(result);
    }

    private static void appendJointBranch(List<LinkEdge> out, RpgPlayerState state, SkillSlot skill,
                                          LinkNodeId joint, List<PassiveSlot> passives) {
        if (state.skill(skill).isEmpty()) return;
        boolean any = false;
        for (PassiveSlot passive : passives) {
            if (state.passive(passive).isPresent()) {
                out.add(LinkEdge.create(LinkNodeId.valueOf(passive.name()), joint));
                any = true;
            }
        }
        if (any) out.add(LinkEdge.create(joint, LinkNodeId.valueOf(skill.name())));
    }

    public record Adjacency(LinkNodeId source, LinkNodeId target) {}
}
