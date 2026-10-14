package com.inigmasgames.hytalerpg.ui.hud;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.hytalerpg.ui.model.RpgHudViewModel;

import javax.annotation.Nonnull;

final class RpgHud extends CustomUIHud {
    static final String KEY = "inigmas:hytalerpg:hud";
    static final int XP_FILL_WIDTH = 696;
    private RpgHudViewModel model;
    private boolean emptyHand;
    private SentinelAffixPresentation.Lines sentinelAffixes;
    private SkillFailureNotice.View skillFailure = new SkillFailureNotice.View("", -1);
    private boolean weaponHint;
    private com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto enemyTarget;
    private int enemyHealthWidth;
    private EnemyBillboardProjection.Frame enemyFrame;
    private boolean enemyUsesNativeHealthbar;
    private final java.util.UUID viewerId;

    RpgHud(PlayerRef playerRef, RpgHudViewModel model) {
        super(playerRef, KEY, 900);
        this.model = model;
        this.viewerId=playerRef.getUuid();
    }

    @Override protected void build(@Nonnull UICommandBuilder commands) {
        commands.append("RpgHud.ui");
        commands.append("RpgCooldownSweep.ui");
        commands.append("RpgEnemyTarget.ui");
        commands.append("Phase00RevisionHud.ui");
        commands.set("#BuildRevision.TextSpans", Message.raw(com.inigmasgames.hytalerpg.phase00.BuildIdentity.DISPLAY_REVISION));
        writeAll(commands, model, emptyHand);
        writeSentinelAffixes(commands,sentinelAffixes);
        writeSkillFailure(commands);
        writeEnemyTarget(commands,enemyTarget,enemyFrame,enemyHealthWidth,enemyUsesNativeHealthbar);
    }
    void refreshEnemyTarget(com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto next,
            EnemyBillboardProjection.Frame frame,double currentHealth,double maxHealth){
        refreshEnemyTarget(next,frame,currentHealth,maxHealth,false);
    }
    void refreshEnemyTarget(com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto next,
            EnemyBillboardProjection.Frame frame,double currentHealth,double maxHealth,boolean nativeHealthbar){
        if(frame==null)next=null;
        if(next==null)nativeHealthbar=false;
        int width=next==null?0:(int)Math.round(frame.frameWidth()*enemyHealthFraction(currentHealth,maxHealth));
        if(java.util.Objects.equals(enemyTarget,next)&&java.util.Objects.equals(enemyFrame,frame)&&enemyHealthWidth==width
                &&enemyUsesNativeHealthbar==nativeHealthbar)return;
        enemyTarget=next;enemyFrame=frame;enemyHealthWidth=width;enemyUsesNativeHealthbar=nativeHealthbar;
        var commands=new UICommandBuilder();writeEnemyTarget(commands,next,frame,width,nativeHealthbar);update(false,commands);
        if(next!=null&&com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.active(viewerId)){
            var layout=EnemyTargetLayout.of(next);
            com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.observe(viewerId,"HUD.PUBLICATION","RpgHud","ME12_HUD",next.logicalActorId().toString(),next.worldId(),null,null,next.stateRevision(),
                    java.util.Map.of("affixCount",next.ownAffixTags().size(),"rowCount",layout.lines().size(),"generation",next.generation(),"result","SERVER_UI_PUBLISHED","proof","NO_NATIVE_RENDER_ACK"));
        }
    }
    private static void writeEnemyTarget(UICommandBuilder commands,com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto target,
            EnemyBillboardProjection.Frame frame,int healthWidth,boolean nativeHealthbar){
        commands.set("#RpgEnemyTarget.Visible",target!=null&&frame!=null);
        if(target==null||frame==null)return;
        var layout=EnemyTargetLayout.of(target);
        var root=new Anchor();root.setRight(Value.of(16));root.setTop(Value.of(16));
        root.setWidth(Value.of(EnemyTargetLayout.WIDTH));root.setHeight(Value.of(layout.height()));
        commands.setObject("#RpgEnemyTarget.Anchor",root);
        commands.set("#RpgEnemyHealth.Visible",true);
        commands.set("#RpgEnemyIdentity.Visible",true);
        commands.set("#RpgEnemyAffixes.Visible",true);
        int inside=EnemyTargetLayout.WIDTH-20;
        commands.setObject("#RpgEnemyHealth.Anchor",topLine(44,inside,12));
        commands.setObject("#RpgEnemyHealthBackground.Anchor",leftFill(inside,0,0,12));
        commands.setObject("#RpgEnemyHealthFill.Anchor",leftFill((int)Math.round(inside*(healthWidth/(double)frame.frameWidth())),0,0,12));
        commands.setObject("#RpgEnemyHealthFrame.Anchor",leftFill(inside,0,0,12));
        commands.setObject("#RpgEnemyName.Anchor",topLine(0,EnemyTargetLayout.WIDTH,22));
        commands.setObject("#RpgEnemyIdentity.Anchor",topLine(22,EnemyTargetLayout.WIDTH,20));
        commands.setObject("#RpgEnemyAffixes.Anchor",topLine(64,EnemyTargetLayout.WIDTH,EnemyTargetLayout.LINE*layout.lines().size()+8));
        commands.set("#RpgEnemyName.Style.FontSize",14);
        commands.set("#RpgEnemyIdentity.Style.FontSize",EnemyTargetLayout.FONT);
        commands.set("#RpgEnemyAffixes.Style.FontSize",EnemyTargetLayout.FONT);
        commands.set("#RpgEnemyName.Text",layout.name());
        commands.set("#RpgEnemyIdentity.Text",layout.identity());
        commands.set("#RpgEnemyAffixes.Text",String.join("\n",layout.lines()));
        String rarityColor=switch(target.rarityLabel()){
            case "Champion"->"#1d4dff";case "Unique"->"#ffd24a";case "Super Unique"->"#ff9100";case "Boss"->"#ed3030";default->"#f2eee5";};
        commands.set("#RpgEnemyName.Style.TextColor",rarityColor);
    }
    static int enemyHealthFillWidth(double current,double maximum){
        return proportionalWidth(enemyHealthFraction(current,maximum),360);
    }
    private static double enemyHealthFraction(double current,double maximum){
        if(!Double.isFinite(current)||!Double.isFinite(maximum)||maximum<=0)return 0;
        return Math.max(0,Math.min(1,current/maximum));
    }
    private static Anchor topLine(int top,int width,int height){
        var anchor=new Anchor();anchor.setTop(Value.of(top));anchor.setWidth(Value.of(width));anchor.setHeight(Value.of(height));return anchor;
    }
    void refreshSkillFailure(SkillFailureNotice.View notice, boolean hint) {
        if (skillFailure.equals(notice) && weaponHint == hint) return;
        skillFailure = notice; weaponHint = hint;
        var commands = new UICommandBuilder(); writeSkillFailure(commands); update(false, commands);
    }
    private void writeSkillFailure(UICommandBuilder commands) {
        commands.set("#SkillWeaponHint.Visible", weaponHint);
        commands.set("#SkillFailureNotice.Visible", skillFailure.phase() >= 0);
        String[] phases = {"Low", "Medium", "Full"};
        for (int i = 0; i < phases.length; i++) {
            commands.set("#SkillFailure" + phases[i] + ".Visible", skillFailure.phase() == i);
            commands.set("#SkillFailure" + phases[i] + ".Text", skillFailure.text());
        }
    }
    void refreshSentinelAffixes(SentinelAffixPresentation.Lines next){
        if(java.util.Objects.equals(sentinelAffixes,next))return;
        sentinelAffixes=next;
        var update=new UICommandBuilder();writeSentinelAffixes(update,next);update(false,update);
    }
    private static void writeSentinelAffixes(UICommandBuilder commands,SentinelAffixPresentation.Lines lines){
        commands.set("#SentinelAffixDebug.Visible",lines!=null);
        commands.set("#SentinelAffixTitle.Text",lines==null?"":lines.title());
        for(int i=0;i<8;i++){
            String row=lines!=null&&i<lines.rows().size()?lines.rows().get(i):"";
            commands.set("#SentinelAffixRow"+i+".Text",row);
            commands.set("#SentinelAffixRow"+i+".Visible",!row.isEmpty());
        }
    }

