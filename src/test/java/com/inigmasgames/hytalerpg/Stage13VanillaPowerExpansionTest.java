package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.combat.power.*;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.diagnostics.*;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleEquipmentAdapter;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Production description/coordinator/geometry/projectile path; native connected carrier/input QA remains separate. */
class Stage13VanillaPowerExpansionTest {
    static final NativeItemPowerRegistry REGISTRY=NativeItemPowerRegistry.loadProduction();
    static JsonObject json(ZipFile zip,String path)throws Exception{
        var e=Objects.requireNonNull(zip.getEntry(path),path);
        try(var r=new java.io.InputStreamReader(zip.getInputStream(e),StandardCharsets.UTF_8)){return JsonParser.parseReader(r).getAsJsonObject();}
    }
    static Map<String,String[]> tags(String family){return family.isEmpty()?Map.of("Type",new String[]{"Weapon"}):Map.of("Type",new String[]{"Weapon"},"Family",new String[]{family});}
    static Map<String,String[]> inheritedTags(String id,Map<String,JsonObject> items){
        var item=Objects.requireNonNull(items.get(id),id);var result=new HashMap<String,String[]>();
        if(item.has("Parent"))result.putAll(inheritedTags(item.get("Parent").getAsString(),items));
        if(item.has("Tags"))for(var e:item.getAsJsonObject("Tags").entrySet())result.put(e.getKey(),new Gson().fromJson(e.getValue(),String[].class));
        return result;
    }
    @Test void allProductionIdsResolveTheirActualInstalledInheritedTags()throws Exception{
        try(var zip=new ZipFile(Stage13ConnectedCastingCorrectionTest.ASSETS.toFile())){
            var items=new HashMap<String,JsonObject>();
            for(var e:Collections.list(zip.entries()))if(e.getName().startsWith("Server/Item/Items/")&&e.getName().endsWith(".json")){
                String p=e.getName();items.put(p.substring(p.lastIndexOf('/')+1,p.length()-5),json(zip,p));
            }
            for(var e:REGISTRY.all())assertEquals(e.descriptor(),HytaleEquipmentAdapter.describe(e.itemId(),inheritedTags(e.itemId(),items)).power(),e.itemId());
        }
    }
    @Test void connectedFlameLongswordDescriptionCommitsOneEmptyQuickSlash()throws Exception{
        try(var zip=new ZipFile(Stage13ConnectedCastingCorrectionTest.ASSETS.toFile())){
            var item=json(zip,"Server/Item/Items/Weapon/Longsword/Weapon_Longsword_Flame.json");
            Map<String,String[]> raw=new Gson().fromJson(item.get("Tags"),new com.google.gson.reflect.TypeToken<Map<String,String[]>>(){}.getType());
            var h=new Stage13ConnectedCastingCorrectionTest.H("quick_slash");h.held=HytaleEquipmentAdapter.describe("Weapon_Longsword_Flame",raw);
            assertEquals("LONGSWORD",h.held.weaponKind());assertEquals(31,h.held.power().weaponPower());
            var result=h.cast();assertTrue(result.committed(),result.toString());assertEquals(0,result.affectedTargets());assertEquals(1,h.swings);
            assertEquals(31,h.last().snapshot().basePower());assertEquals(95,h.current(ResourceType.STAMINA));assertEquals(1,h.resourceWrites);assertEquals(1,h.cooldownSaves);
            assertEquals("COOLDOWN_ACTIVE",h.cast().code());assertEquals(1,h.swings);assertEquals(1,h.resourceWrites);
            assertTrue(h.trace().stream().anyMatch(r->r.eventType()==RpgTraceEventType.EXECUTOR_DISPATCH));
        }
    }
    @Test void connectedMithrilStaffCommitsOneTargetlessFireBoltAndExpiryIsPaid()throws Exception{
        var h=new Stage13ConnectedCastingCorrectionTest.H("fire_bolt");
        h.held=HytaleEquipmentAdapter.describe("Weapon_Staff_Mithril",Stage13ConnectedCastingCorrectionTest.installedTags("Weapon_Staff_Mithril"));
        assertEquals("STAFF",h.held.weaponKind());assertEquals(20,h.held.power().magicPower());assertNull(h.held.power().weaponPower());
        var result=h.cast();assertTrue(result.committed(),result.toString());assertNotNull(h.projectile);assertNull(h.last().target().entityId());
        assertEquals(20,h.last().snapshot().basePower());assertEquals(92,h.current(ResourceType.MANA));assertEquals(1,h.resourceWrites);assertEquals(1,h.cooldownSaves);
        assertEquals("COOLDOWN_ACTIVE",h.cast().code());assertTrue(h.projectile.observe(2,new Vec3(0,0,24)).expired());
        assertTrue(h.projectiles.onForwardTermination(h.projectile,"MAX_RANGE",new Vec3(0,0,24)));assertFalse(h.projectiles.onForwardTermination(h.projectile,"MAX_RANGE",new Vec3(0,0,24)));
        assertEquals(92,h.current(ResourceType.MANA));assertEquals(1,h.resourceWrites);assertEquals(1,h.cooldownSaves);assertTrue(h.projectile.hitTargets().isEmpty());
        assertEquals(0,h.b.service().masteryXp(h.actor,"fire_bolt"));assertTrue(h.trace().stream().noneMatch(r->r.eventType()==RpgTraceEventType.DAMAGE_APPLIED));
    }
    @Test void expandedItemsRetainWrongEquipmentAndResourceGuards(){
        var staff=HytaleEquipmentAdapter.describe("Weapon_Staff_Mithril",tags("Staff"));var sword=HytaleEquipmentAdapter.describe("Weapon_Longsword_Flame",tags("Longsword"));
        for(String skill:List.of("quick_slash","fire_bolt")){
            var wrong=new Stage13ConnectedCastingCorrectionTest.H(skill);wrong.held=skill.equals("quick_slash")?staff:sword;
            assertEquals("INVALID_MAIN_HAND",wrong.cast().code());assertEquals(0,wrong.resourceWrites);
            var poor=new Stage13ConnectedCastingCorrectionTest.H(skill);poor.held=skill.equals("quick_slash")?sword:staff;
            poor.current.put(skill.equals("quick_slash")?ResourceType.STAMINA:ResourceType.MANA,0d);
            assertEquals("INSUFFICIENT_RESOURCE",poor.cast().code());assertEquals(0,poor.resourceWrites);assertEquals(0,poor.cooldownSaves);
        }
    }
    @Test void everyExpandedEntryResolvesExactTagsAndMatchesInstalledLeafOrExplicitReferencePolicy()throws Exception{
        assertEquals(197,REGISTRY.all().size());
        try(var zip=new ZipFile(Stage13ConnectedCastingCorrectionTest.ASSETS.toFile())){
            for(var e:REGISTRY.all()){
                var nativeItem=HytaleEquipmentAdapter.describe(e.itemId(),tags(e.nativeFamily()));
                assertEquals(e.kind(),nativeItem.weaponKind(),e.itemId());assertEquals(e.descriptor(),nativeItem.power(),e.itemId());
                JsonElement leaf=json(zip,e.sourceAsset());
                if(e.selectionPolicy().equals("NATIVE_UNCHARGED_BASE")){
                    assertFalse(e.sourceProperty().isBlank());assertFalse(e.sourceProperty().matches(".*(Charged|Signature|Strength_[1-9]).*"),e.itemId());
                    for(String part:e.sourceProperty().split("/"))leaf=leaf.isJsonArray()?leaf.getAsJsonArray().get(Integer.parseInt(part)):leaf.getAsJsonObject().get(part);
                    assertNotNull(leaf,e.itemId());assertEquals(e.basePower(),leaf.getAsDouble(),e.itemId());
                }else{assertTrue(Set.of("STAFF","WAND","SPELLBOOK","SHIELD").contains(e.kind()));assertEquals(20,e.basePower());assertEquals("",e.sourceProperty());}
                assertTrue(REGISTRY.resolve(e.itemId(),Map.of("Family",new String[]{e.nativeFamily()})).isEmpty());
                assertTrue(REGISTRY.resolve(e.itemId(),tags("NotTheAuditedFamily")).isEmpty());
            }
        }
    }
    @Test void historicalAuditedValuesRemainUnchangedAndUnknownItemsCannotInferTierPower(){
        for(var old:NativeItemPowerRegistry.loadCanonical().all()){
            var now=REGISTRY.find(old.itemId()).orElseThrow();assertEquals(old.basePower(),now.basePower(),old.itemId());assertEquals(old.kind(),now.kind());
        }
        assertTrue(REGISTRY.find("Weapon_Longsword_Flame_Copy").isEmpty());
        var unknown=HytaleEquipmentAdapter.describe("Weapon_Longsword_Flame_Copy",tags("Longsword"));assertNull(unknown.power().weaponPower());
        assertEquals("UNKNOWN",HytaleEquipmentAdapter.describe("Weapon_Longsword_Flame",tags("Staff")).weaponKind());
        assertTrue(REGISTRY.find("Weapon_Sword_Steel").isEmpty(),"Native template has no uncharged base; do not guess tier power");
    }
}
