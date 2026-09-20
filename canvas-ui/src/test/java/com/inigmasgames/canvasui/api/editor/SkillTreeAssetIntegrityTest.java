package com.inigmasgames.canvasui.api.editor;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SkillTreeAssetIntegrityTest {
    private static final String ROOT="/Common/UI/Custom/Assets/SkillTree/";
    private static final Map<String,String> EXPECTED=Map.of(
            "skilltree_joint.png","06D4EA2118CE605DC58AEB77DD50F264EAC2688049F67069B546BCCD638571CD",
            "skilltree_passive_occupied.png","9273F52850BB5289953CAD7FECB4C761CF616245932F7A6B84AD0429405D8C1C",
            "skilltree_passive_unoccupied.png","51E60781D51016F0EB2564CA398B9B218866F080D5C9BC294003F613DC4B527C",
            "Slot@2x.png","52A63FC9ABD7F5A5BF8F719BB03B2EAB00C8F0942A35F029CC5841BB565FAB69",
            "SpecialSlotTemporary@2x.png","E390AA7F60C8C483C4A0E3E37F071F7430A5BCA9254330EF2471239817024A50",
            "StructuralCraftingArrowUp@2x.png","A8F2268DF87E541F82F2778ADF08573BA8362B6FAF66B038766AA99260B361D0");

    @Test void packagedSkillTreeArtMatchesTheApprovedAndNativeSources() throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(var entry:EXPECTED.entrySet()){
            try(InputStream input=getClass().getResourceAsStream(ROOT+entry.getKey())){
                assertNotNull(input,entry.getKey());
                assertEquals(entry.getValue(),java.util.HexFormat.of().withUpperCase().formatHex(digest.digest(input.readAllBytes())),entry.getKey());
            }
        }
    }
}
