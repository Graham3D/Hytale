package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyPresentationConfigTest {
    @Test void generatedNamesAreDeterministicAndValidateEveryWholeName(){
        var names=EnemyNamePools.canonical();
        for(int i=0;i<100;i++){String name=names.uniqueName("birth/"+i);assertEquals(name,names.uniqueName("birth/"+i));assertTrue(name.codePointCount(0,name.length())<=32);}
        var json=EnemyAffixRegistry.resource("name-pools-v1.json");json.getAsJsonArray("stems").add("x".repeat(32));
        assertThrows(IllegalArgumentException.class,()->new EnemyNamePools(json));
    }
    @Test void missingOptionalArtKeepsNativeTextureAndEntriesCannotOverrideGameplay(){
        assertTrue(EnemyVisualVariants.canonical().select("Trork_Warrior",EnemyRarity.UNIQUE,null,"birth").isEmpty());
        var json=EnemyAffixRegistry.resource("visual-variants-v1.json");
        var variant=JsonParser.parseString("""
            {"id":"qa_texture","canonicalRoleId":"Trork_Warrior","enemyRarity":"UNIQUE","weight":1,
             "textureOverrides":[{"modelAssetId":"Trork","originalTextureAssetId":"NPC/Trork.png","replacementTextureAssetId":"NPC/Trork_Qa.png"}]}
            """).getAsJsonObject();json.getAsJsonArray("variants").add(variant);
        var registry=new EnemyVisualVariants(json);
        assertEquals("qa_texture",registry.select("Trork_Warrior",EnemyRarity.UNIQUE,null,"birth").orElseThrow().id());
        assertTrue(registry.select("Trork_Warrior_Other",EnemyRarity.UNIQUE,null,"birth").isEmpty());
        assertEquals("qa_texture",registry.select("Trork_Warrior",EnemyRarity.SUPER_UNIQUE,"qa_texture","birth").orElseThrow().id());
        assertThrows(IllegalArgumentException.class,()->registry.validateBindings(v->true,path->false));
        assertThrows(IllegalArgumentException.class,()->registry.select("Skeleton_Archer",EnemyRarity.SUPER_UNIQUE,"qa_texture","birth"));
        variant.addProperty("scale",2);assertThrows(IllegalArgumentException.class,()->new EnemyVisualVariants(json));
        for(String invalid:new String[]{"../bad.png","C:/bad.png","https://example.com/bad.png","/bad.png","NPC\\bad.png","NPC//bad.png"})
            assertThrows(IllegalArgumentException.class,()->EnemyVisualVariants.textureReference(invalid));
    }
    @Test void appendixWeightsAndTemplateRestrictionsAreFrozenAndDeterministic(){
        var json=EnemyAffixRegistry.resource("visual-variants-v1.json");
        for(var id:java.util.List.of("a","b"))json.getAsJsonArray("variants").add(JsonParser.parseString("""
            {"id":"%s","canonicalRoleId":"Trork_Warrior","enemyRarity":"SUPER_UNIQUE","weight":%d,
             "superUniqueTemplateId":"grimgor","textureOverrides":[{"modelAssetId":"Trork",
             "originalTextureAssetId":"NPC/Trork.png","replacementTextureAssetId":"NPC/%s.png"}]}
            """.formatted(id,id.equals("a")?1:5,id)));
        var registry=new EnemyVisualVariants(json);var seen=new java.util.HashSet<String>();
        for(int i=0;i<50;i++){
            var selected=registry.select("Trork_Warrior",EnemyRarity.SUPER_UNIQUE,"grimgor",null,"birth/"+i);
            assertEquals(selected,registry.select("Trork_Warrior",EnemyRarity.SUPER_UNIQUE,"grimgor",null,"birth/"+i));
            seen.add(selected.orElseThrow().id());
        }
        assertEquals(java.util.Set.of("a","b"),seen);
        assertTrue(registry.select("Trork_Warrior",EnemyRarity.SUPER_UNIQUE,"different",null,"birth").isEmpty());
        assertThrows(IllegalArgumentException.class,()->registry.select("Trork_Warrior",EnemyRarity.SUPER_UNIQUE,"different","a","birth"));
        json.getAsJsonArray("variants").get(0).getAsJsonObject().addProperty("weight",0);
        assertThrows(IllegalArgumentException.class,()->new EnemyVisualVariants(json));
    }
}
