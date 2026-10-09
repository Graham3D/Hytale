package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.meta.MetaKey;
import java.util.UUID;

/** Server-authoritative proc decision between a real mainhand selector and offhand selector.
 * Client waits for the authoritative branch before playing the offhand checkpoint. */
public final class NativeTwinAssaultGate extends SimpleInteraction {
    public static final String TYPE = "RPG_TwinAssaultGate";
    public static final BuilderCodec<NativeTwinAssaultGate> CODEC = BuilderCodec.builder(
            NativeTwinAssaultGate.class, NativeTwinAssaultGate::new, SimpleInteraction.CODEC).build();
    public record Commit(UUID actor, int chainId, UUID mainId, GearInstance offhand, GearEffectSnapshot snapshot) {}
    /** Pure commit decision used by the native checkpoint and offline contact qualification. */
    static Commit decide(UUID actor, int chainId, GearInstance main, GearInstance offhand,
                         GearEffectSnapshot snapshot, boolean compatibleUtility, double roll) {
        return compatibleUtility && NativeTwinAssaultEligibility.accepts(snapshot, main, offhand, roll)
                ? new Commit(actor, chainId, main.identity(), offhand, snapshot) : null;
    }
    private static final MetaKey<Commit> COMMITTED = Interaction.CONTEXT_META_REGISTRY.registerMetaObject(
            ignored -> null, false, "Hywind:TwinAssaultAcceptedOffhand", null);
    private static com.hypixel.hytale.server.core.meta.DynamicMetaStore<InteractionContext> rootStore(InteractionContext context) {
        var root = context.getInteractionManager().getChains().get(context.getChain().getChainId());
        return (root == null ? context.getChain() : root).getContext().getMetaStore();
    }
    public static Commit committed(InteractionContext context) {
        var value = rootStore(context).getIfPresentMetaObject(COMMITTED);
        return value != null && value.chainId() == context.getChain().getChainId() ? value : null;
    }

    @Override public WaitForDataFrom getWaitForDataFrom() { return WaitForDataFrom.Server; }
    @Override public boolean needsRemoteSync() { return true; }

    @Override protected void tick0(boolean first, float dt, InteractionType type, InteractionContext context,
                                   CooldownHandler cooldowns) {
        if (first) {
            boolean accepted = false;
            if (type == InteractionType.Primary && context.getEntity() == context.getOwningEntity()) {
                try {
                    var main = GearNativeItems.read(context.getHeldItem());
                    var utility = context.getCommandBuffer().getComponent(context.getOwningEntity(),
                            InventoryComponent.Utility.getComponentType());
                    var stack = utility == null ? null : utility.getActiveItem();
                    var offhand = GearNativeItems.read(stack);
                    var acceptedChain = NativeGearAcceptedContext.require(context,main);
                    if(acceptedChain==null)throw new IllegalStateException("TWIN_ACCEPTED_CHAIN_MISSING");
                    var snapshot = acceptedChain.snapshot();
                    UUID actor = context.getCommandBuffer().getComponent(context.getOwningEntity(),
                            com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType()).getUuid();
                    String key = actor + "/" + context.getChain().getChainId() + "/" + getId();
                    double roll = new java.util.SplittableRandom(stableHash(key)).nextDouble();
                    var commit = decide(actor, context.getChain().getChainId(), main, offhand, snapshot,
                            stack != null && stack.getItem() != null && stack.getItem().getUtility() != null
                                    && stack.getItem().getUtility().isCompatible(), roll);
                    accepted = commit != null;
                    if (accepted) rootStore(context).putMetaObject(COMMITTED,commit);
                } catch (RuntimeException invalid) {
                    System.err.println("GEAR_TWIN_CHECK_FAILED cause=" + invalid);
                    accepted = false;
                }
            }
            context.getState().state = accepted ? InteractionState.Finished : InteractionState.Failed;
        }
        super.tick0(first, dt, type, context, cooldowns);
    }

    private static long stableHash(String text) { long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < text.length(); i++) { hash ^= text.charAt(i); hash *= 0x100000001b3L; }
        return hash;
    }
}
