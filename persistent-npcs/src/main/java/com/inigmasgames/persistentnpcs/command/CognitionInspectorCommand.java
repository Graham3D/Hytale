package com.inigmasgames.persistentnpcs.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.cognition.CognitionTraceStore;
import com.inigmasgames.persistentnpcs.cognition.ResponseLatencyTraceStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.ui.CognitionInspectorPage;
import com.inigmasgames.persistentnpcs.autonomy.AgentOperationStore;
import com.inigmasgames.persistentnpcs.voice.VoiceInteractionTraceStore;
import com.inigmasgames.persistentnpcs.ai.AiServiceRouter;
import com.inigmasgames.persistentnpcs.orbis.OrbisRuntime;
import com.inigmasgames.persistentnpcs.diagnostics.NpcTraceManager;
import com.inigmasgames.persistentnpcs.sentinel.OrbisDegradationSentinel;

/** Supported plugin-owned admin command; it does not modify Hytale's /npc tree. */
public final class CognitionInspectorCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> nameArg;
    private final NpcProfileRegistry profiles;
    private final CognitionTraceStore traces;
    private final ResponseLatencyTraceStore latency;
    private final AgentOperationStore operations;
    private final VoiceInteractionTraceStore voiceTraces;
    private final AiServiceRouter aiServices;
    private final OrbisRuntime orbisRuntime;
    private final NpcTraceManager npcTraces;
    private final OrbisDegradationSentinel degradationSentinel;

    public CognitionInspectorCommand(NpcProfileRegistry profiles, CognitionTraceStore traces) {
        this(profiles, traces, new ResponseLatencyTraceStore(), null);
    }

    public CognitionInspectorCommand(NpcProfileRegistry profiles, CognitionTraceStore traces,
            ResponseLatencyTraceStore latency) {
        this(profiles, traces, latency, null);
    }

    public CognitionInspectorCommand(NpcProfileRegistry profiles, CognitionTraceStore traces,
            ResponseLatencyTraceStore latency, AgentOperationStore operations) {
        this(profiles, traces, latency, operations, null);
    }

    public CognitionInspectorCommand(NpcProfileRegistry profiles, CognitionTraceStore traces,
            ResponseLatencyTraceStore latency, AgentOperationStore operations,
            VoiceInteractionTraceStore voiceTraces) {
        this(profiles, traces, latency, operations, voiceTraces, null);
    }

    public CognitionInspectorCommand(NpcProfileRegistry profiles, CognitionTraceStore traces,
            ResponseLatencyTraceStore latency, AgentOperationStore operations,
            VoiceInteractionTraceStore voiceTraces, AiServiceRouter aiServices) {
        this(profiles, traces, latency, operations, voiceTraces, aiServices, null, null);
    }

    public CognitionInspectorCommand(NpcProfileRegistry profiles, CognitionTraceStore traces,
            ResponseLatencyTraceStore latency, AgentOperationStore operations,
            VoiceInteractionTraceStore voiceTraces, AiServiceRouter aiServices,
            OrbisRuntime orbisRuntime, NpcTraceManager npcTraces) {
        this(profiles, traces, latency, operations, voiceTraces, aiServices,
                orbisRuntime, npcTraces, null);
    }

    public CognitionInspectorCommand(NpcProfileRegistry profiles, CognitionTraceStore traces,
            ResponseLatencyTraceStore latency, AgentOperationStore operations,
            VoiceInteractionTraceStore voiceTraces, AiServiceRouter aiServices,
            OrbisRuntime orbisRuntime, NpcTraceManager npcTraces,
            OrbisDegradationSentinel degradationSentinel) {
        super("immersivecognition", "Open the Immersive NPC cognition inspector");
        this.profiles = profiles;
        this.traces = traces;
        this.latency = latency;
        this.operations = operations;
        this.voiceTraces = voiceTraces;
        this.aiServices = aiServices;
        this.orbisRuntime = orbisRuntime;
        this.npcTraces = npcTraces;
        this.degradationSentinel = degradationSentinel;
        this.nameArg = withRequiredArg("name", "Immersive NPC name", ArgTypes.GREEDY_STRING);
        requirePermission("inigmasgames.immersivenpcs.debug.cognition");
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store,
            Ref<EntityStore> playerEntityRef, PlayerRef playerRef, World world) {
        try {
            var profile = profiles.requireName(context.get(nameArg));
            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            if (player == null) throw new IllegalStateException("Player page manager unavailable");
            var page = new CognitionInspectorPage(playerRef, profile,
                    traces.latest(profile.id()).orElse(null),
                    latency.latest(profile.id()).orElse(null),
                    operations == null ? null : operations.latestFor(
                            profile.id(), "GUIDE_PLAYER_TO_NPC").orElse(null),
                    voiceTraces == null ? null : voiceTraces.latest(profile.id()).orElse(null),
                    aiServices, orbisRuntime, npcTraces, degradationSentinel);
            player.getPageManager().openCustomPage(playerEntityRef, store, page);
        } catch (RuntimeException failure) {
            context.sendMessage(Message.raw("Cognition inspector failed: "
                    + (failure.getMessage() == null ? failure.getClass().getSimpleName()
                            : failure.getMessage())));
        }
    }
}
