package com.inigmasgames.persistentnpcs.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.sentinel.OrbisDegradationSentinel;
import com.inigmasgames.persistentnpcs.sentinel.OrbisIncidentRecorder;
import com.inigmasgames.persistentnpcs.sentinel.RegressionCandidateExtractor;
import java.util.Locale;

/** Native OP-only Sentinel status/replay/export control surface. */
public final class SentinelOperatorCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> operation;
    private final OrbisDegradationSentinel sentinel;
    private final OrbisIncidentRecorder incidents;
    private final RegressionCandidateExtractor candidates;

    public SentinelOperatorCommand(OrbisDegradationSentinel sentinel,
            OrbisIncidentRecorder incidents, RegressionCandidateExtractor candidates) {
        super("immersivesentinel", "Inspect or replay Immersive NPC Sentinel diagnostics");
        this.sentinel = sentinel;
        this.incidents = incidents;
        this.candidates = candidates;
        requireNoPermission();
        operation = withRequiredArg("operation", "status, incidents, candidates, circuits, "
                + "replay <id>, smoke, export candidate|incident <id>",
                ArgTypes.GREEDY_STRING);
    }

    @Override protected void execute(CommandContext context, Store<EntityStore> store,
            Ref<EntityStore> playerEntityRef, PlayerRef playerRef, World world) {
        if (!PermissionsModule.get().getGroupsForUser(playerRef.getUuid())
                .contains(ImmersiveNpcTraceCommand.OPERATOR_GROUP)) {
            context.sendMessage(Message.raw("Only a server operator can control Sentinel."));
            return;
        }
        String raw = context.get(operation).strip();
        String[] parts = raw.split("\\s+", 3);
        String action = parts[0].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "status" -> context.sendMessage(Message.raw(sentinel.diagnostics()));
                case "incidents" -> context.sendMessage(Message.raw(
                        "Sentinel incidents: " + incidents.snapshot()));
                case "candidates" -> context.sendMessage(Message.raw(
                        "Sentinel candidates: " + candidates.snapshot()));
                case "circuits" -> context.sendMessage(Message.raw(
                        "Sentinel circuits: " + sentinel.circuits().snapshot()
                                + " quarantined=" + sentinel.quarantinedScopes()));
                case "replay" -> {
                    require(parts, 2, "replay <candidate-id>");
                    candidates.replay(parts[1]).whenComplete((result, failure) ->
                            playerRef.sendMessage(Message.raw(failure == null
                                    ? "Sentinel replay: " + result
                                    : "Sentinel replay failed: " + reason(failure))));
                }
                case "smoke" -> candidates.smoke().whenComplete((results, failure) ->
                        playerRef.sendMessage(Message.raw(failure == null
                                ? "Sentinel prior-revision smoke: " + results
                                : "Sentinel smoke failed: " + reason(failure))));
                case "export" -> {
                    require(parts, 3, "export candidate|incident <id>");
                    java.nio.file.Path path = switch (parts[1].toLowerCase(Locale.ROOT)) {
                        case "candidate" -> candidates.export(parts[2]);
                        case "incident" -> incidents.exportIncident(parts[2]);
                        default -> throw new IllegalArgumentException(
                                "Use export candidate|incident <id>");
                    };
                    context.sendMessage(Message.raw("Sentinel export: " + path));
                }
                default -> throw new IllegalArgumentException("Unknown operation. Use status, "
                        + "incidents, candidates, circuits, replay, smoke, or export.");
            }
        } catch (RuntimeException failure) {
            context.sendMessage(Message.raw("Sentinel command failed: " + reason(failure)));
        }
    }

    private static void require(String[] parts, int count, String usage) {
        if (parts.length < count) throw new IllegalArgumentException("Usage: /immersivesentinel "
                + usage);
    }
    private static String reason(Throwable failure) {
        Throwable value = failure instanceof java.util.concurrent.CompletionException
                && failure.getCause() != null ? failure.getCause() : failure;
        return value.getMessage() == null ? value.getClass().getSimpleName()
                : value.getMessage();
    }
}
