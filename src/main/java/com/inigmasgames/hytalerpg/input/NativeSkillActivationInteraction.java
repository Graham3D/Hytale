package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/** Native execution-side entry point. No native gameplay effect or client simulation execution. */
public final class NativeSkillActivationInteraction extends SimpleInstantInteraction {
    public static final String TYPE = "RPG_ActivateSkill";
    private final HytaleAbilitySkillInputAdapter inputs;

    public NativeSkillActivationInteraction(HytaleAbilitySkillInputAdapter inputs) { this.inputs = inputs; }

    public static BuilderCodec<NativeSkillActivationInteraction> codec(HytaleAbilitySkillInputAdapter inputs) {
        return BuilderCodec.builder(NativeSkillActivationInteraction.class,
                () -> new NativeSkillActivationInteraction(inputs), SimpleInstantInteraction.CODEC).build();
    }

    @Override public WaitForDataFrom getWaitForDataFrom() { return WaitForDataFrom.Server; }
    @Override public boolean needsRemoteSync() { return true; }

    @Override protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        var buffer = context.getCommandBuffer();
        var owner = context.getOwningEntity();
        var chain = context.getChain();
        if (buffer == null || owner == null || !owner.isValid() || chain == null) return;
        // Do not activate proxy actors, child/fork chains, foreign runes or Signature Move.
        var player = buffer.getComponent(owner, PlayerRef.getComponentType());
        if (player == null) return;
        if (!owner.equals(context.getEntity()) || chain.getForkedChainId() != null) {
            inputs.diagnostic(player.getUuid(), "BRIDGE_REJECTED", java.util.Map.of(
                    "reason", "PROXY_OR_FORK", "action", type.name(), "chainId", chain.getChainId()));
            return;
        }
        var original = context.getOriginalItemType();
        var ability = original != null && original.getAbility() instanceof com.hypixel.hytale.server.core.asset.type.item.config.CoreItemAbility a ? a : null;
        if (ability == null || !NativeAbilityBridgeAudit.rootForItem(original.getId()).equals(ability.getCastRootId())) {
            inputs.diagnostic(player.getUuid(), "BRIDGE_REJECTED", java.util.Map.of(
                    "reason", "ORIGINAL_ITEM_OR_ROOT_MISMATCH", "action", type.name(),
                    "chainId", chain.getChainId(), "originalItem", original == null ? "" : original.getId()));
            return;
        }
        inputs.acceptNativeExecution(player.getUuid(), type, chain.getChainId(), chain,
                original.getId(), context.getHeldItemSlot());
    }

    @Override protected void simulateFirstRun(InteractionType type, InteractionContext context,
                                              CooldownHandler cooldowns) {
        // SimpleInstantInteraction's default calls firstRun. Simulation must NEVER enqueue RPG gameplay.
    }
}
