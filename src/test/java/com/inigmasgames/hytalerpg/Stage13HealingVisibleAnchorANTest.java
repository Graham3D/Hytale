package com.inigmasgames.hytalerpg;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13HealingVisibleAnchorANTest {
    @Test void proofUsesShippedRenderableModelAndExactExistingBlipAttachment()throws Exception{
        var model=JsonParser.parseString(Files.readString(Path.of("src/main/resources/Server/Models/RPG/RPG_Heal_Path_Visible_AN.json"))).getAsJsonObject();
        assertEquals("NPC/MISC/Mannequin/Models/Model.blockymodel",model.get("Model").getAsString());
        assertEquals("NPC/MISC/Mannequin/Models/Model_Default.png",model.get("Texture").getAsString());
        assertEquals(1,model.get("MinScale").getAsInt());assertEquals(1,model.get("MaxScale").getAsInt());
        var particles=model.getAsJsonArray("Particles");assertEquals(1,particles.size());
        var particle=particles.get(0).getAsJsonObject();
        assertEquals("RPG_Heal_Path_Blips",particle.get("SystemId").getAsString());
        assertEquals("Entity",particle.get("TargetEntityPart").getAsString());
        assertEquals(1,particle.get("Scale").getAsInt());
        assertFalse(particle.get("DetachedFromModel").getAsBoolean());assertTrue(particle.get("ClearParticlesOnRemove").getAsBoolean());
        try(var assets=new ZipFile(Path.of(System.getenv("APPDATA"),"Hytale/install/pre-release/package/game/latest/Assets.zip").toFile())){
            assertNotNull(assets.getEntry("Common/"+model.get("Model").getAsString()));
            assertNotNull(assets.getEntry("Common/"+model.get("Texture").getAsString()));
        }
    }
    @Test void amAssetsAndSplineRemainByteIdenticalToDeployedAm()throws Exception{
        try(var baseline=new ZipFile("evidence/stage-13/cohort-am/artifacts/HyARPG.jar")){
            for(String directory:new String[]{"Server/Particles/RPG/HealingPath","Server/Models/RPG"}){
                try(var files=Files.list(Path.of("src/main/resources",directory))){
                    for(var file:files.filter(p->p.getFileName().toString().startsWith("RPG_Heal_Path_")&&!p.getFileName().toString().contains("Visible_AN")).toList()){
                        var entry=baseline.getEntry(directory+"/"+file.getFileName());assertNotNull(entry);
                        try(var stream=baseline.getInputStream(entry)){assertArrayEquals(stream.readAllBytes(),Files.readAllBytes(file),file.toString());}
                    }
                }
            }
        }
    }
}
