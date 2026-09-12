package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.ui.*;
import com.inigmasgames.hytalerpg.ui.model.*;
import com.inigmasgames.hytalerpg.ui.hud.CooldownSweep;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13PresentationAETest {
    @Test void blizzardHudSeparatesActiveCooldownFromManaFailureWithoutWritingGameplay(){
        var bundle=Stage01BTestSupport.bundle();var kernel=RpgCombatKernel.createProduction();var actor=UUID.randomUUID();
        assertTrue(bundle.service().equipSkill(actor,SkillSlot.SKILL02,new SkillId("blizzard")).success());
        var projection=new RpgUiProjectionService(bundle.catalog(),bundle.service(),kernel.derivedStats(),kernel.cooldowns());
        projection.configureResourceReadiness(kernel.resources(),(id,slot)->0);
        double[] remaining={3};projection.configureActiveRemaining((id,skill)->remaining[0]);
        int saves=bundle.repository().saves;
        var emptyMana=new HytaleResourceViewAdapter.Snapshot(new NativeResourceView(0,100),new NativeResourceView(100,100),new NativeResourceView(100,100));
        var active=projection.hud(actor,emptyMana,null).skills().get(1);
        assertEquals(SkillSlotView.State.COOLDOWN,active.state());assertEquals("LOW_MANA",active.unavailableReason());
        assertEquals(3,active.cooldownDurationSeconds());assertEquals("3",CooldownSweep.countdown(active.cooldownRemainingSeconds()));
        remaining[0]=0;
        var starved=projection.hud(actor,emptyMana,null).skills().get(1);
        assertEquals(SkillSlotView.State.INSUFFICIENT_RESOURCE,starved.state());assertEquals("LOW_MANA",starved.unavailableReason());
        assertEquals("",CooldownSweep.countdown(starved.cooldownRemainingSeconds()));
        var restored=new HytaleResourceViewAdapter.Snapshot(new NativeResourceView(100,100),emptyMana.health(),emptyMana.stamina());
        var ready=projection.hud(actor,restored,null).skills().get(1);assertEquals(SkillSlotView.State.READY,ready.state());assertEquals("",ready.unavailableReason());
        assertEquals(0,kernel.cooldowns().remaining(actor,"blizzard"));assertEquals(saves,bundle.repository().saves);
    }
    @Test void lowStaminaHasDistinctFeedbackAndEmptySlotsDoNotReportResourceFailure(){
        var bundle=Stage01BTestSupport.bundle();var kernel=RpgCombatKernel.createProduction();var actor=UUID.randomUUID();
        bundle.service().equipSkill(actor,SkillSlot.SKILL01,new SkillId("quick_slash"));
        var projection=new RpgUiProjectionService(bundle.catalog(),bundle.service(),kernel.derivedStats(),kernel.cooldowns());
        projection.configureResourceReadiness(kernel.resources(),(id,slot)->0);
        var model=projection.hud(actor,new HytaleResourceViewAdapter.Snapshot(new NativeResourceView(100,100),new NativeResourceView(100,100),new NativeResourceView(0,100)),null);
        assertEquals("LOW_STAMINA",model.skills().getFirst().unavailableReason());assertEquals(SkillSlotView.State.INSUFFICIENT_RESOURCE,model.skills().getFirst().state());
        assertEquals(SkillSlotView.State.EMPTY,model.skills().get(1).state());assertEquals("",model.skills().get(1).unavailableReason());
    }
    @Test void countdownNeverShowsReadyZeroWhileCooldownIsStillActive(){
        assertEquals("3",CooldownSweep.countdown(3));assertEquals("2",CooldownSweep.countdown(1.01));
        assertEquals("1",CooldownSweep.countdown(1));assertEquals("0.1",CooldownSweep.countdown(.001));
        assertEquals("",CooldownSweep.countdown(0));assertEquals("",CooldownSweep.countdown(Double.NaN));
    }
    @Test void radialMaskCoversIconCentreAndFaceInsteadOfOnlyTransparentCentreRing() throws Exception {
        try(var input=getClass().getResourceAsStream("/Common/UI/Custom/Assets/RpgHud/CooldownMask.png")){
            var image=ImageIO.read(input);assertEquals(58,image.getWidth());assertEquals(58,image.getHeight());
            assertEquals(255,image.getRGB(29,29)>>>24);int opaque=0;
            for(int y=0;y<58;y++)for(int x=0;x<58;x++)if((image.getRGB(x,y)>>>24)==255)opaque++;
            assertTrue(opaque>58*58*.95,"Opaque face is required for a visible radial sector");
        }
    }
}
