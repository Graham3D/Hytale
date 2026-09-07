package com.inigmasgames.hytalerpg.ui.hud;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.hytalerpg.ui.model.NativeResourceView;
import com.inigmasgames.hytalerpg.ui.model.RpgHudViewModel;
import com.inigmasgames.hytalerpg.ui.model.SkillSlotView;

import javax.annotation.Nonnull;
import java.util.Locale;

final class RpgHud extends CustomUIHud {
    static final String KEY = "inigmas:hytalerpg:hud";
    static final int RESOURCE_FILL_WIDTH = 210;
    static final int XP_FILL_WIDTH = 925;
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
        if (!previous.health().equals(next.health()) || !previous.mana().equals(next.mana())
                || !previous.stamina().equals(next.stamina())) writeResources(update, next);
        if (!previous.xp().equals(next.xp())) writeXp(update, next);
        if (previous.pendingLevelUpPoints() != next.pendingLevelUpPoints()) writeNotice(update, next);
        if (!previous.skills().equals(next.skills())) writeSkills(update, next);
        if (update.getCommands().length > 0) update(false, update);
    }

    private static void writeAll(UICommandBuilder commands, RpgHudViewModel model) {
        writeResources(commands, model);
        writeXp(commands, model);
        writeNotice(commands, model);
        writeSkills(commands, model);
    }

    private static void writeResources(UICommandBuilder commands, RpgHudViewModel model) {
        writeResource(commands, "Health", model.health());
        writeResource(commands, "Mana", model.mana());
        writeResource(commands, "Stamina", model.stamina());
    }

    private static void writeResource(UICommandBuilder commands, String name, NativeResourceView resource) {
        Anchor fill = leftFill(resourceFillWidth(resource), 0, 0, 12);
        commands.setObject("#" + name + "Fill.Anchor", fill);
        commands.set("#" + name + "Value.TextSpans", Message.raw(format(resource.current(), resource.maximum())));
    }

    private static void writeXp(UICommandBuilder commands, RpgHudViewModel model) {
        commands.set("#XpLabel.TextSpans", Message.raw("LV " + model.xp().level() + "  "
                + Math.round(model.xp().progress() * 100.0) + "%"));
        commands.setObject("#ExperienceFill.Anchor", leftFill(xpFillWidth(model.xp().progress()), 3, 3, 22));
    }

    private static void writeNotice(UICommandBuilder commands, RpgHudViewModel model) {
        commands.set("#LevelUpNotice.Visible", model.showLevelUpNotice());
        commands.set("#LevelUpNotice.TextSpans", Message.raw("LEVEL UP - " + model.pendingLevelUpPoints()
                + " ATTRIBUTE POINT" + (model.pendingLevelUpPoints() == 1 ? "" : "S")));
    }

    private static void writeSkills(UICommandBuilder commands, RpgHudViewModel model) {
        for (int index = 0; index < model.skills().size(); index++) {
            SkillSlotView slot = model.skills().get(index);
            int number = index + 1;
            boolean occupied = !slot.skillId().isBlank();
            boolean cooldown = slot.state() == SkillSlotView.State.COOLDOWN;
            boolean unavailable = slot.state() == SkillSlotView.State.UNAVAILABLE;
            boolean ready = slot.state() == SkillSlotView.State.READY;
            String state = switch (slot.state()) {
                case EMPTY -> "EMPTY";
                case READY -> "READY";
                case COOLDOWN -> String.format(Locale.ROOT, "%.1fs", slot.cooldownRemainingSeconds());
                case UNAVAILABLE -> "UNAVAILABLE";
            };
            commands.set("#Skill" + number + "Action.TextSpans", Message.raw(slot.action()));
            commands.set("#Skill" + number + "Icon.Visible", occupied);
            commands.set("#Skill" + number + "Cooldown.Visible", cooldown);
            commands.set("#Skill" + number + "Unavailable.Visible", unavailable);
            commands.set("#Skill" + number + "ReadyFrame.Visible", ready);
            commands.set("#Skill" + number + "NotReadyFrame.Visible", !ready);
            commands.set("#Skill" + number + "Name.TextSpans", Message.raw(occupied ? slot.name() : "Empty"));
            commands.set("#Skill" + number + "State.TextSpans", Message.raw(state));
        }
    }

    static int resourceFillWidth(NativeResourceView resource) {
        if (resource.maximum() <= 0.0) return 0;
        return proportionalWidth(resource.current() / resource.maximum(), RESOURCE_FILL_WIDTH);
    }

    static int xpFillWidth(double progress) { return proportionalWidth(progress, XP_FILL_WIDTH); }

    private static int proportionalWidth(double fraction, int fullWidth) {
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        return (int) Math.round(fullWidth * clamped);
    }

    private static Anchor leftFill(int width, int left, int top, int height) {
        Anchor fill = new Anchor();
        fill.setLeft(Value.of(left)); fill.setTop(Value.of(top));
        fill.setWidth(Value.of(width)); fill.setHeight(Value.of(height));
        return fill;
    }

    private static String format(double current, double maximum) {
        return String.format(Locale.ROOT, "%.1f / %.1f", current, maximum);
    }
}
