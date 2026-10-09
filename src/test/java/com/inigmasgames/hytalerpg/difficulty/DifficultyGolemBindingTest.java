package com.inigmasgames.hytalerpg.difficulty;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DifficultyGolemBindingTest {
    @TempDir Path root;
    @Test void installedGolemRolesResolveTheNativeModelsUsedForPreSpawnClearance() throws Exception {
        var assets=Path.of(System.getProperty("user.home"),"AppData","Roaming","Hytale","install",
                "pre-release","package","game","latest","Assets.zip");
        try(var zip=new java.util.zip.ZipFile(assets.toFile())){
            for(var golem:GolemMilestones.load().golems()){
                var roleEntry=zip.getEntry(golem.assetPath());
                assertNotNull(roleEntry,"Installed native role "+golem.roleId());
                com.google.gson.JsonObject role;
                try(var reader=new java.io.InputStreamReader(zip.getInputStream(roleEntry),java.nio.charset.StandardCharsets.UTF_8)){
                    role=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                }
                String appearance=role.getAsJsonObject("Modify").get("Appearance").getAsString();
                assertEquals(golem.roleId(),appearance,"Command clearance must use the role's actual appearance");
                assertNotNull(zip.getEntry("Server/Models/Elemental/"+appearance+".json"),
                        "Native model asset required before spawning "+golem.roleId());
            }
        }
    }
    @Test void exactAuditedMarkerOrOperatorOwnershipRequiredInEachWorld(){
        var worlds=new WorldDifficultyRegistry(root.resolve("worlds.json"));var policy=new GolemEncounterBinding(worlds);var catalog=GolemMilestones.load();
        for(var mode:DifficultyId.values()){
            var world=UUID.randomUUID();worlds.register(new WorldDifficultyRegistry.Binding(world,mode.name(),WorldDifficultyRegistry.Kind.CAMPAIGN,mode,"stage2","server-character",true));
            for(var golem:catalog.golems()){
                UUID enemy=UUID.randomUUID();assertTrue(policy.classify(world,enemy,golem.roleId(),"",null,null,1).isEmpty());
                assertTrue(policy.classify(world,enemy,golem.roleId(),"wrong marker",UUID.randomUUID(),null,1).isEmpty());
                var marker=policy.classify(world,enemy,golem.roleId(),golem.nativeMarker(),UUID.randomUUID(),null,1);
                assertEquals(!golem.nativeMarker().isEmpty(),marker.isPresent());
                var stamped=policy.classify(world,enemy,golem.roleId(),"",null,new CampaignEncounterProjection(world,UUID.randomUUID(),golem.roleId()),1).orElseThrow();
                assertEquals(mode,stamped.milestone().difficulty());assertEquals(golem.id(),stamped.milestone().milestone());assertEquals(0,stamped.level());
                assertTrue(policy.classify(world,enemy,golem.roleId(),"",null,new CampaignEncounterProjection(UUID.randomUUID(),UUID.randomUUID(),golem.roleId()),1).isEmpty());
            }
        }
    }
    @Test void unknownWorldHubAndOtherGolemsCannotEarn(){var worlds=new WorldDifficultyRegistry(root.resolve("worlds.json"));var policy=new GolemEncounterBinding(worlds);UUID world=UUID.randomUUID();
        assertTrue(policy.classify(world,UUID.randomUUID(),"Golem_Crystal_Earth","Golem_Crystal_Earth",UUID.randomUUID(),null,1).isEmpty());
        worlds.register(new WorldDifficultyRegistry.Binding(world,"hub",WorldDifficultyRegistry.Kind.SHARED_HUB,DifficultyId.NORMAL,"hub","server-character",true));
        assertTrue(policy.classify(world,UUID.randomUUID(),"Golem_Crystal_Earth","Golem_Crystal_Earth",UUID.randomUUID(),null,1).isEmpty());
        assertTrue(GolemMilestones.load().role("Golem_Crystal_Firesteel").isEmpty());assertTrue(GolemMilestones.load().require("thunder").nativeMarker().isEmpty());}
    @Test void portalsSurviveRestartAndCannotOverlapOrSilentlyReset()throws Exception{var file=root.resolve("portals.json");var portals=new DifficultyPortals(file);UUID world=UUID.randomUUID();
        var p=new DifficultyPortals.Portal(UUID.randomUUID(),UUID.randomUUID(),world,DifficultyId.NIGHTMARE,10,70,10);portals.add(p);
        assertEquals(List.of(p),new DifficultyPortals(file).all());assertTrue(p.label().contains("Recommended Level: 40+"));assertTrue(p.touches(10,71,10));assertFalse(p.touches(13,70,10));
        assertThrows(IllegalStateException.class,()->portals.add(new DifficultyPortals.Portal(UUID.randomUUID(),p.owner(),world,DifficultyId.HELL,11,70,10)));
        new DifficultyPortals(file).remove(p.id());assertTrue(new DifficultyPortals(file).all().isEmpty());Files.writeString(file,"corrupt");assertThrows(IllegalStateException.class,()->new DifficultyPortals(file));}
}
