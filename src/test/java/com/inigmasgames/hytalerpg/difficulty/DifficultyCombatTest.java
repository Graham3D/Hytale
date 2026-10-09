package com.inigmasgames.hytalerpg.difficulty;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DifficultyCombatTest {
    @TempDir Path directory;
    final EnemyRewardRegistry legacy=EnemyRewardRegistry.load();
    final AuthoredEncounterCatalog catalog=AuthoredEncounterCatalog.load();
    WorldDifficultyRegistry registry(){var worlds=new WorldDifficultyRegistry(directory.resolve("worlds.json"));
        if(worlds.bindings().isEmpty())for(var mode:DifficultyId.values())worlds.register(new WorldDifficultyRegistry.Binding(UUID.randomUUID(),mode.name(),WorldDifficultyRegistry.Kind.CAMPAIGN,mode,
                mode==DifficultyId.NORMAL?legacy.profileId():"rpg.encounters."+mode.name().toLowerCase(Locale.ROOT)+".pending","server-character",true));return worlds;}
    UUID world(WorldDifficultyRegistry worlds,DifficultyId mode){return worlds.bindings().stream().filter(b->b.difficulty()==mode).findFirst().orElseThrow().worldId();}
    EnemyRewardRegistry.Spawn wild(DifficultyId mode,int biome){var worlds=registry();String band=List.of("emerald_wilds","howling_sands","whisperfrost","devastated_lands").get(biome);
        var source=legacy.biomes().stream().filter(b->b.rpgBand().equals(band)).findFirst().orElseThrow();return catalog.resolver(worlds).classifyAuthored(legacy,world(worlds,mode),UUID.randomUUID(),"Wolf_Black",source.key(),EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();}
    EnemyRewardRegistry.Spawn golem(DifficultyId mode,String key){var worlds=registry();var role=GolemMilestones.load().require(key).roleId();var id=world(worlds,mode);
        var stamp=new CampaignEncounterProjection(id,UUID.randomUUID(),role);
        var source=new GolemEncounterBinding(worlds).classify(id,UUID.randomUUID(),role,"",null,stamp,100).orElseThrow();
        return catalog.resolver(worlds).author(source,AuthoredEncounterCatalog.CAMPAIGN_GOLEM).orElseThrow();}
    @ParameterizedTest @EnumSource(DifficultyId.class) void newWildLevelsUseRegionBandsAndAuditedNativeStats(DifficultyId mode){
        int[][] expected={{5,15,25,35},{42,47,52,57},{64,74,84,94}};
        var factors=ProgressionProfiles.load().difficulty(mode.name());
        for(int i=0;i<4;i++){
            var spawn=wild(mode,i);assertEquals(expected[mode.ordinal()][i],spawn.level());
            var growth=MonsterProgression.current().at(spawn.level());
            assertEquals(103*growth.healthGrowth()*factors.healthMultiplier(),spawn.combat().maxHealth(),1e-6);
            assertEquals(27*growth.damageGrowth()*factors.damageMultiplier(),spawn.combat().attackBasis(),1e-6);
            assertEquals(103,spawn.combat().nativeHealthBaseline(),1e-6);
            assertEquals(mode,spawn.lootSource().orElseThrow().difficulty());assertEquals(spawn.level(),spawn.lootSource().orElseThrow().sourceCombatLevel());
            assertEquals(EncounterProfileResolver.Evidence.AUTHORED_BASELINE_CONNECTED_UNVERIFIED,spawn.combat().evidence());
            assertEquals(growth.elementalFloor(),spawn.combat().resistance().raw(MonsterResistanceProfile.Channel.FIRE),1e-9);
            assertTrue(spawn.combat().resistance().immunities().isEmpty());
        }
    }
    @ParameterizedTest @EnumSource(DifficultyId.class) void allFiveAuthoredGolemsHaveFrozenRewardLevels(DifficultyId mode){
        for(String key:List.of("earth","sand","frost","flame","thunder")){
            var spawn=golem(mode,key);assertTrue(spawn.level()>0);assertEquals(mode,spawn.milestone().difficulty());assertEquals(ProgressionMath.Rank.BOSS,spawn.rank());
            var role=catalog.roles().stream().filter(r->r.id().equals(spawn.roleId())).findFirst().orElseThrow();
            assertEquals(DifficultyDefinitions.load().band(role.campaignRegion(),mode).maximum(),spawn.level());
            var ledger=new EncounterContributions();ledger.begin(spawn);UUID player=UUID.randomUUID();
            assertTrue(ledger.damage(spawn.world(),spawn.enemy(),player,100,90,100,true,101));
            var plan=ledger.death(spawn.world(),spawn.enemy(),Vec3.ZERO,102,List.of(new EncounterContributions.Participant(player,spawn.world(),Vec3.ZERO,37,true,null,null)));
            var share=plan.shares().getFirst();assertEquals(ProgressionMath.enemyReward(spawn.level(),spawn.rank(),spawn.rarity(),37),share.xp());
            assertEquals(spawn.rank().insight,share.insight());assertNotNull(plan.reward(share).milestone());assertSame(plan,ledger.death(spawn.world(),spawn.enemy(),Vec3.ZERO,103,List.of()));
        }
    }
    @Test void resistanceIsAuthoredPerChannelAndOnlyHellFlameOptsIntoImmunity(){
        var fire=MonsterResistanceProfile.Channel.FIRE;
        assertEquals(100,golem(DifficultyId.NORMAL,"flame").combat().resistance().resolve(fire,100).amount());
        assertEquals(75,golem(DifficultyId.NIGHTMARE,"flame").combat().resistance().resolve(fire,100).amount());
        var hell=golem(DifficultyId.HELL,"flame").combat().resistance();assertEquals(.5,hell.effective(fire));assertEquals(0,hell.resolve(fire,100).amount());
        assertEquals(100,hell.resolve(MonsterResistanceProfile.Channel.COLD,100).amount());
        assertEquals(50,golem(DifficultyId.HELL,"frost").combat().resistance().resolve(MonsterResistanceProfile.Channel.COLD,100).amount());
        for(String key:List.of("earth","sand","frost","thunder"))assertTrue(golem(DifficultyId.HELL,key).combat().resistance().immunities().isEmpty());
    }
    @Test void persistenceRestoresFrozenCombatEvenIfNewCatalogChanges(){
        var spawn=wild(DifficultyId.HELL,2);Path path=directory.resolve("encounters");
        try(var store=new FileEncounterStore(path)){store.create(spawn);}
        var changed=new AuthoredEncounterCatalog(1,"future.v2",catalog.roles().stream().map(r->new AuthoredEncounterCatalog.Role(r.id(),r.nativeHealth()*4,r.attackReference(),r.assetSha256(),r.campaignRegion(),r.affinities(),r.hellImmunities())).toList());
        assertNotEquals(spawn.combat().maxHealth(),changed.resolver(registry()).resolveAuthored(spawn.world(),spawn.enemy(),spawn.roleId(),spawn.biomeKey()).maxHealth());
        try(var store=new FileEncounterStore(path);var runtime=new PersistentEncounterRuntime(store,(p,r)->{})){
            assertTrue(runtime.attach(spawn.world(),spawn.enemy(),spawn.roleId(),Optional.empty()));assertEquals(spawn,runtime.spawn(spawn.world(),spawn.enemy()).orElseThrow());
            assertEquals(spawn.lootSource(),runtime.spawn(spawn.world(),spawn.enemy()).orElseThrow().lootSource());
        }
    }
    @Test void oldJsonShapeAndLegacyPilotRewardsAreUnchanged(){
        var spawn=legacy.classify(UUID.randomUUID(),UUID.randomUUID(),"Wolf_Black",legacy.biomes().stream().filter(b->b.rpgBand().equals("whisperfrost")).findFirst().orElseThrow().key(),EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();
        var json=new Gson().toJson(spawn);assertFalse(JsonParser.parseString(json).getAsJsonObject().has("combat"));assertFalse(JsonParser.parseString(json).getAsJsonObject().has("milestone"));assertEquals(45,spawn.level());
        assertEquals(spawn,new Gson().fromJson(json,EnemyRewardRegistry.Spawn.class));assertTrue(spawn.lootSource().isEmpty());
    }
    @Test void spawnRejectsTamperedWorldLevelRoleAndNonfiniteStat(){
        var spawn=wild(DifficultyId.HELL,1);var json=new Gson().toJsonTree(spawn).getAsJsonObject();json.addProperty("level",1);
        assertThrows(RuntimeException.class,()->new Gson().fromJson(json,EnemyRewardRegistry.Spawn.class));
        json.addProperty("level",spawn.level());json.getAsJsonObject("combat").addProperty("worldId",UUID.randomUUID().toString());
        assertThrows(RuntimeException.class,()->new Gson().fromJson(json,EnemyRewardRegistry.Spawn.class));
        assertThrows(IllegalArgumentException.class,()->new AuthoredEncounterCatalog.Role("x",Double.NaN,1,"0".repeat(64),"",Set.of(),Set.of()));
    }
    @Test void unqualifiedRolesBiomesOriginsAndWorldsCannotAcquireAuthoredProfile(){
        var worlds=registry();var resolver=catalog.resolver(worlds);var id=world(worlds,DifficultyId.HELL);
        for(var origin:EnemyRewardRegistry.Origin.values())if(origin!=EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN)
            assertTrue(resolver.classifyAuthored(legacy,id,UUID.randomUUID(),"Wolf_Black",legacy.biomes().getFirst().key(),origin,100).isEmpty());
        assertTrue(resolver.classifyAuthored(legacy,id,UUID.randomUUID(),"Made_Up","biome",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).isEmpty());
        assertTrue(resolver.classifyAuthored(legacy,UUID.randomUUID(),UUID.randomUUID(),"Wolf_Black",legacy.biomes().getFirst().key(),EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).isEmpty());
        assertThrows(IllegalStateException.class,()->resolver.resolveProduction(id,UUID.randomUUID(),"Wolf_Black",legacy.biomes().getFirst().key()));
    }
    @Test void installedRoleHashesAndHealthMatchEveryAuthoredBaseline() throws Exception {
        Path assets=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip");
        try(var zip=new java.util.zip.ZipFile(assets.toFile())){
            for(var role:catalog.roles()){
                var entry=zip.stream().filter(e->e.getName().startsWith("Server/NPC/Roles/")&&e.getName().endsWith("/"+role.id()+".json")).findFirst().orElseThrow();
                byte[] bytes;try(var in=zip.getInputStream(entry)){bytes=in.readAllBytes();}
                assertEquals(role.assetSha256(),HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                assertEquals(role.nativeHealth(),JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("Modify").get("MaxHealth").getAsDouble());
            }
        }
    }
    @ParameterizedTest @EnumSource(DifficultyId.class) void nativeSavedHealthCodecKeepsWoundsAboveDefaultHundred(DifficultyId mode){
        var spawn=golem(mode,"flame");double wounded=spawn.combat().maxHealth()-7;
        var projection=new DifficultyHealthProjection(spawn.world(),spawn.enemy(),spawn.registryProfile(),wounded);
        var encoded=DifficultyHealthProjection.CODEC.encode(projection.clone(),new com.hypixel.hytale.codec.ExtraInfo());
        var restored=DifficultyHealthProjection.CODEC.decode(encoded,new com.hypixel.hytale.codec.ExtraInfo());
        assertEquals(wounded,restored.healthFor(spawn));assertEquals(wounded,restored.clone().healthFor(spawn));
        assertThrows(IllegalStateException.class,()->restored.healthFor(golem(mode,"flame")));
    }
    @Test void healthProjectionRejectsInvalidSavedValues(){
        for(double value:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,
                ()->new DifficultyHealthProjection(UUID.randomUUID(),UUID.randomUUID(),"profile",value));
    }
}
