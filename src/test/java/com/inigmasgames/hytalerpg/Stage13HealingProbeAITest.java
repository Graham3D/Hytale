package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.execution.hytale.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13HealingProbeAITest {
    static JsonObject json(String path)throws Exception{return JsonParser.parseString(Files.readString(Path.of("src/main/resources/"+path))).getAsJsonObject();}
    @Test void explicitDisposablePathOnly(){
        var live=Path.of("/hytale/Saves/RPG");var root=Path.of("/repo/run/healing-probe-ai");
        assertTrue(HealingProbePolicy.allows(root,root.resolve("universe/worlds/default"),live));
        assertFalse(HealingProbePolicy.allows(live,live,live));
        assertFalse(HealingProbePolicy.allows(live.resolve("healing-probe-ai"),live.resolve("healing-probe-ai/world"),live));
        assertFalse(HealingProbePolicy.allows(Path.of("/hytale"),live,live));
        assertFalse(HealingProbePolicy.allows(root,Path.of("/repo/run/healing-probe-ai-evil/world"),live));
        assertFalse(HealingProbePolicy.allows(root,root.resolve("../live"),live));
        assertFalse(HealingProbePolicy.allows(root,live,live));
        assertFalse(HealingProbePolicy.allows(null,root,live));
    }
    @Test void boundedModesAndFiniteWatchdog(){
        assertEquals(9,HealingProbePolicy.Mode.values().length); // Eight retained controls plus read-only CHANNEL observer.
        for(var mode:HealingProbePolicy.Mode.values())assertEquals(mode,HealingProbePolicy.mode(mode.name().toLowerCase(Locale.ROOT).replace('_','-')));
        assertThrows(IllegalArgumentException.class,()->HealingProbePolicy.mode("live"));
        assertEquals(10,HealingProbePolicy.RUN_SECONDS);assertEquals(12,HealingProbePolicy.EFFECT_SECONDS);
        assertEquals(128,HealingProbePolicy.MAX_TRANSITIONS);assertEquals(4,HealingProbePolicy.MAX_WORLDS);
    }
    @Test void visibleControlChangesOnlyModelAndTextureAndDependenciesExist()throws Exception{
        var empty=json("Server/Models/RPG/RPG_Healing_Stream.json");var visible=json("Server/Models/RPG/RPG_Probe_Healing_Visible.json");
        try(var zip=new ZipFile(Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip").toFile())){
            for(var model:List.of(empty,visible))for(String key:List.of("Model","Texture"))assertNotNull(zip.getEntry("Common/"+model.get(key).getAsString()));
        }
        empty.remove("Model");empty.remove("Texture");visible.remove("Model");visible.remove("Texture");assertEquals(empty,visible);
    }
    @Test void probeEffectsRetainOriginalAppearanceAndOtherwiseMatchCosmeticWrappers()throws Exception{
        for(String suffix:List.of("Recipient","Staff_Block5","Staff_Knob","Staff_Origin_Projectile","Staff_TopPommel")){
            var base=json("Server/Entity/Effects/RPG/RPG_Healing_"+suffix+".json");
            var probe=json("Server/Entity/Effects/RPG/RPG_Probe_Healing_"+suffix+".json");
            // AP recolors production only. Diagnostic controls intentionally retain their stock visuals.
            var current=base.getAsJsonObject("ApplicationEffects").getAsJsonArray("Particles").get(0).getAsJsonObject();
            var control=probe.getAsJsonObject("ApplicationEffects").getAsJsonArray("Particles").get(0).getAsJsonObject();
            assertEquals(suffix.equals("Recipient")?"RPG_Heal_Red_Recipient":"RPG_Heal_Red_Staff",current.get("SystemId").getAsString());
            assertEquals(suffix.equals("Recipient")?"Effect_Health_Pack":"Staff_Bronze",control.get("SystemId").getAsString());
            current.add("SystemId",control.get("SystemId"));
            assertEquals(12,probe.get("Duration").getAsDouble());probe.remove("Duration");base.remove("Duration");assertEquals(base,probe);
        }
    }
    @Test void lockInAhDirectionalEmitterExtentLimitation(){
        var origin=new Vec3(0,0,0);
        assertEquals(HealingParticleVisuals.rotation(origin,new Vec3(2,0,0)),HealingParticleVisuals.rotation(origin,new Vec3(18,0,0)),
                "AH source/rotation-only particle cannot encode the different endpoint distances; this is not a correct two-ended renderer");
    }
    @Test void noProbeInspectorConsumesNativeChangesOrClaimsClientSuccess()throws Exception{
        var source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HealingPresentationProbe.java"));
        for(String forbidden:List.of(".consumeChanges(",".consumeNetworkOutdated(",".clearChanges(",".addInfiniteEffect(","CLIENT_RENDERED\",true"))assertFalse(source.contains(forbidden),forbidden);
        assertTrue(source.contains("\"packetSent\",\"UNVERIFIED\""));
        var command=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/commands/HealingProbeCommand.java"));
        assertTrue(command.contains("requirePermission(\"inigmasgames.rpg.healingprobe\")"));
    }
}
