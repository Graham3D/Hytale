package com.inigmasgames.taverns;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Versioned persistence for durable Tavern identities and their physical Core records. */
final class TavernRepository {
    static final int CURRENT_SCHEMA_VERSION = 3;

    private static final String TAVERN_PREFIX = "tavern.";
    private static final String CORE_PREFIX = "core.";

    private final Path dataFile;
    private final Consumer<String> info;
    private final Consumer<Throwable> error;
    private final Map<UUID, TavernRecord> taverns = new LinkedHashMap<>();
    private final CoreRegistry cores = new CoreRegistry();

    TavernRepository(Path dataDirectory, Consumer<String> info, Consumer<Throwable> error) {
        this.dataFile = dataDirectory.resolve("taverns.properties");
        this.info = info;
        this.error = error;
    }

    synchronized void load() {
        if (!Files.exists(dataFile)) {
            taverns.clear();
            cores.replaceAll(java.util.List.of());
            return;
        }

        Properties properties = new Properties();
        try {
            try (Reader reader = Files.newBufferedReader(dataFile)) {
                properties.load(reader);
            }
            int sourceVersion = Integer.parseInt(properties.getProperty("schema.version", "1"));
            if (sourceVersion < 1 || sourceVersion > CURRENT_SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported Tavern schema version " + sourceVersion);
            }

            Map<UUID, TavernRecord> loadedTaverns = new LinkedHashMap<>();
            Map<UUID, CoreRecord> loadedCores = new LinkedHashMap<>();
            if (sourceVersion == 1) {
                loadSchemaOne(properties, loadedTaverns, loadedCores);
            } else {
                loadSchemaTwoOrThree(properties, loadedTaverns, loadedCores, sourceVersion);
            }
            validateLoadedData(loadedTaverns, loadedCores);
            if (sourceVersion < CURRENT_SCHEMA_VERSION) {
                backupBeforeMigration(sourceVersion);
            }

            taverns.clear();
            taverns.putAll(loadedTaverns);
            cores.replaceAll(loadedCores.values());
            info.accept("Loaded " + taverns.size() + " Tavern(s) and " + cores.all().size()
                    + " Core(s) using schema " + sourceVersion + ".");

            if (sourceVersion < CURRENT_SCHEMA_VERSION) {
                save();
                info.accept("Migrated Tavern persistence from schema " + sourceVersion
                        + " to schema " + CURRENT_SCHEMA_VERSION + ".");
            }
        } catch (Exception exception) {
            error.accept(new IOException(
                    "Could not load " + dataFile + "; existing in-memory and on-disk data were left untouched",
                    exception));
        }
    }

    synchronized Collection<TavernRecord> allTaverns() {
        return new ArrayList<>(taverns.values());
    }

    synchronized Collection<CoreRecord> allCores() {
        return cores.all();
    }

    synchronized Optional<TavernRecord> findById(UUID tavernId) {
        return Optional.ofNullable(taverns.get(tavernId));
    }

    synchronized Optional<TavernRecord> findByOwner(UUID ownerId) {
        return taverns.values().stream()
                .filter(record -> record.ownerId().equals(ownerId))
                .findFirst();
    }

    synchronized Optional<CoreRecord> findCoreById(UUID coreId) {
        return cores.findById(coreId);
    }

    synchronized Optional<CoreRecord> findCoreByPosition(UUID worldId, int x, int y, int z) {
        return cores.findAt(worldId, x, y, z);
    }

    synchronized Optional<CoreRecord> findPrimaryCoreContaining(UUID worldId, int x, int y, int z) {
        return cores.findContainingPrimary(worldId, x, y, z);
    }

    synchronized Optional<CoreRecord> findCoreContaining(
            UUID worldId, CoreType type, int x, int y, int z) {
        return cores.findContaining(worldId, type, x, y, z);
    }

    synchronized Optional<CoreRecord> findPrimaryCore(UUID tavernId) {
        return cores.findByTavern(tavernId).stream()
                .filter(core -> core.type().isPrimary())
                .findFirst();
    }

    synchronized Collection<CoreRecord> findCoresByTavern(UUID tavernId) {
        return cores.findByTavern(tavernId);
    }

    synchronized Collection<CoreRecord> findIntersectingCores(
            UUID worldId, Cuboid bounds, UUID ignoredCoreId) {
        return cores.findIntersecting(worldId, bounds, ignoredCoreId);
    }

