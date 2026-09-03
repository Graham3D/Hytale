package com.inigmasgames.persistentnpcs.compat;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.CommandRegistration;
import com.hypixel.hytale.server.npc.commands.NPCCommand;
import com.hypixel.hytale.server.npc.commands.NPCSpawnCommand;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * R027 Update 6 compatibility shim. Hytale exposes no post-registration subcommand
 * hook, so this class replaces the root with a fresh parity-checked NPCCommand.
 */
public final class NativeNpcCommandCompatibility implements AutoCloseable {
    private final CommandManager commands;
    private final Consumer<String> diagnostics;
    private final Consumer<String[]> invalidateArgumentCache;
    private AbstractCommand originalRoot;
    private NPCCommand combinedRoot;
    private CommandRegistration combinedRegistration;

    public NativeNpcCommandCompatibility(Consumer<String> diagnostics) {
        this(CommandManager.get(), diagnostics, null);
    }

    NativeNpcCommandCompatibility(CommandManager commands, Consumer<String> diagnostics) {
        this(commands, diagnostics, null);
    }

    /** Visible for a serverless command-tree compatibility test. */
    public NativeNpcCommandCompatibility(
            CommandManager commands,
            Consumer<String> diagnostics,
            Consumer<String[]> invalidateArgumentCache) {
        this.commands = java.util.Objects.requireNonNull(commands, "commands");
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
        this.invalidateArgumentCache = invalidateArgumentCache == null
                ? names -> this.commands.broadcastArgCacheInvalidation(names)
                : invalidateArgumentCache;
    }

    public synchronized void install(AbstractCommand create, AbstractCommand update,
            AbstractCommand trace) {
        if (combinedRoot != null) throw new IllegalStateException(
                "Native /npc compatibility shim is already installed");
        AbstractCommand existing = commands.getCommandRegistration().get("npc");
        if (!(existing instanceof NPCCommand)) {
            throw new IllegalStateException(
                    "Refusing to replace /npc: registered root is not Hytale NPCCommand");
        }
        if (!(existing.getSubCommand("spawn") instanceof NPCSpawnCommand)) {
            throw new IllegalStateException(
                    "Refusing to replace /npc: native spawn command is missing");
        }

        NPCCommand fresh = new NPCCommand();
        List<String> existingTree = nativeTree(existing);
        List<String> freshTree = nativeTree(fresh);
        if (!existingTree.equals(freshTree)) {
            throw new IllegalStateException(
                    "Refusing to replace /npc: fresh native command tree failed parity check");
        }
        if (fresh.getSubCommand("create") != null || fresh.getSubCommand("update") != null
                || fresh.getSubCommand("trace") != null) {
            throw new IllegalStateException(
                    "Refusing to replace /npc: native tree now owns create, update, or trace");
        }
        fresh.addSubCommand(java.util.Objects.requireNonNull(create, "create"));
        fresh.addSubCommand(java.util.Objects.requireNonNull(update, "update"));
        fresh.addSubCommand(java.util.Objects.requireNonNull(trace, "trace"));
        fresh.setOwner(existing.getOwner());

        if (commands.getCommandRegistration().get("npc") != existing) {
            throw new IllegalStateException(
                    "Refusing to replace /npc: command root changed during parity check");
        }
        CommandRegistration registration = commands.register(fresh);
        if (registration == null || commands.getCommandRegistration().get("npc") != fresh) {
            restoreAfterFailedInstall(existing);
            throw new IllegalStateException("Could not install combined native /npc command tree");
        }
        if (!(fresh.getSubCommand("spawn") instanceof NPCSpawnCommand)
                || !nativeTreeWithoutImmersive(fresh).equals(existingTree)) {
            registration.unregister();
            restoreAfterFailedInstall(existing);
            throw new IllegalStateException(
                    "Combined /npc tree failed post-registration native parity check");
        }
        originalRoot = existing;
        combinedRoot = fresh;
        combinedRegistration = registration;
        invalidateArgumentCache.accept(new String[] { "npc" });
        diagnostics.accept("IMMERSIVE_NPC_COMMAND_SHIM_INSTALLED nativeParity=true"
                + " added=[create,update,trace] nativeSpawnPreserved=true");
    }

    public synchronized boolean installed() {
        return combinedRoot != null
                && commands.getCommandRegistration().get("npc") == combinedRoot;
    }

    @Override
    public synchronized void close() {
        if (combinedRoot == null) return;
        if (commands.getCommandRegistration().get("npc") == combinedRoot) {
            combinedRegistration.unregister();
            restoreAfterFailedInstall(originalRoot);
            invalidateArgumentCache.accept(new String[] { "npc" });
            diagnostics.accept("IMMERSIVE_NPC_COMMAND_SHIM_REMOVED nativeRootRestored=true");
        }
        originalRoot = null;
        combinedRoot = null;
        combinedRegistration = null;
    }

    private void restoreAfterFailedInstall(AbstractCommand nativeRoot) {
        if (nativeRoot != null && commands.getCommandRegistration().get("npc") != nativeRoot) {
            CommandRegistration restored = commands.register(nativeRoot);
            if (restored == null || commands.getCommandRegistration().get("npc") != nativeRoot) {
                throw new IllegalStateException("Failed to restore Hytale's native /npc root");
            }
        }
    }

    private static List<String> nativeTreeWithoutImmersive(AbstractCommand root) {
        ArrayList<String> signature = new ArrayList<>();
        appendTree(root, "", signature, true);
        return List.copyOf(signature);
    }

    private static List<String> nativeTree(AbstractCommand root) {
        ArrayList<String> signature = new ArrayList<>();
        appendTree(root, "", signature, false);
        return List.copyOf(signature);
    }

    private static void appendTree(
            AbstractCommand command, String parent, List<String> output,
            boolean omitImmersive) {
        String path = parent.isEmpty() ? command.getName() : parent + "/" + command.getName();
        if (omitImmersive && parent.equals("npc")
                && (command.getName().equals("create") || command.getName().equals("update")
                        || command.getName().equals("trace"))) {
            return;
        }
        output.add(path + "|" + command.getClass().getName() + "|"
                + command.getAliases().stream().sorted().toList());
        command.getSubCommands().values().stream()
                .sorted(Comparator.comparing(AbstractCommand::getName))
                .forEach(child -> appendTree(child, path, output, omitImmersive));
        command.getVariantCommands().stream()
                .sorted(Comparator.comparingInt(value -> value.getRequiredArguments().size()))
                .forEach(variant -> appendTree(variant, path + "#variant", output,
                        omitImmersive));
    }
}
