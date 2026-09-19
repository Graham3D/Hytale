package com.inigmasgames.taverns;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;

/** Definition, containment, multiplicity, and persistence coverage for specialized Cores. */
public final class CoreFeatureTest {
    private CoreFeatureTest() {
    }

    public static void main(String[] args) throws Exception {
        requireDefinition(CoreDefinitions.KITCHEN, CoreType.KITCHEN, "Core_Kitchen", 13, 10, 5, 650);
        requireDefinition(CoreDefinitions.BEDROOM, CoreType.BEDROOM, "Core_Bedroom", 7, 5, 5, 175);
        require(CoreDefinitions.byItemId("Core_Kitchen").orElseThrow() == CoreDefinitions.KITCHEN,
                "Kitchen item did not resolve to its Core definition");
        require(CoreDefinitions.byItemId("Core_Bedroom").orElseThrow() == CoreDefinitions.BEDROOM,
                "Bedroom item did not resolve to its Core definition");
        require(CoreDefinitions.byType(CoreType.BAR).isEmpty(), "Bar Core was enabled prematurely");

        Path directory = Files.createTempDirectory("taverns-specialized-core-test-");
        try {
            ArrayList<Throwable> errors = new ArrayList<>();
            TavernRepository repository = new TavernRepository(directory, ignored -> { }, errors::add);
            CoreValidator validator = new CoreValidator(repository);
            UUID tavernId = UUID.fromString("10000000-0000-0000-0000-000000000001");
            UUID worldId = UUID.fromString("20000000-0000-0000-0000-000000000002");
            UUID ownerId = UUID.fromString("30000000-0000-0000-0000-000000000003");
            TavernRecord tavern = new TavernRecord(tavernId, worldId, ownerId, TavernStatus.CLOSED);
            CoreRecord primary = CoreRecord.create(
                    UUID.fromString("40000000-0000-0000-0000-000000000004"),
                    tavernId, CoreDefinitions.TAVERN, worldId, 0, 64, 0);
            repository.create(tavern, primary);

            CoreRecord kitchen = CoreRecord.create(
                    UUID.fromString("50000000-0000-0000-0000-000000000005"),
                    tavernId, CoreDefinitions.KITCHEN, worldId, -4, 64, -5);
            CoreRecord bedroomOne = CoreRecord.create(
                    UUID.fromString("60000000-0000-0000-0000-000000000006"),
                    tavernId, CoreDefinitions.BEDROOM, worldId, 6, 64, -8);
            CoreRecord bedroomTwo = CoreRecord.create(
                    UUID.fromString("70000000-0000-0000-0000-000000000007"),
                    tavernId, CoreDefinitions.BEDROOM, worldId, 6, 64, -2);

            require(validator.validate(kitchen).isEmpty(), "Contained Kitchen Core was rejected");
            repository.addCore(kitchen);
            require(repository.findCoreContaining(worldId, CoreType.KITCHEN, -4, 64, -5)
                            .map(CoreRecord::coreId).filter(kitchen.coreId()::equals).isPresent(),
                    "Kitchen workstation containment lookup did not find the Kitchen Core");
            require(validator.validate(bedroomOne).isEmpty(), "First contained Bedroom Core was rejected");
            repository.addCore(bedroomOne);
            require(validator.validate(bedroomTwo).isEmpty(), "Second contained Bedroom Core was rejected");
            repository.addCore(bedroomTwo);

            CoreRecord outsideBedroom = CoreRecord.create(
                    UUID.randomUUID(), tavernId, CoreDefinitions.BEDROOM, worldId, 8, 64, 7);
            require(validator.validate(outsideBedroom).isPresent(),
                    "Bedroom Core extending outside its Tavern was accepted");
            CoreRecord overlappingBedroom = CoreRecord.create(
                    UUID.randomUUID(), tavernId, CoreDefinitions.BEDROOM, worldId, 6, 64, -7);
            require(validator.validate(overlappingBedroom).isPresent(),
                    "Overlapping specialized Core was accepted");
            CoreRecord contractedPrimary = primary.withBounds(
                    Cuboid.normalized(-10, 64, -10, 5, 68, 10), 0, 0);
            require(validator.validate(contractedPrimary).isPresent(),
                    "Tavern Core was allowed to exclude an existing Bedroom Core");

            TavernRepository reloaded = new TavernRepository(directory, ignored -> { }, errors::add);
            reloaded.load();
            require(errors.isEmpty(), "Specialized Core reload logged an error: " + errors);
            require(reloaded.findCoreById(kitchen.coreId()).orElseThrow().type() == CoreType.KITCHEN,
                    "Kitchen Core identity did not survive reload");
            require(reloaded.findCoreById(bedroomOne.coreId()).orElseThrow().type() == CoreType.BEDROOM,
                    "First Bedroom Core identity did not survive reload");
            require(reloaded.findCoreById(bedroomTwo.coreId()).orElseThrow().type() == CoreType.BEDROOM,
                    "Second Bedroom Core identity did not survive reload");
            long bedroomCount = reloaded.findCoresByTavern(tavernId).stream()
                    .filter(core -> core.type() == CoreType.BEDROOM)
                    .count();
            require(bedroomCount == 2, "Multiple Bedroom Cores overwrote one another");
            require(!bedroomOne.coreId().equals(bedroomTwo.coreId()),
                    "Bedroom records did not retain unique room identities");
            System.out.println("CoreFeatureTest passed");
        } finally {
            deleteTree(directory);
        }
    }

    private static void requireDefinition(
            CoreDefinition definition,
            CoreType type,
            String itemId,
            int width,
            int depth,
            int height,
            long volume) {
        require(definition.type() == type, itemId + " has the wrong Core type");
        require(definition.itemId().equals(itemId), itemId + " has the wrong item ID");
        require(definition.startingWidth() == width
                        && definition.startingDepth() == depth
                        && definition.startingHeight() == height,
                itemId + " has the wrong starting dimensions");
        require(definition.startingVolume() == volume, itemId + " has the wrong starting volume");
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
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