    synchronized void create(TavernRecord tavern, CoreRecord primaryCore) {
        if (!primaryCore.type().isPrimary() || !primaryCore.tavernId().equals(tavern.tavernId())) {
            throw new IllegalArgumentException("A new Tavern requires its own primary Core");
        }
        if (!primaryCore.worldId().equals(tavern.worldId())) {
            throw new IllegalArgumentException("Tavern and primary Core must be in the same world");
        }
        if (taverns.containsKey(tavern.tavernId()) || cores.findById(primaryCore.coreId()).isPresent()) {
            throw new IllegalStateException("Duplicate Tavern or Core identity");
        }
        if (findByOwner(tavern.ownerId()).isPresent()) {
            throw new IllegalStateException("Owner already has a Tavern");
        }
        taverns.put(tavern.tavernId(), tavern);
        cores.add(primaryCore);
        save();
    }

    synchronized void updateTavern(TavernRecord tavern) {
        if (!taverns.containsKey(tavern.tavernId())) {
            throw new IllegalStateException("Unknown Tavern " + tavern.tavernId());
        }
        taverns.put(tavern.tavernId(), tavern);
        save();
    }

    synchronized void updateCore(CoreRecord core) {
        if (!taverns.containsKey(core.tavernId())) {
            throw new IllegalStateException("Unknown Tavern " + core.tavernId());
        }
        cores.update(core);
        save();
    }

    synchronized void addCore(CoreRecord core) {
        if (!taverns.containsKey(core.tavernId())) {
            throw new IllegalStateException("Unknown Tavern " + core.tavernId());
        }
        cores.add(core);
        save();
    }

    synchronized void removeCore(UUID coreId) {
        if (cores.remove(coreId).isPresent()) {
            save();
        }
    }

    synchronized void removeTavern(UUID tavernId) {
        if (taverns.remove(tavernId) == null) {
            return;
        }
        for (CoreRecord core : new ArrayList<>(cores.findByTavern(tavernId))) {
            cores.remove(core.coreId());
        }
        save();
    }

    private static void loadSchemaOne(
            Properties properties,
            Map<UUID, TavernRecord> loadedTaverns,
            Map<UUID, CoreRecord> loadedCores) {
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(TAVERN_PREFIX) || !key.endsWith(".world")) {
                continue;
            }
            String idText = key.substring(TAVERN_PREFIX.length(), key.length() - ".world".length());
            UUID tavernId = UUID.fromString(idText);
            String root = TAVERN_PREFIX + tavernId + ".";
            UUID worldId = UUID.fromString(required(properties, root + "world"));
            UUID ownerId = UUID.fromString(required(properties, root + "owner"));
            int[] corePosition = decodePosition(required(properties, root + "core"));
            Cuboid bounds = Cuboid.decode(required(properties, root + "bounds"));
            int units = Integer.parseInt(properties.getProperty(root + "expansionUnits", "0"));
            UUID coreId = UUID.nameUUIDFromBytes(
                    ("taverns:primary-core:" + tavernId).getBytes(StandardCharsets.UTF_8));

