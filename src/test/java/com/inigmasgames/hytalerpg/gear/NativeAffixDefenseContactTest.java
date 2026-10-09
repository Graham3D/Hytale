package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.container.EmptyItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.ResistanceModifier;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Native effect codec and the installed FilterDamage modifier owner, without a world. */
class NativeAffixDefenseContactTest {
    private static final GearCatalog CATALOG = GearCatalog.load();
    private static final GearAffixQaSuite QA = new GearAffixQaSuite(CATALOG);
    private static final Map<RpgAttribute,Integer> STATS = Map.of(RpgAttribute.STR,500,
            RpgAttribute.DEX,500,RpgAttribute.INT,500,RpgAttribute.WIS,500,RpgAttribute.LUCK,500);
    private static final Set<String> CAPABILITIES = CATALOG.affixes().stream()
            .map(GearCatalog.Affix::id).collect(Collectors.toUnmodifiableSet());
    private static final Path EFFECTS = Path.of("src/main/resources/Server/Entity/Effects/RPG/Gear");

    private static GearInstance item(String id, String baseId, boolean rolled) {
        var base = CATALOG.base(baseId);
        var definition = CATALOG.affix(id);
        assertTrue(new GearBindings().require(baseId).mapped());
        assertTrue(GearDropGenerator.eligible(definition,base));
        var fixture = QA.fixtures().stream().filter(f -> f.fixtureId().equals("ab-"+id.toLowerCase()+"-affixed"))
                .findFirst().orElseThrow();
        var authored = QA.preview(fixture).affixes().getFirst();
        var roll = new GearInstance.AffixRoll(id,definition.side(),definition.exclusionGroup(),
                authored.tier(),authored.value(),new GearRequirements.Gate(1,Map.of()),
                definition.name(),definition.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,rolled?List.of(roll):List.of(),BigDecimal.ZERO);
    }

    private static GearEquipmentResolution.Result resolve(GearInstance item, boolean intact, boolean correctSlot) {
        return GearEquipmentResolution.resolve(99,STATS,
                List.of(new GearEquipmentResolution.Candidate(item,true,intact,correctSlot)),CAPABILITIES);
    }

    private static int projectedTenth(GearEquipmentResolution.Result result, GearInstance item, int level) {
        return HytaleGearEquipment.nativeProtectionTenth(result.effects().snapshot(),level);
    }

    @Test void wa069NativeFilterInput() throws Exception { check("WA-069"); }
    @Test void wa070NativeFilterInput() throws Exception { check("WA-070"); }
    @Test void wa071NativeFilterInput() throws Exception { check("WA-071"); }
    @Test void ga159NativeFilterInput() throws Exception { check("GA-159"); }
    @Test void ga160NativeFilterInput() throws Exception { check("GA-160"); }

    private static void check(String id) throws Exception {
        try (var fixture = NativeAssetTestFixtures.open()) {
            fixture.loadDamageCauses();
            var storeField = NativeAssetTestFixtures.class.getDeclaredField("effectStore");
            storeField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var store = (HytaleAssetStore<String,EntityEffect,?>)storeField.get(fixture);
            // EntityEffect caches its first registered map across fixture lifetimes.
            Object assetMap = EntityEffect.getAssetMap();
            var seed = assetMap.getClass().getDeclaredMethod("seed",Map.class);
            seed.setAccessible(true);
                String base = id.startsWith("WA")?"gm.shield_iron.nm":"gm.plate_iron.chest.nm";
                var rolled = item(id,base,true);
                var plain = item(id,base,false);
                var positive = resolve(rolled,true,true);
                var control = resolve(plain,true,true);
                assertEquals(List.of(rolled),positive.validItems(),id);
                assertEquals(List.of(plain),control.validItems(),id);
                int changed = projectedTenth(positive,rolled,60);
                int ordinary = projectedTenth(control,plain,60);
                assertTrue(changed>ordinary,id+" projected protection");
                assertEquals(0,projectedTenth(resolve(rolled,false,true),rolled,60),
                        id+" broken excludes item");
                assertEquals(0,projectedTenth(resolve(rolled,true,false),rolled,60),id+" wrong slot");
                assertEquals("BROKEN",resolve(rolled,false,true).rejected().get(rolled.identity()),id);
                assertEquals("WRONG_SLOT",resolve(rolled,true,false).rejected().get(rolled.identity()),id);
                var nativeChanged = nativePercent(store,assetMap,seed,fixture,changed,"Physical");
                var nativeOrdinary = nativePercent(store,assetMap,seed,fixture,ordinary,"Physical");
                assertTrue(nativeChanged>nativeOrdinary,id+" native Physical filter input");
                assertEquals(nativeChanged,nativePercent(store,assetMap,seed,fixture,changed,"Projectile"),1e-7,id);
        }
    }

    private static float nativePercent(HytaleAssetStore<String,EntityEffect,?> store,Object assetMap,
            java.lang.reflect.Method seed,NativeAssetTestFixtures fixture,int tenth,String cause) throws Exception {
        String name = "RPG_Gear_Protection_"+tenth;
        var path = EFFECTS.resolve(name+".json");
        assertTrue(Files.isRegularFile(path),name+" missing projection asset");
        var effect = store.decode("NativeOfflineQualification",name,BsonDocument.parse(Files.readString(path)));
        assertNotNull(effect,name+" native codec");
        seed.invoke(assetMap,Map.of(name,effect));
        DamageCause nativeCause = fixture.damageCause(cause);
        ResistanceModifier[] modifiers = effect.getDamageResistanceValues().get(nativeCause);
        assertNotNull(modifiers,name+" / "+cause);
        assertEquals(1,modifiers.length);
        assertEquals(ResistanceModifier.ResistanceCalculationType.PERCENT,modifiers[0].getCalculationType());
        assertEquals(tenth/1000f,modifiers[0].getAmount(),1e-7,name+" projected native amount");
        var active = new EffectControllerComponent();
        int index = EntityEffect.getAssetMap().getIndex(name);
        assertTrue(index>=0,name);
        active.addActiveEntityEffects(new ActiveEntityEffect[]{
                new ActiveEntityEffect(name,index,2f,false,null,false)});
        var nativeMap = DamageSystems.ArmorDamageReduction.getResistanceModifiers(
                null,EmptyItemContainer.INSTANCE,false,active);
        var nativeEntry = nativeMap.get(nativeCause);
        assertNotNull(nativeEntry,name+" native FilterDamage lookup");
        var multiplier = nativeEntry.getClass().getDeclaredField("multiplierModifier");
        multiplier.setAccessible(true);
        assertEquals(modifiers[0].getAmount(),multiplier.getFloat(nativeEntry),1e-7,name);
        return multiplier.getFloat(nativeEntry);
    }
}
