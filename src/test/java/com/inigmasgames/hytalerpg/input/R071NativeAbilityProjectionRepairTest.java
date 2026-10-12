package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for Reset/rejoin/re-equip stale native HUD projections. */
class R071NativeAbilityProjectionRepairTest {
    private static final short SLOT = NativeAbilityProjectionService.ABILITY2_PRIMARY_INDEX;
    private static final String SPARK = "RPG_Ability_Charged_Bolt";
    private static final String FOREIGN = "Weapon_Sword_Iron";
    private static final String SPIRIT = "RPG_Ability_Spirit_Shield";
    private static final String BLIZZARD = "RPG_Ability_Blizzard";
    private static final String FIRE_BOLT = "RPG_Ability_Fire_Bolt";
    private static HytaleAssetStore<String, Item, DefaultAssetMap<String, Item>> store;

    @BeforeAll static void nativeItemFixture() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        Map<String, Item> items = Map.of(SPARK, new Item(SPARK), FOREIGN, new Item(FOREIGN),
                SPIRIT,new Item(SPIRIT),BLIZZARD,new Item(BLIZZARD),FIRE_BOLT,new Item(FIRE_BOLT));
        var builder = HytaleAssetStore.builder(Item.class, new DefaultAssetMap<String, Item>(items))
                .setPath("Item/Items").setCodec(Item.CODEC).setKeyFunction(Item::getId);
        store = new HytaleAssetStore<>(builder) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        AssetRegistry.register(store);
    }

    @AfterAll static void releaseFixture() { if (store != null) AssetRegistry.unregister(store); }

    @Test void authoritativeProjectionCanClearAndReinstallOwnedAbilityStack() {
        var container = new InventoryComponent.AbilitySlots(
                InventoryComponent.DEFAULT_ABILITIES_CAPACITY).getInventory();
        var spark = new ItemStack(SPARK);

        assertTrue(NativeAbilityProjectionService.writeOwnedProjection(container, SLOT, SPARK, spark));
        assertEquals(SPARK, container.getItemStack(SLOT).getItemId());

        assertTrue(NativeAbilityProjectionService.clearOwnedProjection(container, SLOT));
        assertTrue(ItemStack.isEmpty(container.getItemStack(SLOT)));

        assertTrue(NativeAbilityProjectionService.writeOwnedProjection(
                container, SLOT, SPARK, new ItemStack(SPARK)));
        assertEquals(SPARK, container.getItemStack(SLOT).getItemId());
    }

    @Test void authoritativeProjectionNeverClearsOrReplacesForeignNativeRune() {
        var container = new InventoryComponent.AbilitySlots(
                InventoryComponent.DEFAULT_ABILITIES_CAPACITY).getInventory();
        var foreign = new ItemStack(FOREIGN);
        assertTrue(container.setItemStackForSlot(SLOT, foreign, false).succeeded());

        assertFalse(NativeAbilityProjectionService.clearOwnedProjection(container, SLOT));
        assertFalse(NativeAbilityProjectionService.writeOwnedProjection(
                container, SLOT, SPARK, new ItemStack(SPARK)));
        assertEquals(FOREIGN, container.getItemStack(SLOT).getItemId());
    }

    @Test void savedChapter1AssignmentsProjectReassignClearAndDispatchOnce() throws Exception {
        var player=java.util.UUID.randomUUID();
        var state=com.inigmasgames.hytalerpg.progress.RpgPlayerState.create(player);
        state.skill(com.inigmasgames.hytalerpg.domain.SkillSlot.SKILL01,
                new com.inigmasgames.hytalerpg.domain.SkillId("spirit_shield"));
        state.skill(com.inigmasgames.hytalerpg.domain.SkillSlot.SKILL02,
                new com.inigmasgames.hytalerpg.domain.SkillId("blizzard"));
        var catalog=com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical();
        var compatibility=new com.inigmasgames.hytalerpg.links.CompatibilityService();
        var graph=new com.inigmasgames.hytalerpg.links.RpgLinkGraphService(catalog,compatibility);
        var repository=new com.inigmasgames.hytalerpg.progress.RpgPlayerStateRepository(){
            public LoadResult load(java.util.UUID id){return new LoadResult(state.copy(),true,false,
                    state.schemaVersion,java.util.List.of());}
            public void save(com.inigmasgames.hytalerpg.progress.RpgPlayerState value){}
        };
        try(var loadouts=new com.inigmasgames.hytalerpg.progress.RpgLoadoutService(catalog,repository,graph,
                new com.inigmasgames.hytalerpg.links.LinkCompiler(catalog,graph,compatibility),
                new com.inigmasgames.hytalerpg.progress.OwnershipEntitlementPolicy(true),ignored->{})){
            loadouts.preload(player).toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS);
            var slots=new InventoryComponent.AbilitySlots(InventoryComponent.DEFAULT_ABILITIES_CAPACITY);
            try(var projection=new NativeAbilityProjectionService(loadouts,
                    com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles.loadCanonical(catalog),
                    ignored->{},loadouts::ready)){
                loadouts.addMutationListener(projection::onLoadoutMutation);
                projection.install(player,slots);
                assertEquals(SPIRIT,slots.getInventory().getItemStack((short)0).getItemId());
                assertEquals(BLIZZARD,slots.getInventory().getItemStack((short)3).getItemId());
                assertEquals("PROJECTED",projection.projectionResult(player,
                        com.inigmasgames.hytalerpg.domain.SkillSlot.SKILL01));
                var adapter=new HytaleAbilitySkillInputAdapter();adapter.useNativeExecution();
                Object chain=new Object();
                adapter.acceptNativeExecution(player,com.hypixel.hytale.protocol.InteractionType.Ability2,
                        42,chain,SPIRIT,0);
                adapter.acceptNativeExecution(player,com.hypixel.hytale.protocol.InteractionType.Ability2,
                        42,chain,SPIRIT,0);
                var requests=new java.util.ArrayList<HytaleAbilitySkillInputAdapter.Request>();
                assertEquals(1,adapter.drainFor(player,requests::add,5));
                assertEquals(com.inigmasgames.hytalerpg.domain.SkillSlot.SKILL01,requests.getFirst().slot());
                assertEquals("Ability2",requests.getFirst().action());
                adapter.acceptNativeExecution(player,com.hypixel.hytale.protocol.InteractionType.Ability3,
                        43,new Object(),BLIZZARD,3);
                adapter.acceptNativeExecution(player,com.hypixel.hytale.protocol.InteractionType.Ability4,
                        44,new Object(),BLIZZARD,3);
                requests.clear();
                assertEquals(1,adapter.drainFor(player,requests::add,5));
                assertEquals(com.inigmasgames.hytalerpg.domain.SkillSlot.SKILL02,requests.getFirst().slot());
                loadouts.equipSkill(player,com.inigmasgames.hytalerpg.domain.SkillSlot.SKILL01,
                        new com.inigmasgames.hytalerpg.domain.SkillId("fire_bolt"));
                assertEquals(FIRE_BOLT,slots.getInventory().getItemStack((short)0).getItemId());
                loadouts.unequipSkill(player,com.inigmasgames.hytalerpg.domain.SkillSlot.SKILL02);
                assertTrue(ItemStack.isEmpty(slots.getInventory().getItemStack((short)3)));
                assertEquals("CLEARED",projection.projectionResult(player,
                        com.inigmasgames.hytalerpg.domain.SkillSlot.SKILL02));
            }
        }
    }
}
