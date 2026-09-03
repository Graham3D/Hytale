package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.persistentnpcs.orbis.OrbisReadinessRow;
import com.inigmasgames.persistentnpcs.orbis.OrbisReadinessService;
import com.inigmasgames.persistentnpcs.orbis.OrbisReadinessSnapshot;
import com.inigmasgames.persistentnpcs.orbis.OrbisReadinessStatus;
import com.inigmasgames.persistentnpcs.orbis.OrbisReadinessSystem;
import javax.annotation.Nonnull;

/** Native Update 6 revision badge and live cached Orbis readiness panel. */
final class PersistentNpcsHud extends CustomUIHud {
    static final String KEY = "ImmersiveNpcsHud";
    private final OrbisReadinessService readiness;
    private volatile AutoCloseable subscription;

    PersistentNpcsHud(PlayerRef playerRef, OrbisReadinessService readiness) {
        super(playerRef, KEY, 100);
        this.readiness = java.util.Objects.requireNonNull(readiness, "readiness");
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Hud/ImmersiveNPCsRevision.ui");
        commandBuilder.set("#RevisionLabel.Text",
                "IMMERSIVE NPCS  " + PersistentNpcsPlugin.REVISION);
        OrbisReadinessSnapshot initial = readiness.snapshot();
        render(commandBuilder, initial);
        subscription = readiness.subscribe(this::publish);
        OrbisReadinessSnapshot current = readiness.snapshot();
        if (current.revision() != initial.revision()) render(commandBuilder, current);
    }

    private void publish(OrbisReadinessSnapshot snapshot) {
        UICommandBuilder commands = new UICommandBuilder();
        render(commands, snapshot);
        try {
            update(false, commands);
        } catch (RuntimeException ignored) {
            // The HUD can disappear concurrently with an asynchronous provider transition.
        }
    }

    static void render(UICommandBuilder commands, OrbisReadinessSnapshot snapshot) {
        setRow(commands, "Moonshine", snapshot.row(OrbisReadinessSystem.MOONSHINE));
        setRow(commands, "Nemotron", snapshot.row(OrbisReadinessSystem.NEMOTRON));
        setRow(commands, "Chatterbox", snapshot.row(OrbisReadinessSystem.CHATTERBOX));
        setRow(commands, "Orbis", snapshot.row(OrbisReadinessSystem.ORBIS));
    }

    private static void setRow(UICommandBuilder commands, String selector,
            OrbisReadinessRow row) {
        commands.set("#" + selector + "Name.Text", row.displayName());
        for (int index = 1; index <= 10; index++) {
            commands.set("#" + selector + "Pip" + index + ".Visible",
                    index <= row.filledPips());
        }
        commands.set("#" + selector + "Status.Text", row.status().name());
        commands.set("#" + selector + "Status.Style.TextColor", statusColor(row.status()));
    }

    private static String statusColor(OrbisReadinessStatus status) {
        return switch (status) {
            case READY -> "#73E08C";
            case ERROR -> "#FF7272";
            case DEGRADED -> "#FFB85C";
            case STARTING, LOADING, WARMING -> "#F0D47A";
            case DISABLED -> "#9099A4";
            case NOT_STARTED -> "#D7DCE2";
        };
    }

    @Override
    protected void onRemove() {
        AutoCloseable current = subscription;
        subscription = null;
        if (current != null) {
            try { current.close(); } catch (Exception ignored) { }
        }
    }
}
