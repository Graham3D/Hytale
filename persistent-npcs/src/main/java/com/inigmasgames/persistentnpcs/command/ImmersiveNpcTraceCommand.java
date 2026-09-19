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
import com.inigmasgames.persistentnpcs.diagnostics.NpcTraceManager;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import java.util.UUID;
import java.util.function.Predicate;

/** OP-only toggle for an ephemeral, profile-local NPC black-box trace session. */
public final class ImmersiveNpcTraceCommand extends AbstractPlayerCommand {
    public static final String OPERATOR_GROUP = "hytale:Admin";
    private final RequiredArg<String> nameArg;
    private final NpcProfileRegistry profiles;
    private final NpcTraceManager traces;
    private final Predicate<UUID> operatorCheck;

    public ImmersiveNpcTraceCommand(NpcProfileRegistry profiles, NpcTraceManager traces) {
        this(profiles, traces, playerId -> PermissionsModule.get().getGroupsForUser(playerId)
                .contains(OPERATOR_GROUP));
    }

    /** Visible for serverless authorization tests. */
    public ImmersiveNpcTraceCommand(NpcProfileRegistry profiles, NpcTraceManager traces,
            Predicate<UUID> operatorCheck) {
        super("trace", "Toggle an operator trace for one Immersive NPC");
        this.profiles = java.util.Objects.requireNonNull(profiles, "profiles");
        this.traces = java.util.Objects.requireNonNull(traces, "traces");
        this.operatorCheck = java.util.Objects.requireNonNull(operatorCheck, "operatorCheck");
        // Authorization is checked against Hytale's actual OP group at execution time. This
        // prevents a separately granted command permission from weakening the OP-only rule.
        requireNoPermission();
        nameArg = withRequiredArg("character name", "Authored Immersive NPC name",
                ArgTypes.GREEDY_STRING);
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store,
            Ref<EntityStore> playerEntityRef, PlayerRef playerRef, World world) {
        if (!isOperator(playerRef.getUuid())) {
            context.sendMessage(Message.raw("Only a server operator can trace an NPC."));
            return;
        }
        try {
            NpcProfile profile = profiles.requireName(context.get(nameArg));
            traces.toggleAsync(playerRef.getUuid(), profile).whenComplete((result, failure) -> {
                if (failure != null) {
                    playerRef.sendMessage(Message.raw("NPC trace failed: "
                            + (failure.getMessage() == null ? failure.getClass().getSimpleName()
                                    : failure.getMessage())));
                } else {
                    playerRef.sendMessage(Message.raw(result.started()
                            ? "NPC trace started for " + profile.name() + ". File: "
                                    + result.path()
                            : "NPC trace stopped for " + profile.name() + "."));
                }
            });
        } catch (RuntimeException failure) {
            String reason = failure.getMessage() == null || failure.getMessage().isBlank()
                    ? failure.getClass().getSimpleName() : failure.getMessage();
            context.sendMessage(Message.raw("NPC trace failed: " + reason));
        }
    }

    public boolean isOperator(UUID playerId) {
        return playerId != null && operatorCheck.test(playerId);
    }
}
