package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.strike.*;
import com.inigmasgames.hytalerpg.execution.hytale.NativeStrikeFeedback;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13PlayerFeedbackCorrectionTest {
    static JsonObject json(String path)throws Exception{try(var in=Stage13PlayerFeedbackCorrectionTest.class.getResourceAsStream(path)){return JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(in))).getAsJsonObject();}}
    @Test void quickSlashHasTwoFullDamageHitsWithOneUnchangedCostAndCooldown(){
        var p=Stage04SkillProfiles.loadCanonical(RpgCatalog.loadCanonical()).require("quick_slash");
        assertEquals(2,p.strike().repeats());assertEquals(.85,p.strike().coefficient());assertEquals(5,p.resourceCost());assertEquals(.8,p.cooldownSeconds());
        assertEquals(Set.of("SWORD","LONGSWORD","DAGGER"),p.allowedMainHandKinds());
    }
    @Test void multistrikeRepeatsTheCompleteQuickSlashPairWithOnePayment(){
        var h=new Stage11ResourcePassivesTest.H("quick_slash");
        h.link("multistrike",com.inigmasgames.hytalerpg.domain.PassiveSlot.PASSIVE01);
        assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());var c=h.last();
        assertEquals(6,c.profile().strike().repeats());assertEquals(.85,c.profile().strike().coefficient());
        assertEquals(95,h.current(com.inigmasgames.hytalerpg.combat.resource.ResourceType.STAMINA));assertEquals(1,h.cooldownSaves);
        for(int group=1;group<=2;group++){
            var child=c.multistrikeCopy(group);assertEquals(.65,child.snapshot().modifiers().factor(),1e-9);
            assertEquals("PASS",child.effects().claim(child.skillInstanceId(),1,false));
            assertEquals("PASS",child.effects().authoredComponent(child.skillInstanceId()));
            assertThrows(IllegalStateException.class,()->child.multistrikeCopy(1));
        }
        for(String kind:List.of("SWORD","LONGSWORD","DAGGER")){
            double interval=NativeStrikeFeedback.quickSlashInterval(kind);
            var schedule=new StrikeRepeatSchedule(6,interval,0,interval*6);
            for(int hit=1;hit<6;hit++)assertEquals(hit,schedule.claimDue(Math.round(interval*1e9)*hit).orElseThrow());
            assertTrue(schedule.complete(Math.round(interval*6e9)+10));
            assertTrue(c.profile().strike().details().actionLockSeconds()>=interval*6);
        }
    }
    @Test void eachWeaponSchedulesBothDirectionsWithoutDuplicateHits(){
        for(String kind:List.of("SWORD","LONGSWORD","DAGGER")){
            double interval=NativeStrikeFeedback.quickSlashInterval(kind);long second=Math.round(interval*1e9);
            var schedule=new StrikeRepeatSchedule(2,interval,0,interval*2);var ledger=new SkillHitLedger();
            assertTrue(ledger.accept("root",0,"target"));assertFalse(ledger.accept("root",0,"target"));
            assertTrue(schedule.claimDue(second-1).isEmpty());assertEquals(1,schedule.claimDue(second).orElseThrow());
            assertTrue(ledger.accept("root",1,"target"));assertFalse(ledger.accept("root",1,"target"));
            assertTrue(schedule.claimDue(second+1).isEmpty());assertFalse(schedule.complete(second));assertTrue(schedule.complete(Math.round(interval*2e9)));
        }
    }
    @Test void animationAssetsReuseInstalledLightSwingsAtExactlyOneAndHalfSpeed()throws Exception{
        try(var zip=new ZipFile(Path.of(System.getenv("APPDATA"),"Hytale/install/pre-release/package/game/latest/Assets.zip").toFile())){
            for(String profile:List.of("Sword","Longsword","Daggers")){
                JsonObject source;try(var reader=new java.io.InputStreamReader(zip.getInputStream(zip.getEntry("Server/Item/Animations/"+profile+".json")))){source=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("Animations");}
                var replacement=json("/Server/Item/Animations/RPG_QuickSlash_"+profile+".json");assertEquals(profile,replacement.get("Parent").getAsString());
                for(String action:List.of("SwingLeft","SwingRight")){
                    var expected=source.getAsJsonObject(action).deepCopy();expected.addProperty("Speed",expected.get("Speed").getAsDouble()*3.0);
                    assertEquals(expected,replacement.getAsJsonObject("Animations").getAsJsonObject(action));
                }
            }
        }
    }
    @Test void chillIconsAreNativeDebuffsWithoutAdditionalGameplayEffects()throws Exception{
        for(int stack=1;stack<=4;stack++){
            var icon=json("/Server/Entity/Effects/RPG/RPG_Chill_Icon_"+stack+".json");
            assertTrue(icon.get("Debuff").getAsBoolean());assertEquals("UI/StatusEffects/RPG/StatusChill0"+stack+".png",icon.get("StatusEffectIcon").getAsString());
            assertFalse(icon.has("ApplicationEffects"));assertFalse(icon.has("DamageCalculator"));
        }
        for(String id:List.of("RPG_Frozen","RPG_Frozen_Slow")){var icon=json("/Server/Entity/Effects/RPG/"+id+".json");assertTrue(icon.get("Debuff").getAsBoolean());assertTrue(icon.get("StatusEffectIcon").getAsString().endsWith("05.png"));}
    }
    @Test void packagedFiveIconsAreByteIdenticalToOwnerArtwork()throws Exception{
        for(int stack=1;stack<=5;stack++)try(var in=getClass().getResourceAsStream("/Common/UI/StatusEffects/RPG/StatusChill0"+stack+".png")){
            assertArrayEquals(Files.readAllBytes(Path.of("art/StatusChill0"+stack+".png")),Objects.requireNonNull(in).readAllBytes());
        }
    }
    @Test void revisionBadgeIsTopRightWithoutNativeResourceControls()throws Exception{
        String badge=Files.readString(Path.of("src/main/resources/Common/UI/Custom/Phase00RevisionHud.ui"));
        assertTrue(badge.contains("Right: 18, Top: 18"));assertTrue(badge.contains("R032-U"));
        String hud=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/ui/hud/RpgHud.java"));
        assertTrue(hud.contains("commands.append(\"Phase00RevisionHud.ui\")"));assertTrue(hud.contains("HealingTetherPresentation.REVISION"));
        assertEquals("R032-AL",com.inigmasgames.hytalerpg.execution.hytale.HealingTetherPresentation.REVISION);
        for(String forbidden:List.of("#Health","#Stamina","#Mana"))assertFalse(hud.contains(forbidden));
    }
}
