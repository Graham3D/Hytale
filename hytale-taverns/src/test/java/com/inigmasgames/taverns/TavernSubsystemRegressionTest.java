package com.inigmasgames.taverns;

import com.hypixel.hytale.protocol.GameMode;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

final class TavernSubsystemRegressionTest {
    @TempDir Path directory;

    @Test
    void allEstablishedCoreTypesAndResourcesRemainAvailable() {
        assertEquals(CoreType.TAVERN, CoreDefinitions.TAVERN.type());
        assertEquals(CoreType.KITCHEN, CoreDefinitions.KITCHEN.type());
        assertEquals(CoreType.BEDROOM, CoreDefinitions.BEDROOM.type());
        assertEquals(21L * 21L * 5L, CoreDefinitions.TAVERN.startingVolume());
        assertEquals(13L * 10L * 5L, CoreDefinitions.KITCHEN.startingVolume());
        assertEquals(7L * 5L * 5L, CoreDefinitions.BEDROOM.startingVolume());

        for (String resource : new String[] {
                "Server/Item/Items/Core/Core_Tavern.json",
                "Server/Item/Items/Core/Core_Kitchen.json",
                "Server/Item/Items/Core/Core_Bedroom.json",
                "Server/Drops/Core/Core_Tavern.json",
                "Server/Drops/Core/Core_Kitchen.json",
                "Server/Drops/Core/Core_Bedroom.json",
                "Server/NPC/Roles/Taverns/Tavern_Patron.json",
                "Server/Item/Items/Furniture/Tavern/Furniture_Tavern_Service.json",
                "prepared_foods.json",
                "comfort_registry.json"
        }) {
            assertNotNull(getClass().getClassLoader().getResource(resource), resource);
        }
    }

    @Test
    void schemaThreeRoundTripPreservesTavernRoomsStatusAndPaidInvestment() {
        ArrayList<Throwable> errors = new ArrayList<>();
        TavernRepository repository = new TavernRepository(directory, ignored -> { }, errors::add);
        UUID tavernId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        TavernRecord tavern = new TavernRecord(tavernId, worldId, ownerId, TavernStatus.OPEN);
        CoreRecord primary = expanded(CoreRecord.create(UUID.randomUUID(), tavernId, CoreDefinitions.TAVERN, worldId, 0, 64, 0), 8, 6);
        CoreRecord kitchen = expanded(CoreRecord.create(UUID.randomUUID(), tavernId, CoreDefinitions.KITCHEN, worldId, -4, 64, 0), 3, 3);
        CoreRecord bedroom = expanded(CoreRecord.create(UUID.randomUUID(), tavernId, CoreDefinitions.BEDROOM, worldId, 5, 64, 0), 2, 1);

        repository.create(tavern, primary);
        repository.addCore(kitchen);
        repository.addCore(bedroom);
        assertTrue(errors.isEmpty(), () -> "save errors: " + errors);

        TavernRepository reloaded = new TavernRepository(directory, ignored -> { }, errors::add);
        reloaded.load();
        assertTrue(errors.isEmpty(), () -> "reload errors: " + errors);
        assertEquals(tavern, reloaded.findById(tavernId).orElseThrow());
        assertEquals(3, reloaded.findCoresByTavern(tavernId).size());
        assertEquals(primary, reloaded.findCoreById(primary.coreId()).orElseThrow());
        assertEquals(kitchen, reloaded.findCoreById(kitchen.coreId()).orElseThrow());
        assertEquals(bedroom, reloaded.findCoreById(bedroom.coreId()).orElseThrow());
        assertEquals("3", readProperties(directory.resolve("taverns.properties")).getProperty("schema.version"));
    }