    void refresh(RpgHudViewModel next, boolean emptyHand) {
        RpgHudViewModel previous = model;
        boolean handChanged = this.emptyHand != emptyHand;
        model = next;
        this.emptyHand = emptyHand;
        UICommandBuilder update = new UICommandBuilder();
        if (!previous.xp().equals(next.xp())) writeXp(update, next);
        if (!previous.health().equals(next.health()) || !previous.mana().equals(next.mana())
                || !previous.barrier().equals(next.barrier())) writeResources(update,next);
        if (previous.pendingLevelUpPoints() != next.pendingLevelUpPoints()) writeNotice(update, next);
        if(!previous.skills().equals(next.skills()))writeCooldowns(update,next);
        if(handChanged||!previous.skills().equals(next.skills()))writeEmptyHandSkills(update,next,emptyHand);
        if (update.getCommands().length > 0) update(false, update);
    }

    private static void writeAll(UICommandBuilder commands, RpgHudViewModel model, boolean emptyHand) {
        writeXp(commands, model);
        writeResources(commands,model);
        writeNotice(commands, model);
        writeCooldowns(commands,model);
        writeEmptyHandSkills(commands,model,emptyHand);
    }
    private static void writeEmptyHandSkills(UICommandBuilder commands,RpgHudViewModel model,boolean emptyHand){
        for(int i=0;i<model.skills().size();i++){
            var skill=model.skills().get(i);String suffix="0"+(i+1);
            boolean projectionUnavailable=skill.unavailableReason().startsWith("NATIVE_")
                    ||"NO_ACTIVE_WORLD_SESSION".equals(skill.unavailableReason())
                    ||"EXECUTOR_NOT_IMPLEMENTED".equals(skill.unavailableReason());
            boolean visible=i>=2||emptyHand||projectionUnavailable;
            commands.set("#RpgEmptyHandSkill"+suffix+".Visible",visible);
            commands.set("#RpgEmptyHandState"+suffix+".Visible",visible);
            if(visible){
                boolean assigned=!skill.skillId().isEmpty();
                String icon=assigned?skillIcon(skill.skillId()):null;
                commands.set("#RpgEmptyHandIcon"+suffix+".Visible",icon!=null);
                commands.set("#RpgEmptyHandName"+suffix+".Visible",icon==null);
                if(icon!=null)commands.setObject("#RpgEmptyHandIcon"+suffix+".Background",
                        new PatchStyle().setTexturePath(Value.of(icon)));
                else commands.set("#RpgEmptyHandName"+suffix+".Text",assigned
                        ? skill.name().substring(0,Math.min(9,skill.name().length())) : "EMPTY");
                commands.set("#RpgEmptyHandKey"+suffix+".Text",i<2?"A"+(i+2):"—");
                commands.set("#RpgEmptyHandState"+suffix+".Text",!assigned?(i>=2?"S"+(i+1)+" EMPTY":"UNASSIGNED")
                        :i>=2?"S"+(i+1)+" UNBOUND"
                        :projectionUnavailable?"UNAVAILABLE"
                        :skill.cooldownRemainingSeconds()>0?CooldownSweep.countdown(skill.cooldownRemainingSeconds())
                        :skill.unavailableReason().startsWith("LOW_")?skill.unavailableReason().replace('_',' ')
                        :"HOLD WEAPON");
            }
        }
    }
    private static String skillIcon(String id){
        if(id.equals("iron_sentinel"))return "Icons/RPG/SkillSummonskeletonarchers.png";
        String name="Skill"+Character.toUpperCase(id.charAt(0))+id.substring(1).replace("_","");
        String path="Icons/RPG/"+name+".png";
        return RpgHud.class.getClassLoader().getResource("Common/UI/Custom/"+path)==null?null:path;
    }
    private static void writeCooldowns(UICommandBuilder commands,RpgHudViewModel model){
        for(int i=0;i<model.skills().size();i++){
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

    static void writeResources(UICommandBuilder commands,RpgHudViewModel model){
        commands.set("#RpgHealthValue.Text","Health "+resourceText(model.health()));
        commands.set("#RpgManaValue.Text","Mana "+resourceText(model.mana()));
        var barrier=model.barrier();
        commands.set("#RpgBarrierMeter.Visible",barrier.active());
        if(barrier.active()){
            commands.setObject("#RpgBarrierFill.Anchor",leftFill(barrierFillWidth(barrier),3,3,16));
            commands.set("#RpgBarrierValue.Text","Barrier "+number(barrier.current())+" / "+number(barrier.maximum()));
        }
    }
    static int barrierFillWidth(com.inigmasgames.hytalerpg.ui.model.BarrierView barrier){
        return barrier.active()?proportionalWidth(barrier.current()/barrier.maximum(),404):0;
    }
    static String resourceText(com.inigmasgames.hytalerpg.ui.model.NativeResourceView value){
        return number(value.current())+" / "+number(value.maximum());
    }
    private static String number(double value){
        return String.format(java.util.Locale.ROOT,"%.1f",value);
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
