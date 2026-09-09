package com.inigmasgames.hytalerpg.ui.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.hytalerpg.ui.model.RpgHudViewModel;

/** Three noninteractive template pips; no native resource/ability/XP control is touched. */
final class FinisherHud extends CustomUIHud {
    static final String KEY="inigmas:hytalerpg:finisher";
    private boolean visible;
    private int count;
    FinisherHud(PlayerRef player,RpgHudViewModel model,int count){super(player,KEY,901);this.visible=equipped(model);this.count=count;}
    private static boolean equipped(RpgHudViewModel model){return model.skills().stream().anyMatch(s->"finishing_strike".equals(s.skillId()));}
    @Override protected void build(UICommandBuilder commands){commands.append("RpgFinisherPips.ui");write(commands,visible,count);}
    void refresh(RpgHudViewModel model,int count){
        boolean next=equipped(model);if(visible==next&&this.count==count)return;
        var commands=new UICommandBuilder();write(commands,next,count);update(false,commands);visible=next;this.count=count;
    }
    private static void write(UICommandBuilder commands,boolean visible,int count){
        if(count<0||count>3)throw new IllegalArgumentException("Invalid finisher pip count");
        commands.set("#FinisherPips.Visible",visible);
        for(int i=1;i<=3;i++)commands.set("#FinisherPip"+i+".Visible",i<=count);
    }
}
