package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.combat.power.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.vfx.ProjectileReadability;
import java.nio.file.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact installed source evidence, distinct from runtime resolution and connected gameplay. */
class Stage13NativeEquipmentSourceTest {
    @Test void everySelectedProductionBaseHasAnExactSourceOrExplicitAuthoredPolicy() throws Exception {
        var registry=NativeItemPowerRegistry.loadCanonical();assertEquals(15,registry.all().size());
        var path=Path.of(System.getProperty("user.home"),"AppData","Roaming","Hytale","install","pre-release","package","game","latest","Assets.zip");
        assertTrue(Files.isRegularFile(path));
        try(var zip=new ZipFile(path.toFile())){
            for(var record:registry.all()){
                var entry=zip.getEntry(record.sourceAsset());assertNotNull(entry,record.itemId());
                try(var stream=zip.getInputStream(entry)){
                    JsonElement source=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8));
                    if(record.selectionPolicy().equals("NATIVE_UNCHARGED_BASE")){
                        assertFalse(record.sourceProperty().matches(".*(Signature|Charged|Combo).*"));
                        for(String field:record.sourceProperty().split("/"))source=source.isJsonArray()?source.getAsJsonArray().get(Integer.parseInt(field)):source.getAsJsonObject().get(field);
                        assertNotNull(source,record.itemId());assertEquals(record.basePower(),source.getAsDouble(),1e-9,record.itemId());
                    }else{
                        assertEquals("RPG_AUTHORED_BASE",record.selectionPolicy());assertEquals(20,record.basePower());assertEquals("",record.sourceProperty());
                    }
                }
            }
        }
    }
    @Test void crossbowUsesTenNotTheFortyFourDamageSummaryOrSeventyEightSignature() {
        var record=NativeItemPowerRegistry.loadCanonical().find("Weapon_Crossbow_Iron").orElseThrow();
        assertEquals(10,record.basePower());assertEquals(10,record.descriptor().weaponPower());assertNull(record.descriptor().magicPower());
    }
    @Test void knownMagicFamilyExceptionIsExplicitlyBoundToItsItemNotAnyMagicObject(){
        var registry=NativeItemPowerRegistry.loadCanonical();var family=Map.of("Family",new String[]{"Magic"},"Type",new String[]{"Weapon"});
        var flame=registry.resolve("Weapon_Staff_Crystal_Flame",family).orElseThrow();
        assertEquals("STAFF",flame.kind());assertEquals(10,flame.descriptor().magicPower());
        assertTrue(registry.resolve("Unregistered_Magic_Staff",family).isEmpty());
        assertTrue(registry.resolve("Weapon_Staff_Crystal_Flame",Map.of("Family",new String[]{"Sword"})).isEmpty());
    }
    @Test void missingOrAmbiguousNativeTagsCannotAuthorizeEvenARegisteredId(){
        var registry=NativeItemPowerRegistry.loadCanonical();
        assertTrue(registry.resolve("Weapon_Spear_Iron",Map.of()).isEmpty());
        assertTrue(registry.resolve("Weapon_Spear_Iron",Map.of("Family",new String[]{"Spear","Sword"})).isEmpty());
        assertTrue(registry.find("Weapon_Spear_Unknown").isEmpty());
    }
    @Test void auditedBattleaxeWithoutNativeFamilyIsNotANameBasedWildcard(){
        var registry=NativeItemPowerRegistry.loadCanonical();var tags=Map.of("Type",new String[]{"Weapon"});
        assertEquals("BATTLEAXE",registry.resolve("Weapon_Battleaxe_Iron",tags).orElseThrow().kind());
        assertTrue(registry.resolve("Weapon_Battleaxe_Unknown",tags).isEmpty());
        assertTrue(registry.resolve("Weapon_Battleaxe_Iron",Map.of()).isEmpty());
        assertTrue(registry.resolve("Weapon_Battleaxe_Iron",Map.of("Type",new String[]{"Weapon"},"Family",new String[]{"Sword"})).isEmpty());
    }
    @Test void registryRejectsDuplicateOrNonfiniteSelections(){
        var first=NativeItemPowerRegistry.loadCanonical().all().iterator().next();
        assertThrows(IllegalArgumentException.class,()->new NativeItemPowerRegistry(List.of(first,first)));
        assertThrows(IllegalArgumentException.class,()->new NativeItemPowerRegistry.Entry("id","SPEAR","Spear","source","field","NATIVE_UNCHARGED_BASE",Double.NaN));
    }
    @Test void visualsAreTenHertzWithNoCatchUpBurstOrRepeatedFailureSpam(){
        var state=new ProjectileReadability(Vec3.ZERO,0);var point=new Vec3(0,0,2);
        assertTrue(state.sample(point,99_999_999).isEmpty());assertTrue(state.sample(point,100_000_000).isPresent());
        assertTrue(state.sample(point,10_000_000_000L).isEmpty());
        assertTrue(state.sample(new Vec3(0,0,3),10_000_000_001L).isEmpty());
        assertTrue(state.firstFailure());assertFalse(state.firstFailure());
        assertEquals(.10,ProjectileReadability.CAST_SECONDS);assertEquals(.20,ProjectileReadability.TRAIL_SECONDS);
        assertEquals(.12,ProjectileReadability.IMPACT_SECONDS);assertEquals(.15,ProjectileReadability.EXPIRY_SECONDS);
    }
}
