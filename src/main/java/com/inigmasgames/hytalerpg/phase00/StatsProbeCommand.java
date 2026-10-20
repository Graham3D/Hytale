package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.hytaledevlib.lib.StatsHelper;

final class StatsProbeCommand extends PlayerProbeCommand {
    private static final String[] NATIVE_STAT_IDS = {"Health", "Mana", "Stamina"};

    StatsProbeCommand() {
        super("rpgp00-stats", "Read native Health/Mana/Stamina without mutating them.");
    }

    @Override
    protected void executeProbe(CommandContext context, Store<EntityStore> store,
                                Ref<EntityStore> ref, PlayerRef playerRef, Player player, World world) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) {
            context.sendMessage(Message.raw("Phase 00: EntityStatMap unavailable."));
            return;
        }
        StringBuilder result = new StringBuilder("Phase 00 native stats:");
        for (String id : NATIVE_STAT_IDS) {
            EntityStatValue value = stats.get(id);
            if (value == null) {
                result.append(' ').append(id).append("=missing;");
            } else {
                result.append(' ').append(id).append('=').append(value.get())
                        .append('/').append(value.getMax()).append(';');
            }
        }
        context.sendMessage(Message.raw(result.toString()));
        try {
            context.sendMessage(Message.raw("HTDevLib read comparison: Health=" + StatsHelper.getHealth(player)
                    + "; Mana=" + StatsHelper.getMana(player)
                    + "; Stamina=" + StatsHelper.getStamina(player) + ";"));
        } catch (Throwable error) {
            context.sendMessage(Message.raw("HTDevLib read comparison failed: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage()));
        }
    }
}
