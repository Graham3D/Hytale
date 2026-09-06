package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.content.CatalogResolution;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveDefinition;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillDefinition;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.progress.MutationResult;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutOperations;

/** Temporary command editing frontend. Authority remains in RpgLoadoutOperations. */
public final class RpgCommand extends AbstractCommandCollection {
    public RpgCommand(RpgCatalog catalog, RpgLoadoutOperations loadouts) {
        super("rpg", "Configure and inspect the server-authoritative RPG Link Tree.");
        addSubCommand(new EquipCommand(catalog, loadouts));
        addSubCommand(new UnequipCommand(loadouts));
        addSubCommand(new LinkCommand(loadouts));
        addSubCommand(new UnlinkCommand(loadouts));
        addSubCommand(new LoadoutCommand(catalog, loadouts));
        addSubCommand(new CompileCommand(loadouts));
    }

    private abstract static class PlayerSubcommand extends AbstractPlayerCommand {
        final RpgLoadoutOperations loadouts;
        PlayerSubcommand(String name, String description, RpgLoadoutOperations loadouts) {
            super(name, description); this.loadouts = loadouts; setPermissionGroup(GameMode.Adventure);
        }
        void send(CommandContext context, MutationResult result) { context.sendMessage(Message.raw(result.message())); }
        void error(CommandContext context, RuntimeException error) { context.sendMessage(Message.raw(error.getMessage())); }
    }

    private static final class EquipCommand extends PlayerSubcommand {
        private final RpgCatalog catalog;
        private final RequiredArg<String> slot;
        private final RequiredArg<String> definition;
        EquipCommand(RpgCatalog catalog, RpgLoadoutOperations loadouts) {
            super("equip", "Equip a Skill or Passive definition into a permanent slot.", loadouts);
            this.catalog = catalog;
            slot = withRequiredArg("slot", "skill01..skill04 or passive01..passive06", ArgTypes.STRING);
            definition = withRequiredArg("definition", "canonical name or ID", ArgTypes.GREEDY_STRING);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try {
                String slotValue = context.get(slot);
                String query = clean(context.get(definition));
                if (slotValue.toLowerCase(java.util.Locale.ROOT).startsWith("skill")) {
                    CatalogResolution<SkillDefinition> resolved = catalog.resolveSkill(query);
                    if (resolved.status() != CatalogResolution.Status.RESOLVED) {
                        context.sendMessage(Message.raw(resolved.message())); return;
                    }
                    send(context, loadouts.equipSkill(playerRef.getUuid(), SkillSlot.parse(slotValue), resolved.value().id()));
                } else if (slotValue.toLowerCase(java.util.Locale.ROOT).startsWith("passive")) {
                    CatalogResolution<PassiveDefinition> resolved = catalog.resolvePassive(query);
                    if (resolved.status() != CatalogResolution.Status.RESOLVED) {
                        context.sendMessage(Message.raw(resolved.message())); return;
                    }
                    send(context, loadouts.equipPassive(playerRef.getUuid(), PassiveSlot.parse(slotValue), resolved.value().id()));
                } else throw new IllegalArgumentException("Equip slot must be skill01..skill04 or passive01..passive06.");
            } catch (RuntimeException error) { error(context, error); }
        }
    }

    private static final class UnequipCommand extends PlayerSubcommand {
        private final RequiredArg<String> slot;
        UnequipCommand(RpgLoadoutOperations loadouts) {
            super("unequip", "Unequip a Skill or Passive and safely remove affected links.", loadouts);
            slot = withRequiredArg("slot", "skill01..skill04 or passive01..passive06", ArgTypes.STRING);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try {
                String value = context.get(slot);
                if (value.toLowerCase(java.util.Locale.ROOT).startsWith("skill"))
                    send(context, loadouts.unequipSkill(playerRef.getUuid(), SkillSlot.parse(value)));
                else send(context, loadouts.unequipPassive(playerRef.getUuid(), PassiveSlot.parse(value)));
            } catch (RuntimeException error) { error(context, error); }
        }
    }

    private static final class LinkCommand extends PlayerSubcommand {
        private final RequiredArg<String> source;
        private final RequiredArg<String> target;
        LinkCommand(RpgLoadoutOperations loadouts) {
            super("link", "Create or replace one validated Link route edge.", loadouts);
            source = withRequiredArg("source", "passive or joint node", ArgTypes.STRING);
            target = withRequiredArg("target", "skill or joint node", ArgTypes.STRING);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try { send(context, loadouts.link(playerRef.getUuid(), LinkNodeId.parse(context.get(source)), LinkNodeId.parse(context.get(target)))); }
            catch (RuntimeException error) { error(context, error); }
        }
    }

    private static final class UnlinkCommand extends PlayerSubcommand {
        private final RequiredArg<String> source;
        UnlinkCommand(RpgLoadoutOperations loadouts) {
            super("unlink", "Remove the outgoing edge from a Passive or Joint node.", loadouts);
            source = withRequiredArg("source", "passive or joint node", ArgTypes.STRING);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try { send(context, loadouts.unlinkSource(playerRef.getUuid(), LinkNodeId.parse(context.get(source)))); }
            catch (RuntimeException error) { error(context, error); }
        }
    }

    private static final class LoadoutCommand extends PlayerSubcommand {
        private final RpgCatalog catalog;
        LoadoutCommand(RpgCatalog catalog, RpgLoadoutOperations loadouts) {
            super("loadout", "Print the authoritative equipped graph and compiled plan summary.", loadouts); this.catalog = catalog;
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            context.sendMessage(Message.raw(loadouts.getLoadout(playerRef.getUuid()).format(catalog)));
        }
    }

    private static final class CompileCommand extends PlayerSubcommand {
        CompileCommand(RpgLoadoutOperations loadouts) { super("compile", "Compile the current Link Tree without mutating it.", loadouts); }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            var result = loadouts.compile(playerRef.getUuid());
            context.sendMessage(Message.raw(result.success() ? "Compile: PASS. Plans=" + result.plans().size()
                    : "Compile: FAIL " + result.code() + ": " + result.message()));
        }
    }

    private static String clean(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() >= 2 && ((clean.startsWith("\"") && clean.endsWith("\""))
                || (clean.startsWith("'") && clean.endsWith("'")))) return clean.substring(1, clean.length() - 1);
        return clean;
    }
}
