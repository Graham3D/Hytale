package com.inigmasgames.hytalerpg;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Guards the pre.2 single-player asset merge that rejected Hookshot when Rope vanished. */
class Stage13AssetValidationR040Test {
    @Test void packagedRopeFallbackIsTheExactInstalledPre2Descriptor() throws Exception {
        try (var input=Stage13AssetValidationR040Test.class.getResourceAsStream("/Server/Entity/Beams/Rope.json")) {
            var value=JsonParser.parseString(new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(1,value.size());
            assertEquals("Trails/Rope.png",value.get("TexturePath").getAsString());
        }
    }
}
