package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.hytaledevlib.lib.ParticleHelper;
import org.hytaledevlib.lib.StatsHelper;

final class HtDevLibProbeCommand extends PlayerProbeCommand {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String VERIFIED_EFFECT = "Effect_Heal";

    HtDevLibProbeCommand() {
        super("rpgp00-htdevlib", "Exercise HTDevLib stat and particle helpers without persistent mutation.");
    }

    @Override
    protected void executeProbe(CommandContext context, Store<EntityStore> store,
                                Ref<EntityStore> ref, PlayerRef playerRef, Player player, World world) {
        try {
            float health = StatsHelper.getHealth(player);
            float mana = StatsHelper.getMana(player);
            float stamina = StatsHelper.getStamina(player);
            ParticleHelper.spawnParticleAtEntity(world, VERIFIED_EFFECT, player);
            String message = "PHASE00_HTDEVLIB_PASS revision=" + BuildIdentity.REVISION
                    + " effect=" + VERIFIED_EFFECT + " stats=" + health + ',' + mana + ',' + stamina;
            LOGGER.atInfo().log("%s", message);
            context.sendMessage(Message.raw(message));
        } catch (Throwable error) {
            LOGGER.atSevere().withCause(error).log("PHASE00_HTDEVLIB_FAIL revision=%s",
                    BuildIdentity.REVISION);
            context.sendMessage(Message.raw("PHASE00_HTDEVLIB_FAIL " + error.getClass().getSimpleName()
                    + ": " + error.getMessage()));
        }
    }
}
