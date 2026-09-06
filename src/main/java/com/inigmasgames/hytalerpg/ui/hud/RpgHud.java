package com.inigmasgames.hytalerpg.ui.hud;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.hytalerpg.phase00.BuildIdentity;
import com.inigmasgames.hytalerpg.ui.model.RpgHudViewModel;
import com.inigmasgames.hytalerpg.ui.model.SkillSlotView;

import javax.annotation.Nonnull;
import java.util.Locale;

final class RpgHud extends CustomUIHud {
    static final String KEY = "inigmas:hytalerpg:hud";
    private RpgHudViewModel model;

    RpgHud(PlayerRef playerRef, RpgHudViewModel model) {
        super(playerRef, KEY, 900);
        this.model = model;
    }

    @Override protected void build(@Nonnull UICommandBuilder commands) {
        commands.append("RpgHud.ui");
        writeAll(commands, model);
    }

    void refresh(RpgHudViewModel next) {
        RpgHudViewModel previous = model;
        model = next;
        UICommandBuilder update = new UICommandBuilder();
        if (!previous.mana().equals(next.mana()) || !previous.health().equals(next.health())
                || !previous.stamina().equals(next.stamina())) writeResources(update, next);
        if (!previous.xp().equals(next.xp())) writeXp(update, next);
        if (previous.pendingLevelUpPoints() != next.pendingLevelUpPoints()) writeNotice(update, next);
        if (!previous.skills().equals(next.skills())) writeSkills(update, next);
        if (update.getCommands().length > 0) update(false, update);
    }

    private static void writeAll(UICommandBuilder commands, RpgHudViewModel model) {
        commands.set("#RpgRevision.TextSpans", Message.raw(BuildIdentity.REVISION));
        writeResources(commands, model);
        writeXp(commands, model);
        writeNotice(commands, model);
        writeSkills(commands, model);
    }

    private static void writeResources(UICommandBuilder commands, RpgHudViewModel model) {
        commands.set("#ManaValue.TextSpans", Message.raw(format(model.mana().current(), model.mana().maximum())));
        commands.set("#HealthValue.TextSpans", Message.raw(format(model.health().current(), model.health().maximum())));
        commands.set("#StaminaValue.TextSpans", Message.raw(format(model.stamina().current(), model.stamina().maximum())));
    }

    private static void writeXp(UICommandBuilder commands, RpgHudViewModel model) {
        StringBuilder pips = new StringBuilder();
        for (double fill : model.xp().pipFill()) pips.append(fill >= 0.999 ? '|' : fill <= 0.001 ? '.' : ':');
        commands.set("#XpValue.TextSpans", Message.raw("LV " + model.xp().level() + "  [" + pips + "]  "
                + Math.round(model.xp().progress() * 100.0) + "%"));
    }

    private static void writeNotice(UICommandBuilder commands, RpgHudViewModel model) {
        commands.set("#LevelUpNotice.Visible", model.showLevelUpNotice());
        commands.set("#LevelUpNotice.TextSpans", Message.raw("LEVEL UP - " + model.pendingLevelUpPoints()
                + " ATTRIBUTE POINT" + (model.pendingLevelUpPoints() == 1 ? "" : "S")));
    }

    private static void writeSkills(UICommandBuilder commands, RpgHudViewModel model) {
        for (int index = 0; index < model.skills().size(); index++) {
            SkillSlotView slot = model.skills().get(index);
            String state = switch (slot.state()) {
                case EMPTY -> "EMPTY";
                case READY -> "READY";
                case COOLDOWN -> String.format(Locale.ROOT, "%.1fs", slot.cooldownRemainingSeconds());
                case UNAVAILABLE -> "UNAVAILABLE";
            };
            String familyIcon = slot.iconKey().substring(slot.iconKey().lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
            familyIcon = familyIcon.length() <= 3 ? familyIcon : familyIcon.substring(0, 3);
            commands.set("#Skill" + (index + 1) + "Name.TextSpans", Message.raw("[" + familyIcon + "] " + slot.name()));
            commands.set("#Skill" + (index + 1) + "State.TextSpans", Message.raw(slot.action() + " / " + state));
        }
    }

    private static String format(double current, double maximum) {
        return String.format(Locale.ROOT, "%.1f / %.1f", current, maximum);
    }
}
