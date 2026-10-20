package com.inigmasgames.hytalerpg;
import com.inigmasgames.hytalerpg.vfx.AreaPresentationTemplate;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source/asset contracts only: native UI parsing, position and readability still require a client. */
class Stage13FinisherPresentationTest {
    @Test void brighterTrailRequiresTheExplicitEmpoweredPhase(){
        var normal=AreaPresentationTemplate.color("PHYSICAL","IMPACT_STRIKE");
        var empowered=AreaPresentationTemplate.color("PHYSICAL","IMPACT_FINISHER");
        assertTrue(empowered.red()>normal.red());assertTrue(empowered.green()>normal.green());assertTrue(empowered.blue()>normal.blue());
        assertTrue(empowered.red()<=1&&empowered.green()<=1&&empowered.blue()<=1);
    }
    @Test void exactlyThreeNoninteractivePipsDoNotTouchNativeControls()throws Exception{
        var ui=Files.readString(Path.of("src/main/resources/Common/UI/Custom/RpgFinisherPips.ui"));
        assertEquals(3,java.util.regex.Pattern.compile("Group #FinisherPip[123] ").matcher(ui).results().count());
        for(var forbidden:new String[]{"Button","Text:","TexturePath","#Health","#Mana","#Stamina","#Experience","Ability","Hotbar"})assertFalse(ui.contains(forbidden),forbidden);
        var code=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/ui/hud/FinisherHud.java"));
        assertFalse(code.contains("addEventBinding"));assertFalse(code.contains("appendInline"));
        assertTrue(code.contains("finishing_strike"));assertTrue(code.contains("count<0||count>3"));
    }
    @Test void sharedProfileTransformsPreserveFinisherRatherThanCreatingAnotherCombo(){
        var f=new Stage11FoundationTest();var p=f.effective("finishing_strike","long_reach","multistrike");
        assertTrue(p.strike().details().finisher());assertEquals(3,p.strike().repeats());
        assertEquals(2.8*1.25,p.strike().range(),1e-9);assertEquals(1.2,p.strike().coefficient());
    }
}