            TavernRecord tavern = new TavernRecord(tavernId, worldId, ownerId, TavernStatus.CLOSED);
            CoreRecord core = new CoreRecord(
                    coreId, tavernId, CoreType.TAVERN, worldId,
                    corePosition[0], corePosition[1], corePosition[2],
                    bounds, units, units, bounds.intersectedChunks());
            loadedTaverns.put(tavernId, tavern);
            loadedCores.put(coreId, core);
        }
    }

    private static void loadSchemaTwoOrThree(
            Properties properties,
            Map<UUID, TavernRecord> loadedTaverns,
            Map<UUID, CoreRecord> loadedCores,
            int sourceVersion) {
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(TAVERN_PREFIX) || !key.endsWith(".world")) {
                continue;
            }
            String idText = key.substring(TAVERN_PREFIX.length(), key.length() - ".world".length());
            UUID tavernId = UUID.fromString(idText);
            String root = TAVERN_PREFIX + tavernId + ".";
            TavernRecord tavern = new TavernRecord(
                    tavernId,
                    UUID.fromString(required(properties, root + "world")),
                    UUID.fromString(required(properties, root + "owner")),
                    TavernStatus.valueOf(properties.getProperty(root + "status", TavernStatus.CLOSED.name())));
            loadedTaverns.put(tavernId, tavern);
        }

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(CORE_PREFIX) || !key.endsWith(".tavern")) {
                continue;
            }
            String idText = key.substring(CORE_PREFIX.length(), key.length() - ".tavern".length());
            UUID coreId = UUID.fromString(idText);
            String root = CORE_PREFIX + coreId + ".";
            int[] position = decodePosition(required(properties, root + "position"));
            Cuboid bounds = Cuboid.decode(required(properties, root + "bounds"));
            int expansionUnits = Integer.parseInt(properties.getProperty(root + "expansionUnits", "0"));
            int paidExpansionUnits = sourceVersion >= 3
                    ? Integer.parseInt(properties.getProperty(root + "paidExpansionUnits", "0"))
                    : expansionUnits;
            CoreRecord core = new CoreRecord(
                    coreId,
                    UUID.fromString(required(properties, root + "tavern")),
                    CoreType.valueOf(required(properties, root + "type")),
                    UUID.fromString(required(properties, root + "world")),
                    position[0], position[1], position[2],
                    bounds,
                    expansionUnits,
                    paidExpansionUnits,
                    bounds.intersectedChunks());
            loadedCores.put(coreId, core);
        }
    }

    private static void validateLoadedData(
            Map<UUID, TavernRecord> loadedTaverns,
            Map<UUID, CoreRecord> loadedCores) {
        Set<UUID> owners = new java.util.HashSet<>();
        for (TavernRecord tavern : loadedTaverns.values()) {
            if (!owners.add(tavern.ownerId())) {
                throw new IllegalArgumentException("Owner has more than one persisted Tavern: " + tavern.ownerId());
            }
            long primaryCount = loadedCores.values().stream()
                    .filter(core -> core.tavernId().equals(tavern.tavernId()) && core.type().isPrimary())
                    .count();
            if (primaryCount != 1) {
                throw new IllegalArgumentException(
                        "Tavern " + tavern.tavernId() + " must have exactly one primary Core");
            }
        }
        for (CoreRecord core : loadedCores.values()) {
            TavernRecord tavern = loadedTaverns.get(core.tavernId());
            if (tavern == null) {
                throw new IllegalArgumentException("Core references missing Tavern " + core.tavernId());
            }
            if (!core.worldId().equals(tavern.worldId())) {
                throw new IllegalArgumentException("Core and Tavern world mismatch for " + core.coreId());
            }
        }
    }

    private void save() {
        Properties properties = new Properties();
        properties.setProperty("schema.version", Integer.toString(CURRENT_SCHEMA_VERSION));
        for (TavernRecord tavern : taverns.values()) {
            String root = TAVERN_PREFIX + tavern.tavernId() + ".";
            properties.setProperty(root + "world", tavern.worldId().toString());
            properties.setProperty(root + "owner", tavern.ownerId().toString());
            properties.setProperty(root + "status", tavern.status().name());
        }
        for (CoreRecord core : cores.all()) {
            String root = CORE_PREFIX + core.coreId() + ".";
            properties.setProperty(root + "tavern", core.tavernId().toString());
            properties.setProperty(root + "type", core.type().name());
            properties.setProperty(root + "world", core.worldId().toString());
            properties.setProperty(root + "position", encodePosition(core.coreX(), core.coreY(), core.coreZ()));
            properties.setProperty(root + "bounds", core.bounds().encode());
            properties.setProperty(root + "expansionUnits", Integer.toString(core.expansionUnits()));
            properties.setProperty(root + "paidExpansionUnits", Integer.toString(core.paidExpansionUnits()));
            properties.setProperty(root + "chunks", encodeChunks(core.intersectedChunks()));
        }

        Path temporary = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                properties.store(writer, "Taverns persistent data - schema " + CURRENT_SCHEMA_VERSION);
            }
            try {
                Files.move(temporary, dataFile,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            error.accept(new IOException("Could not save " + dataFile, exception));
        }
    }

    private void backupBeforeMigration(int sourceVersion) throws IOException {
        Path backup = dataFile.resolveSibling(dataFile.getFileName() + ".schema" + sourceVersion + ".bak");
        if (!Files.exists(backup)) {
            Files.copy(dataFile, backup);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing property " + key);
        }
        return value;
    }

    private static int[] decodePosition(String encoded) {
        String[] parts = encoded.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid Core position: " + encoded);
        }
        return new int[] {
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        };
    }

    private static String encodePosition(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static String encodeChunks(Set<Long> chunks) {
        return chunks.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("");
    }
}
