package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.NpcInventoryRepository;
import com.inigmasgames.persistentnpcs.profile.NpcInventoryState;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import sun.misc.Unsafe;

/** Deterministic regressions for R122 canonical equality and hydration rollback. */
public final class R122NpcInventoryPersistenceNormalizationTest {
    private R122NpcInventoryPersistenceNormalizationTest() { }

    public static void main(String[] arguments) throws Exception {
        emptyMetadataRepresentationsCompareCanonically();
        legacyJsonDeserializesCanonically();
        meaningfulMetadataRemainsDistinct();
        jonalithLegacyFixtureMatchesRuntime();
        failedHydrationRollsBackAndCanRetry();
        protectiveRefusalAndStructuredLoggingRemainPresent();
        System.out.println("R122 NPC inventory persistence normalization gate passed.");
    }

    private static void emptyMetadataRepresentationsCompareCanonically() {
        NpcInventoryState.PersistedItemStack baseline = item(null);
        assert item("{}").equals(baseline);
        assert item("{ }").equals(baseline);
        assert item("").equals(baseline);
        assert item(" \t\r\n ").equals(baseline);
        assert item(null).equals(baseline);
    }

    private static void legacyJsonDeserializesCanonically() throws Exception {
        Path file = Files.createTempFile("r122-legacy-inventory", ".json");
        Files.writeString(file, """
                {
                  "schemaVersion": 2,
                  "inventory": [{
                    "slot": 31,
                    "itemId": "Soil_Sand_White",
                    "quantity": 36,
                    "durability": 0.0,
                    "maxDurability": 0.0,
                    "qualityIndex": 0,
                    "metadataJson": "{}",
                    "overrideDroppedItemAnimation": false
                  }]
                }
                """);
        NpcInventoryState state = JsonFiles.read(file, NpcInventoryState.class);
        assert state.inventory().size() == 1;
        assert state.inventory().getFirst().metadataJson() == null
                : "The real JSON load boundary must canonicalize legacy {}";
    }

    private static void meaningfulMetadataRemainsDistinct() {
        String first = "{\"custom\": \"kept\", \"rank\": 7}";
        String second = "{\"custom\": \"different\", \"rank\": 7}";
        assert item(first).equals(item(first));
        assert !item(first).equals(item(second));
        assert item(first).metadataJson().equals(first)
                : "Meaningful metadata serialization must be preserved";
    }

    private static void jonalithLegacyFixtureMatchesRuntime() {
        NpcInventoryState.PersistedItemStack persisted =
                new NpcInventoryState.PersistedItemStack((short) 31,
                        "Soil_Sand_White", 36, 0.0, 0.0, 0, "{}", false);
        NpcInventoryState.PersistedItemStack runtime =
                new NpcInventoryState.PersistedItemStack((short) 31,
                        "Soil_Sand_White", 36, 0.0, 0.0, 0, null, false);
        assert persisted.metadataJson() == null;
        assert persisted.equals(runtime);
        assert persisted.quantity() == 36;
    }

    private static void failedHydrationRollsBackAndCanRetry() throws Exception {
        SimpleItemContainer live = new SimpleItemContainer((short) 40);
        ItemStack item = allocateStack("Soil_Sand_White", 36);
        Method hydrate = NpcInventoryRepository.class.getDeclaredMethod(
                "hydrateRollbackSafe", String.class, List.class, Runnable.class,
                BooleanSupplier.class, Consumer.class);
        hydrate.setAccessible(true);

        for (int attempt = 0; attempt < 2; attempt++) {
            assert live.isEmpty() : "A failed prior hydration poisoned retry " + attempt;
            Runnable mutation = () -> {
                if (!live.setItemStackForSlot((short) 31, item).succeeded()) {
                    throw new AssertionError("Fixture mutation failed");
                }
            };
            try {
                hydrate.invoke(null, "Jonalith", List.of(live), mutation,
                        (BooleanSupplier) () -> false,
                        (Consumer<String>) ignored -> { });
                throw new AssertionError("Failed validation must throw");
            } catch (InvocationTargetException expected) {
                assert expected.getCause() instanceof IllegalStateException;
            }
            assert live.isEmpty()
                    : "Failed hydration must restore exact initially-empty Storage";
        }
    }

    private static void protectiveRefusalAndStructuredLoggingRemainPresent() throws Exception {
        String repository = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/profile/NpcInventoryRepository.java"));
        int divergence = repository.indexOf("if (!runtimeMatches(authored");
        int refusal = repository.indexOf("refusing to overwrite non-empty runtime containers");
        int hydration = repository.indexOf("hydrateRollbackSafe(npcName");
        assert divergence >= 0 && refusal > divergence && hydration > refusal
                : "Non-empty divergent Storage must still fail before hydration";
        assert repository.contains("NPC_INVENTORY_HYDRATION_NORMALIZATION");
        assert repository.contains("NPC_INVENTORY_HYDRATION_VALIDATION");
        assert repository.contains("NPC_INVENTORY_HYDRATION_ROLLBACK");
        assert repository.contains("captureExact") && repository.contains("restoreExact");
    }

    private static NpcInventoryState.PersistedItemStack item(String metadata) {
        return new NpcInventoryState.PersistedItemStack((short) 0,
                "Test_Trade_Good", 1, 0.0, 0.0, 0, metadata, false);
    }

    private static ItemStack allocateStack(String itemId, int quantity) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        ItemStack stack = (ItemStack) unsafe.allocateInstance(ItemStack.class);
        set(stack, "itemId", itemId);
        set(stack, "quantity", quantity);
        set(stack, "qualityIndex", 0);
        return stack;
    }

    private static void set(ItemStack stack, String fieldName, Object value) throws Exception {
        Field field = ItemStack.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(stack, value);
    }
}
