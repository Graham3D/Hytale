package com.inigmasgames.hytalerpg.ui.skilltree;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class R050IconSourcePipelineTest {
    @Test void everyOwnerAuthoredIconIsCanonicalAndByteExactInBothResourceSurfaces() throws Exception {
        Path root=Path.of("").toAbsolutePath();
        var index=new Gson().fromJson(Files.readString(root.resolve("src/main/resources/rpg/presentation/icon-index.json")),
                RpgSkillIcons.Index.class);
        var rows=new HashMap<String,RpgSkillIcons.Entry>();
        for(var row:index.entries())rows.put(row.fileName().toLowerCase(),row);
        int checked=0;
        for(String kind:new String[]{"Skill","Passive"}){
            Path art=root.resolve("art").resolve(kind+"s");if(!Files.isDirectory(art))continue;
            try(var stream=Files.list(art)){
                for(Path source:stream.filter(path->path.getFileName().toString().toLowerCase().endsWith(".png")).toList()){
                    var row=rows.get(source.getFileName().toString().toLowerCase());assertNotNull(row,"Unknown icon "+source);
                    assertEquals(kind,row.kind());
                    byte[] expected=Files.readAllBytes(source);
                    Path ui=root.resolve("src/main/resources/Common/UI/Custom/Icons/RPG").resolve(row.fileName());
                    assertTrue(Files.isRegularFile(ui),"Missing canonical UI icon "+ui);
                    assertArrayEquals(digest(expected),digest(Files.readAllBytes(ui)),"UI icon drift "+row.fileName());
                    if("Skill".equals(kind)){
                        Path nativeIcon=root.resolve("src/main/resources/Common/Icons/Items/RPG").resolve(row.fileName());
                        assertTrue(Files.isRegularFile(nativeIcon),"Missing canonical native icon "+nativeIcon);
                        assertArrayEquals(digest(expected),digest(Files.readAllBytes(nativeIcon)),"Native icon drift "+row.fileName());
                    }
                    checked++;
                }
            }
        }
        assertTrue(checked>=17,"Expected the current owner-authored icon cohort");
    }

    private static byte[] digest(byte[] value) throws Exception{return MessageDigest.getInstance("SHA-256").digest(value);}
}
