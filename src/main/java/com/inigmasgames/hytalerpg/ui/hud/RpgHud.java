package com.inigmasgames.hytalerpg.ui.hud;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.hytalerpg.ui.model.RpgHudViewModel;

import javax.annotation.Nonnull;

final class RpgHud extends CustomUIHud {
    static final String KEY = "inigmas:hytalerpg:hud";
    static final int XP_FILL_WIDTH = 696;
    private RpgHudViewModel model;

    RpgHud(PlayerRef playerRef, RpgHudViewModel model) {
        super(playerRef, KEY, 900);
        this.model = model;
    }

    @Override protected void build(@Nonnull UICommandBuilder commands) {
        commands.append("RpgHud.ui");
        commands.append("RpgCooldownSweep.ui");
        commands.append("Phase00RevisionHud.ui");
        commands.set("#BuildRevision.TextSpans", Message.raw(com.inigmasgames.hytalerpg.phase00.BuildIdentity.REVISION + "-AG"));
        writeAll(commands, model);
    }

    void refresh(RpgHudViewModel next) {
        RpgHudViewModel previous = model;
        model = next;
        UICommandBuilder update = new UICommandBuilder();
        if (!previous.xp().equals(next.xp())) writeXp(update, next);
        if (previous.pendingLevelUpPoints() != next.pendingLevelUpPoints()) writeNotice(update, next);
        if(!previous.skills().equals(next.skills()))writeCooldowns(update,next);
        if (update.getCommands().length > 0) update(false, update);
    }

    private static void writeAll(UICommandBuilder commands, RpgHudViewModel model) {
        writeXp(commands, model);
        writeNotice(commands, model);
        writeCooldowns(commands,model);
    }
    private static void writeCooldowns(UICommandBuilder commands,RpgHudViewModel model){
        for(int i=0;i<2;i++){
            var slot=model.skills().get(i);String selector="#RpgCooldownSkill0"+(i+1);
            commands.set(selector+".Visible",slot.cooldownRemainingSeconds()>0&&!slot.skillId().isEmpty());
            commands.set(selector+".Value",CooldownSweep.progress(slot.cooldownRemainingSeconds(),slot.cooldownDurationSeconds()));
            String suffix="0"+(i+1);
            String countdown=CooldownSweep.countdown(slot.cooldownRemainingSeconds());
            commands.set("#RpgCountdown"+suffix+".Text",countdown);
            commands.set("#RpgCountdownShadow"+suffix+".Text",countdown);
            commands.set("#RpgCountdown"+suffix+".Visible",!countdown.isEmpty()&&!slot.skillId().isEmpty());
            commands.set("#RpgCountdownShadow"+suffix+".Visible",!countdown.isEmpty()&&!slot.skillId().isEmpty());
            String warning=slot.unavailableReason().startsWith("LOW_")?slot.unavailableReason().replace('_',' '):"";
            commands.set("#RpgResourceNotice"+suffix+".Text",warning);
            commands.set("#RpgResourceNotice"+suffix+".Visible",!warning.isEmpty());
        }
    }

    private static void writeXp(UICommandBuilder commands, RpgHudViewModel model) {
        commands.setObject("#ExperienceFill.Anchor", leftFill(xpFillWidth(model.xp().progress()), 3, 3, 22));
    }

    private static void writeNotice(UICommandBuilder commands, RpgHudViewModel model) {
        commands.set("#LevelUpNotice.Visible", model.showLevelUpNotice());
        commands.set("#LevelUpNotice.TextSpans", Message.raw("LEVEL UP - " + model.pendingLevelUpPoints()
                + " ATTRIBUTE POINT" + (model.pendingLevelUpPoints() == 1 ? "" : "S")));
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

}
