package com.inigmasgames.canvasui.api.editor;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SkillTreeAssetIntegrityTest {
    private static final String ROOT="/Common/UI/Custom/Assets/SkillTree/";
    private static final Map<String,String> EXPECTED=Map.ofEntries(
            Map.entry("skilltree_joint.png","06D4EA2118CE605DC58AEB77DD50F264EAC2688049F67069B546BCCD638571CD"),
            Map.entry("skilltree_passive_occupied.png","9273F52850BB5289953CAD7FECB4C761CF616245932F7A6B84AD0429405D8C1C"),
            Map.entry("skilltree_passive_unoccupied.png","51E60781D51016F0EB2564CA398B9B218866F080D5C9BC294003F613DC4B527C"),
            Map.entry("skilltree_port.png","0627B757AAC4B24BE6A7ABF49C2B4D7EB591439AF99F3D3652C5F572EDB8356B"),
            Map.entry("Slot@2x.png","52A63FC9ABD7F5A5BF8F719BB03B2EAB00C8F0942A35F029CC5841BB565FAB69"),
            Map.entry("SpecialSlotTemporary@2x.png","E390AA7F60C8C483C4A0E3E37F071F7430A5BCA9254330EF2471239817024A50"),
            Map.entry("StructuralCraftingArrowUp@2x.png","A8F2268DF87E541F82F2778ADF08573BA8362B6FAF66B038766AA99260B361D0"),
            Map.entry("StructuralCraftingArrowUpRight@2x.png","33AE0D4F81039A1519B21F157B8FE6CB2A0418B8AE8C28E6BFDF22DE0440598F"),
            Map.entry("StructuralCraftingArrowRight@2x.png","4AA76E903A099135EB83C9EAB1293428E4082DFC54AE8DC3074BCEB525BFCD4B"),
            Map.entry("StructuralCraftingArrowDownRight@2x.png","121456800A2E60A22046BCF30C3BEDC08D4251D9CC4F0F1EB1F12F1B6ACD1AE3"),
            Map.entry("StructuralCraftingArrowDown@2x.png","0BBA0F329A5F1327C920E49F5E7B3AAF81BF469585C82CBE072819585A2884EB"),
            Map.entry("StructuralCraftingArrowDownLeft@2x.png","FAC4A8FE72375B5EE0B7900D2AF6459CF7699C90849790050CD74A6D87D393A6"),
            Map.entry("StructuralCraftingArrowLeft@2x.png","F303F08DD48AB1AC34E98118B595962F15AF8B746E7F1F82F4313B1CE8577586"),
            Map.entry("StructuralCraftingArrowUpLeft@2x.png","382D0E71CA068F07F68C66F9E5B8586A318A4C751AE9A5DF67B566033AB97ABE"),
            Map.entry("Hytale/ContainerFullPatch@2x.png","E3B2844B4FAF1217DCB1C43CDEAC55D5473A3253C8FC1EE5B9EC7F24FC1AB7E9"),
            Map.entry("Hytale/ContainerHeader@2x.png","79BB9D916C5682679CD1A3C4CC9569E81E4A875DC1B437C62643F5E7611351ED"),
            Map.entry("Hytale/ContainerDecorationTop@2x.png","5797B2C5B375A5ACEF1A9321A65B67345F5AF489B0499A85CFB13EEBBCF734FA"),
            Map.entry("Hytale/ContainerDecorationBottom@2x.png","EA09B85F36835BF402381145011168306860B6A364775B201476ADED4512DEBD"),
            Map.entry("Hytale/ContainerPanelPatch@2x.png","AE096ECA2BD4C50FAA21F5915E68F5ABF0E7435516D5213ACF219C9EDB144EC3"),
            Map.entry("Hytale/ContainerPanelLightPatch@2x.png","A87A4861B503A9EA0E9643CA7E478161E44599D3C01EC1E5D4D75F0EC5D969EA"),
            Map.entry("Hytale/DiagramCraftingBackground@2x.png","CDE0F00445ED640FA1EA9E1E8B31690A3A5BEE9B08CF2221CFE8576B9BEA49B8"),
            Map.entry("Hytale/HeaderTabBackground@2x.png","76491F41CD314AA2E8FFAD1994F09414296594236F186D8E885AC8B66632FBDF"),
            Map.entry("Hytale/HeaderTabSelectedBackground@2x.png","CD19EF85AFEDF036B58CA49822553A954DA427743E7BD5A3BB82F947EECFD7E3"),
            Map.entry("Hytale/TabSelectedOverlay@2x.png","6D5DDE222575CE414C5E9ABBE503A57B7D3B9B90E56DA7AAB6074C6F131CF3C3"),
            Map.entry("Hytale/TextGradient.png","809600625645B69BCB91D2A5B35204709FBE0E6E251592A8EB57B2410CED0D8D"),
            Map.entry("Hytale/InputBox@2x.png","5A7B31B3161AE25D5A52E279E0A83594519E5BE933AD1A4AD7CBA83A0B11351D"),
            Map.entry("Hytale/Divider@2x.png","D68ADBA12FBA7683ACC26E59A9FA824F202785EB7888F6AE6972AC847C9681B2"),
            Map.entry("Hytale/UnknownItemIcon@2x.png","6D9EF7879B5B597BF6145CBFBE90FE087CEFA855D5D8E7FE727D8B3756D86065"),
            Map.entry("Hytale/Buttons/Primary@2x.png","28B6DD8A7F0F4120D499AFCF17CDB9DBDB059FEF1E57CC4D4C9F17EA442A4617"),
            Map.entry("Hytale/Buttons/Primary_Hovered@2x.png","7ABE9389458DC442628C302164ADECBB65B43458C1C3E2B22F5F53644E3635FD"),
            Map.entry("Hytale/Buttons/Primary_Pressed@2x.png","C648AFD40403EEB9B2F34842E52DDA821DFCBDC28C1CC7E51FE2CFC485F00E58"),
            Map.entry("Hytale/Buttons/Destructive@2x.png","3F577C6C22A8FF173ACC0E5B6FACD15ED9F181E8E5EAFE38483BE04358CD6712"),
            Map.entry("Hytale/Buttons/Destructive_Hovered@2x.png","A9130564471757E65F4AC94ABCFC7249679C4B1352363D41ABAE85D339661DFD"),
            Map.entry("Hytale/Buttons/Destructive_Pressed@2x.png","960D28E09057F4390543ACBAD2A84F343958214BE3C69D38D2885E0EF70A14E8"));

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
