package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** The installed Hytale Item validator only accepts texture paths in approved Common roots. */
class MonsterVisualAssetsTest {
    private static final String BASE="/Common/NPC/RPG/Enemies/Visual/Trork/";
    @Test void itemTextureUsesApprovedRootAndAllReferencedArtIsPackaged() throws Exception {
        var itemResource=getClass().getResourceAsStream(
                "/Server/Item/Items/RPG/EnemyVisual/RPG_ME_Trork_Battleaxe_FireVisual.json");
        assertNotNull(itemResource);
        var item=JsonParser.parseReader(new InputStreamReader(itemResource,StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("Weapon_Battleaxe_Stone_Trork",item.get("Parent").getAsString());
        assertEquals(0,item.getAsJsonArray("Categories").size());
        String weapon=item.get("Texture").getAsString();
        assertTrue(weapon.startsWith("Items/"),"Hytale Item.Texture requires an approved Common root");
        assertPng("/Common/"+weapon);
        for(String rarity:new String[]{"Champion","Unique","SuperUnique"})assertPng(BASE+"Skin_"+rarity+".png");
        for(String part:new String[]{"Chest","Hands","Feet","Head"})
            assertPng(BASE+"Armor_StoneSkin_"+part+".png");
        assertNull(getClass().getResource("/Common/RPG/Enemies/Visual/Trork/Weapon_Fire_Battleaxe.png"));
    }
    private void assertPng(String path) throws Exception {
        try(var stream=getClass().getResourceAsStream(path)){
            assertNotNull(stream,"Missing packaged visual: "+path);
            var image=ImageIO.read(stream);
            assertNotNull(image,"Invalid PNG: "+path);
            assertTrue(image.getWidth()>0&&image.getHeight()>0);
        }
    }
}
