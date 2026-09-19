package com.inigmasgames.persistentnpcs.profile;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Explicit native-role configuration, never a fabricated current ECS value or a file writer. */
public record NpcConfiguredVitals(Map<String, Double> maxima, Optional<Boolean> invulnerable) {
    public static final NpcConfiguredVitals EMPTY = new NpcConfiguredVitals(Map.of(), Optional.empty());
    public NpcConfiguredVitals { maxima = Map.copyOf(maxima); }
    public static NpcConfiguredVitals read(Path role) {
        if (!Files.isRegularFile(role)) return EMPTY;
        JsonObject json = JsonFiles.read(role, JsonObject.class);
        var maxima = new TreeMap<String, Double>();
        for (String id : new String[] { "Health", "Stamina", "Mana" }) {
            var value = json.get("Max" + id);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                double maximum = value.getAsDouble();
                if (Double.isFinite(maximum) && maximum >= 0) maxima.put(id, maximum);
            }
        }
        var flag = json.get("Invulnerable");
        Optional<Boolean> invulnerable = flag != null && flag.isJsonPrimitive()
                && flag.getAsJsonPrimitive().isBoolean() ? Optional.of(flag.getAsBoolean()) : Optional.empty();
        return new NpcConfiguredVitals(maxima, invulnerable);
    }
    public String text(String id) {
        return maxima.containsKey(id) ? "MAX " + number(maxima.get(id)) : "—";
    }
    public String tooltip() {
        return "Unspawned: explicit native-role maxima only; current vitals unavailable."
                + invulnerable.map(value -> " Configured Invulnerable: " + value + ".").orElse("");
    }
    public static String number(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }
}
