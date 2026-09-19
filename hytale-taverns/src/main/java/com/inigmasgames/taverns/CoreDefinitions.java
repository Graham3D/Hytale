package com.inigmasgames.taverns;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Registry for implemented Core definitions. Future Cores register here without changing Core services. */
public final class CoreDefinitions {
    public static final CoreDefinition TAVERN = new CoreDefinition(
            CoreType.TAVERN,
            "Core_Tavern",
            "Ingredient_Crystal_Green",
            21, 21, 5,
            5,
            1_000_000L);
    public static final CoreDefinition KITCHEN = new CoreDefinition(
            CoreType.KITCHEN,
            "Core_Kitchen",
            "Ingredient_Crystal_Cyan",
            13, 10, 5,
            5,
            1_000_000L);
    public static final CoreDefinition BEDROOM = new CoreDefinition(
            CoreType.BEDROOM,
            "Core_Bedroom",
            "Ingredient_Crystal_Blue",
            7, 5, 5,
            5,
            1_000_000L);

    private static final Map<CoreType, CoreDefinition> BY_TYPE = new EnumMap<>(CoreType.class);
    private static final Map<String, CoreDefinition> BY_ITEM_ID;

    static {
        BY_TYPE.put(TAVERN.type(), TAVERN);
        BY_TYPE.put(KITCHEN.type(), KITCHEN);
        BY_TYPE.put(BEDROOM.type(), BEDROOM);
        Map<String, CoreDefinition> byItemId = new LinkedHashMap<>();
        for (CoreDefinition definition : BY_TYPE.values()) {
            byItemId.put(definition.itemId(), definition);
        }
        BY_ITEM_ID = Map.copyOf(byItemId);
    }

    private CoreDefinitions() {
    }

    public static Optional<CoreDefinition> byType(CoreType type) {
        return Optional.ofNullable(BY_TYPE.get(type));
    }

    public static Optional<CoreDefinition> byItemId(String itemId) {
        return Optional.ofNullable(BY_ITEM_ID.get(itemId));
    }

    public static CoreDefinition require(CoreType type) {
        return byType(type).orElseThrow(() -> new IllegalStateException("Core type is not implemented: " + type));
    }
}
