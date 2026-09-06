package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.domain.EdgeId;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.links.CompatibilityResult;
import com.inigmasgames.hytalerpg.links.CompilationResult;

import java.util.Map;
import java.util.UUID;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;

/** Stable backend seam for command UI and future CanvasUI/Noesis adapters. */
public interface RpgLoadoutOperations {
    MutationResult equipSkill(UUID player, SkillSlot slot, SkillId skill);
    MutationResult unequipSkill(UUID player, SkillSlot slot);
    MutationResult equipPassive(UUID player, PassiveSlot slot, PassiveId passive);
    MutationResult unequipPassive(UUID player, PassiveSlot slot);
    MutationResult link(UUID player, LinkNodeId source, LinkNodeId target);
    MutationResult unlink(UUID player, EdgeId edge);
    MutationResult unlinkSource(UUID player, LinkNodeId source);
    CompilationResult compile(UUID player);
    RpgLoadoutView getLoadout(UUID player);
    Map<LinkNodeId, CompatibilityResult> getCompatibleTargets(UUID player, LinkNodeId source);
    MutationResult setDevelopmentAttribute(UUID player, RpgAttribute attribute, int rawValue);
    MutationResult resetDevelopmentAttributes(UUID player);
}