    @Test
    void legacySchemasMigrateToThreeWithBackupsAndInvestmentIntact() throws Exception {
        UUID tavernId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Cuboid bounds = Cuboid.normalized(-10, 64, -10, 12, 70, 14);
        Properties schemaOne = new Properties();
        String tavernRoot = "tavern." + tavernId + ".";
        schemaOne.setProperty("schema.version", "1");
        schemaOne.setProperty(tavernRoot + "world", worldId.toString());
        schemaOne.setProperty(tavernRoot + "owner", ownerId.toString());
        schemaOne.setProperty(tavernRoot + "core", "1,65,2");
        schemaOne.setProperty(tavernRoot + "bounds", bounds.encode());
        schemaOne.setProperty(tavernRoot + "expansionUnits", "37");
        writeProperties(directory.resolve("taverns.properties"), schemaOne);

        ArrayList<Throwable> errors = new ArrayList<>();
        TavernRepository repository = new TavernRepository(directory, ignored -> { }, errors::add);
        repository.load();
        CoreRecord migrated = repository.findPrimaryCore(tavernId).orElseThrow();
        assertTrue(errors.isEmpty(), () -> "schema-1 migration errors: " + errors);
        assertEquals(bounds, migrated.bounds());
        assertEquals(37, migrated.expansionUnits());
        assertEquals(37, migrated.paidExpansionUnits());
        assertTrue(Files.exists(directory.resolve("taverns.properties.schema1.bak")));
        assertEquals("3", readProperties(directory.resolve("taverns.properties")).getProperty("schema.version"));

        Path schemaTwoDirectory = directory.resolve("schema-two");
        Files.createDirectories(schemaTwoDirectory);
        UUID coreId = UUID.randomUUID();
        Properties schemaTwo = new Properties();
        schemaTwo.setProperty("schema.version", "2");
        schemaTwo.setProperty(tavernRoot + "world", worldId.toString());
        schemaTwo.setProperty(tavernRoot + "owner", ownerId.toString());
        schemaTwo.setProperty(tavernRoot + "status", TavernStatus.CLOSED.name());
        String coreRoot = "core." + coreId + ".";
        schemaTwo.setProperty(coreRoot + "tavern", tavernId.toString());
        schemaTwo.setProperty(coreRoot + "type", CoreType.TAVERN.name());
        schemaTwo.setProperty(coreRoot + "world", worldId.toString());
        schemaTwo.setProperty(coreRoot + "position", "1,65,2");
        schemaTwo.setProperty(coreRoot + "bounds", bounds.encode());
        schemaTwo.setProperty(coreRoot + "expansionUnits", "19");
        writeProperties(schemaTwoDirectory.resolve("taverns.properties"), schemaTwo);

        TavernRepository schemaTwoRepository = new TavernRepository(schemaTwoDirectory, ignored -> { }, errors::add);
        schemaTwoRepository.load();
        CoreRecord migratedTwo = schemaTwoRepository.findCoreById(coreId).orElseThrow();
        assertTrue(errors.isEmpty(), () -> "schema-2 migration errors: " + errors);
        assertEquals(19, migratedTwo.expansionUnits());
        assertEquals(19, migratedTwo.paidExpansionUnits());
        assertTrue(Files.exists(schemaTwoDirectory.resolve("taverns.properties.schema2.bak")));
    }

    @Test
    void specializedRoomsStayInsidePrimaryAndCannotOverlap() {
        ArrayList<Throwable> errors = new ArrayList<>();
        TavernRepository repository = new TavernRepository(directory, ignored -> { }, errors::add);
        UUID tavernId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        CoreRecord primary = CoreRecord.create(UUID.randomUUID(), tavernId, CoreDefinitions.TAVERN, worldId, 0, 64, 0)
                .withBounds(Cuboid.normalized(-20, 60, -20, 20, 80, 20), 0, 0);
        repository.create(new TavernRecord(tavernId, worldId, UUID.randomUUID(), TavernStatus.CLOSED), primary);
        CoreValidator validator = new CoreValidator(repository);

        CoreRecord kitchen = CoreRecord.create(UUID.randomUUID(), tavernId, CoreDefinitions.KITCHEN, worldId, -5, 64, 0);
        assertTrue(validator.validate(kitchen).isEmpty());
        repository.addCore(kitchen);

        CoreRecord overlappingBedroom = CoreRecord.create(UUID.randomUUID(), tavernId, CoreDefinitions.BEDROOM, worldId, -3, 64, 0);
        assertEquals("Specialized Core volumes cannot overlap one another.", validator.validate(overlappingBedroom).orElseThrow());

        CoreRecord outsideBedroom = CoreRecord.create(UUID.randomUUID(), tavernId, CoreDefinitions.BEDROOM, worldId, 40, 64, 0);
        assertEquals("A specialized Core must remain fully inside its Tavern Core.", validator.validate(outsideBedroom).orElseThrow());
        assertTrue(errors.isEmpty(), () -> "repository errors: " + errors);
    }

    @Test
    void expansionAndRefundAccountingPreservesPaidOnlyContract() {
        UUID id = UUID.randomUUID();
        UUID tavern = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        CoreRecord record = expanded(CoreRecord.create(id, tavern, CoreDefinitions.TAVERN, world, 0, 64, 0), 5, 3);

        CoreModeManager.ExpansionTransfer growth = CoreModeManager.planExpansionTransfer(record, 8, GameMode.Adventure);
        assertEquals(3, growth.unitDifference());
        assertEquals(3, growth.shardTransfer());
        assertEquals(6, growth.paidExpansionUnits());

        CoreModeManager.ExpansionTransfer shrink = CoreModeManager.planExpansionTransfer(record, 1, GameMode.Adventure);
        assertEquals(-4, shrink.unitDifference());
        assertEquals(-3, shrink.shardTransfer());
        assertEquals(0, shrink.paidExpansionUnits());

        CoreModeManager.ExpansionTransfer creativeGrowth = CoreModeManager.planExpansionTransfer(record, 8, GameMode.Creative);
        assertEquals(3, creativeGrowth.unitDifference());
        assertEquals(0, creativeGrowth.shardTransfer());
        assertEquals(3, creativeGrowth.paidExpansionUnits());
    }

    private static CoreRecord expanded(CoreRecord source, int units, int paidUnits) {
        return source.withBounds(source.bounds(), units, paidUnits);
    }

    private static Properties readProperties(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void writeProperties(Path path, Properties properties) throws Exception {
        try (Writer writer = Files.newBufferedWriter(path)) {
            properties.store(writer, "fixture");
        }
    }
}
