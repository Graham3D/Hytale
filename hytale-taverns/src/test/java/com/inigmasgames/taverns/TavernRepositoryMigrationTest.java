package com.inigmasgames.taverns;

import com.hypixel.hytale.protocol.GameMode;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;

/** Dependency-free persistence regression test runnable with Java assertions enabled. */
public final class TavernRepositoryMigrationTest {
    private TavernRepositoryMigrationTest() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("taverns-migration-test-");
        try {
            UUID tavernId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            UUID worldId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
            UUID ownerId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
            Cuboid bounds = Cuboid.normalized(-10, 64, -10, 12, 70, 14);
            writeSchemaOne(directory, tavernId, worldId, ownerId, bounds, 37);

            ArrayList<Throwable> errors = new ArrayList<>();
            TavernRepository repository = new TavernRepository(directory, ignored -> { }, errors::add);
            repository.load();

            require(errors.isEmpty(), "Migration logged an error: " + errors);
            TavernRecord tavern = repository.findById(tavernId).orElseThrow();
            CoreRecord core = repository.findPrimaryCore(tavernId).orElseThrow();
            require(tavern.ownerId().equals(ownerId), "Owner changed during migration");
            require(tavern.worldId().equals(worldId), "World changed during migration");
            require(tavern.status() == TavernStatus.CLOSED, "Migrated Tavern was not safely closed");
            require(core.bounds().equals(bounds), "Bounds changed during migration");
            require(core.expansionUnits() == 37, "Expansion investment changed during migration");
            require(core.paidExpansionUnits() == 37, "Paid expansion investment changed during migration");
            require(repository.findCoreByPosition(worldId, core.coreX(), core.coreY(), core.coreZ()).isPresent(),
                    "Migrated Core was not added to the spatial index");

            Properties migrated = read(directory.resolve("taverns.properties"));
            require("3".equals(migrated.getProperty("schema.version")), "Schema was not upgraded to 3");
            require("37".equals(migrated.getProperty("core." + core.coreId() + ".paidExpansionUnits")),
                    "Migrated Core did not persist its paid expansion investment");
            require(Files.exists(directory.resolve("taverns.properties.schema1.bak")),
                    "Schema-1 backup was not created");

            TavernRepository reloaded = new TavernRepository(directory, ignored -> { }, errors::add);
            reloaded.load();
            require(errors.isEmpty(), "Reloading schema 3 logged an error: " + errors);
            require(reloaded.findById(tavernId).isPresent(), "Tavern did not survive schema-3 reload");
            require(reloaded.findCoreById(core.coreId()).isPresent(), "Core identity did not survive reload");
            reloaded.updateTavern(reloaded.findById(tavernId).orElseThrow()
                    .withStatus(TavernStatus.OPEN));
            TavernRepository serviceReload = new TavernRepository(directory, ignored -> { }, errors::add);
            serviceReload.load();
            require(serviceReload.findById(tavernId).orElseThrow().status() == TavernStatus.OPEN,
                    "Open-for-Service state did not survive reload");
            deleteTree(directory);
            Files.createDirectories(directory);
            UUID schemaTwoCoreId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
            writeSchemaTwo(directory, tavernId, schemaTwoCoreId, worldId, ownerId, bounds, 273);
            errors.clear();
            TavernRepository schemaTwoRepository = new TavernRepository(directory, ignored -> { }, errors::add);
            schemaTwoRepository.load();
            require(errors.isEmpty(), "Schema-2 migration logged an error: " + errors);
            CoreRecord schemaTwoCore = schemaTwoRepository.findCoreById(schemaTwoCoreId).orElseThrow();
            require(schemaTwoCore.expansionUnits() == 273, "Schema-2 expansion units changed");
            require(schemaTwoCore.paidExpansionUnits() == 273,
                    "Schema-2 expansion investment was not made refundable");
            Properties schemaTwoMigrated = read(directory.resolve("taverns.properties"));
            require("3".equals(schemaTwoMigrated.getProperty("schema.version")),
                    "Schema 2 was not upgraded to schema 3");
            require("273".equals(schemaTwoMigrated.getProperty(
                            "core." + schemaTwoCoreId + ".paidExpansionUnits")),
                    "Schema-2 paid expansion investment was not persisted");
            require(Files.exists(directory.resolve("taverns.properties.schema2.bak")),
                    "Schema-2 backup was not created");

            testExpansionTransfers();
            System.out.println("TavernRepositoryMigrationTest passed");
        } finally {
            deleteTree(directory);
        }
    }

    private static void testExpansionTransfers() {
        CoreDefinition definition = CoreDefinitions.TAVERN;
        CoreRecord base = CoreRecord.create(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                definition,
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                0, 64, 0);
        CoreRecord paidExpansion = base.withBounds(base.bounds(), 30, 20);

        CoreModeManager.ExpansionTransfer spend = CoreModeManager.planExpansionTransfer(
                paidExpansion, 35, GameMode.Adventure);
        require(spend.unitDifference() == 5, "Expansion difference was incorrect");
        require(spend.shardTransfer() == 5, "Adventure expansion did not charge Crystal Shards");
        require(spend.paidExpansionUnits() == 25, "Paid expansion balance did not increase");

        CoreModeManager.ExpansionTransfer refund = CoreModeManager.planExpansionTransfer(
                paidExpansion, 10, GameMode.Adventure);
        require(refund.shardTransfer() == -20, "Contraction did not refund paid Crystal Shards");
        require(refund.paidExpansionUnits() == 0, "Refund did not reduce paid expansion balance");

        CoreRecord partlyPaidExpansion = base.withBounds(base.bounds(), 30, 5);
        CoreModeManager.ExpansionTransfer cappedRefund = CoreModeManager.planExpansionTransfer(
                partlyPaidExpansion, 10, GameMode.Adventure);
        require(cappedRefund.shardTransfer() == -5, "Refund exceeded the paid Crystal Shard balance");
        require(cappedRefund.paidExpansionUnits() == 0, "Capped refund retained a paid balance");

        CoreModeManager.ExpansionTransfer creativeExpansion = CoreModeManager.planExpansionTransfer(
                paidExpansion, 35, GameMode.Creative);
        require(creativeExpansion.shardTransfer() == 0, "Creative expansion charged Crystal Shards");
        require(creativeExpansion.paidExpansionUnits() == 20,
                "Creative expansion changed the paid Crystal Shard balance");

        require(CoreModeManager.projectedShardTotal(27, 5) == 22,
                "Affordable expansion counter did not decrease");
        require(CoreModeManager.projectedShardTotal(27, 35) == -8,
                "Unaffordable expansion counter did not become negative");
        require(CoreModeManager.projectedShardTotal(27, -20) == 47,
                "Refund counter did not increase");
        require(CoreModeManager.counterTone(27, 5) == TavernsHud.CounterTone.WHITE,
                "Affordable expansion counter was not white");
        require(CoreModeManager.counterTone(27, 35) == TavernsHud.CounterTone.RED,
                "Unaffordable expansion counter was not red");
        require(CoreModeManager.counterTone(0, -20) == TavernsHud.CounterTone.GREEN,
                "Refund counter was not green");
    }
    private static void writeSchemaOne(
            Path directory, UUID tavernId, UUID worldId, UUID ownerId, Cuboid bounds, int units) throws Exception {
        Properties properties = new Properties();
        String root = "tavern." + tavernId + ".";
        properties.setProperty("schema.version", "1");
        properties.setProperty(root + "world", worldId.toString());
        properties.setProperty(root + "owner", ownerId.toString());
        properties.setProperty(root + "core", "1,65,2");
        properties.setProperty(root + "bounds", bounds.encode());
        properties.setProperty(root + "expansionUnits", Integer.toString(units));
        try (Writer writer = Files.newBufferedWriter(directory.resolve("taverns.properties"))) {
            properties.store(writer, "schema one fixture");
        }
    }


    private static void writeSchemaTwo(
            Path directory, UUID tavernId, UUID coreId, UUID worldId, UUID ownerId,
            Cuboid bounds, int units) throws Exception {
        Properties properties = new Properties();
        String tavernRoot = "tavern." + tavernId + ".";
        String coreRoot = "core." + coreId + ".";
        properties.setProperty("schema.version", "2");
        properties.setProperty(tavernRoot + "world", worldId.toString());
        properties.setProperty(tavernRoot + "owner", ownerId.toString());
        properties.setProperty(tavernRoot + "status", TavernStatus.CLOSED.name());
        properties.setProperty(coreRoot + "tavern", tavernId.toString());
        properties.setProperty(coreRoot + "type", CoreType.TAVERN.name());
        properties.setProperty(coreRoot + "world", worldId.toString());
        properties.setProperty(coreRoot + "position", "1,65,2");
        properties.setProperty(coreRoot + "bounds", bounds.encode());
        properties.setProperty(coreRoot + "expansionUnits", Integer.toString(units));
        try (Writer writer = Files.newBufferedWriter(directory.resolve("taverns.properties"))) {
            properties.store(writer, "schema two fixture");
        }
    }
    private static Properties read(Path path) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        return properties;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
