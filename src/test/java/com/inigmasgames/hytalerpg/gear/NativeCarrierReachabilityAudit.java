package com.inigmasgames.hytalerpg.gear;

import org.bson.BsonDocument;
import org.bson.BsonValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Conservative static reachability walk over the installed native graphs and local overrides. */
final class NativeCarrierReachabilityAudit implements AutoCloseable {
    private static final String ROOT_PREFIX = "Server/Item/RootInteractions/";
    private static final String INTERACTION_PREFIX = "Server/Item/Interactions/";
    private static final String PROJECTILE_PREFIX = "Server/ProjectileConfigs/";
    private final ZipFile zip;
    private final Map<String, Source> roots = new HashMap<>();
    private final Map<String, Source> interactions = new HashMap<>();
    private final Map<String, Source> projectiles = new HashMap<>();
    private final Map<String, BsonDocument> decoded = new HashMap<>();
    private Map<String, BsonValue> variables;
    private final Set<String> visited = new HashSet<>();
    private final Set<String> managedLeaves = new HashSet<>();
    private String carrier;

    private record Source(Path file, ZipEntry entry) {}

    NativeCarrierReachabilityAudit(Path serverRoot) throws IOException {
        var installed = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "Hytale",
                "install", "pre-release", "package", "game", "latest", "Assets.zip");
        zip = new ZipFile(installed.toFile());
        for (var entry : java.util.Collections.list(zip.entries())) {
            var name = entry.getName();
            if (!name.endsWith(".json")) continue;
            var index = name.startsWith(ROOT_PREFIX) ? roots : name.startsWith(INTERACTION_PREFIX)
                    ? interactions : name.startsWith(PROJECTILE_PREFIX) ? projectiles : null;
            if (index != null) index.put(id(name), new Source(null, entry));
        }
        indexLocal(Path.of("src/main/resources/Server"));
        if (!serverRoot.toAbsolutePath().normalize().equals(Path.of("src/main/resources/Server").toAbsolutePath().normalize()))
            indexLocal(serverRoot);
    }

    private static String id(String name) {
        return name.substring(name.lastIndexOf('/') + 1, name.length() - 5);
    }
    private void indexLocal(Path root) throws IOException {
        for (var section : Map.of("Item/RootInteractions", roots, "Item/Interactions", interactions,
                "ProjectileConfigs", projectiles).entrySet()) {
            var folder = root.resolve(section.getKey());
            if (!Files.isDirectory(folder)) continue;
            try (var files = Files.walk(folder)) {
                for (var file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList())
                    section.getValue().put(id(file.getFileName().toString()), new Source(file, null));
            }
        }
    }
    private BsonDocument document(Map<String, Source> index, String id) throws IOException {
        var source = index.get(id);
        if (source == null) throw new IllegalStateException(carrier + " references absent native asset " + id);
        var cacheKey = System.identityHashCode(index) + ":" + id;
        var cached = decoded.get(cacheKey);
        if (cached != null) return cached;
        String json;
        if (source.file() != null) json = Files.readString(source.file());
        else try (var input = zip.getInputStream(source.entry())) {
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var parsed = BsonDocument.parse(json);
        decoded.put(cacheKey, parsed);
        return parsed;
    }

    Set<String> inspect(Path itemPath) throws IOException {
        carrier = itemPath.getFileName().toString();
        visited.clear();
        managedLeaves.clear();
        var item = BsonDocument.parse(Files.readString(itemPath));
        variables = item.containsKey("InteractionVars") ? new HashMap<>(item.getDocument("InteractionVars")) : Map.of();
        for (var value : item.getDocument("Interactions").values()) {
            if (!value.isString()) continue;
            visitRoot(value.asString().getValue());
        }
        if (managedLeaves.isEmpty()) throw new IllegalStateException(carrier + " has no reachable managed native leaf");
        return Set.copyOf(managedLeaves);
    }

    private void visitRoot(String id) throws IOException {
        if (!visited.add("root:" + id)) return;
        walk(document(roots, id), "root:" + id);
    }
    private void visitInteraction(String id) throws IOException {
        if (!visited.add("interaction:" + id)) return;
        walk(document(interactions, id), "interaction:" + id);
    }
    private void visitProjectile(String id) throws IOException {
        if (!visited.add("projectile:" + id)) return;
        walk(document(projectiles, id), "projectile:" + id);
    }
    private void walk(BsonValue value, String trace) throws IOException {
        if (visited.size() > 4096) throw new IllegalStateException(carrier + " native graph exceeds 4096 nodes");
        if (value.isArray()) {
            for (var child : value.asArray()) walk(child, trace);
            return;
        }
        if (value.isString()) {
            var id = value.asString().getValue();
            if (interactions.containsKey(id)) visitInteraction(id);
            else if (roots.containsKey(id)) visitRoot(id);
            else if (id.startsWith("RPG_Carrier_"))
                throw new IllegalStateException(carrier + " references absent custom native node " + id + " at " + trace);
            return;
        }
        if (!value.isDocument()) return;
        var object = value.asDocument();
        if (object.containsKey("Parent") && object.get("Parent").isString()) {
            var parent = object.getString("Parent").getValue();
            var source = interactions.containsKey(parent) ? interactions : roots;
            object = merged(document(source, parent), object);
        }
        if (object.containsKey("Type") && object.get("Type").isString()) {
            var type = object.getString("Type").getValue();
            if (type.equals("Replace")) {
                var var = object.getString("Var").getValue();
                var selected = variables.get(var);
                if (selected != null) {
                    if (selected.isString()) visitRoot(selected.asString().getValue());
                    else walk(selected, trace + "/Var:" + var);
                } else if (object.containsKey("DefaultValue")) walk(object.get("DefaultValue"), trace + "/Default:" + var);
                else if (!object.getBoolean("DefaultOk", new org.bson.BsonBoolean(false)).getValue())
                    throw new IllegalStateException(carrier + " unresolved native Replace " + var + " at " + trace);
                return;
            }
            if (type.equals("DamageEntity") || type.equals("LaunchProjectile") ||
                    type.equals("ModifyInventory") || type.startsWith("Explode"))
                throw new IllegalStateException(carrier + " reaches fixed native " + type + " at " + trace);
            if (type.equals(ManagedCarrierDamageInteraction.TYPE)) managedLeaves.add(trace);
            if (type.equals(ManagedCarrierProjectile.TYPE)) {
                if (!object.containsKey("Config")) throw new IllegalStateException(carrier + " carrier projectile has no config");
                visitProjectile(object.getString("Config").getValue());
            }
        }
        if (object.containsKey("EntityDamage"))
            throw new IllegalStateException(carrier + " reaches fixed native EntityDamage at " + trace);
        for (var field : object.entrySet()) {
            if (field.getKey().equals("Parent") || field.getKey().equals("Type") ||
                    field.getKey().equals("Config")) continue;
            walk(field.getValue(), trace + "/" + field.getKey());
        }
    }

    private static BsonDocument merged(BsonDocument parent, BsonDocument child) {
        var result = parent.clone();
        for (var field : child.entrySet()) {
            if (field.getKey().equals("Parent")) continue;
            var existing = result.get(field.getKey());
            if (existing != null && existing.isDocument() && field.getValue().isDocument())
                result.put(field.getKey(), merged(existing.asDocument(), field.getValue().asDocument()));
            else result.put(field.getKey(), field.getValue());
        }
        result.remove("Parent");
        return result;
    }
    @Override public void close() throws IOException { zip.close(); }
}
