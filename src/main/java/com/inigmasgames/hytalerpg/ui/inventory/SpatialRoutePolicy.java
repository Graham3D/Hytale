package com.inigmasgames.hytalerpg.ui.inventory;

import java.util.EnumMap;
import java.util.Map;

/** Release gate for every enabled player-item route. No row defaults to safe. */
public final class SpatialRoutePolicy {
    public enum Route {
        MANAGED_WORLD_LOOT, STOCK_WORLD_LOOT, AUTOMATIC_PICKUP, INTERACTIVE_PICKUP,
        ENCOUNTER_REWARD, HYTALE_GEAR_LOOT, ADMIN_GRANT,
        CHEST_TO_PLAYER, PLAYER_TO_CHEST, VENDOR_PURCHASE, VENDOR_SALE,
        STOCK_SHOP_GIVE_ITEM, TRIGGER_VOLUME_GRANT, OBJECTIVE_ITEM_REWARD,
        TAVERN_ITEM_RETURN, PROJECT_QA_GRANT, PROJECT_AMMO_REFUND,
        CRAFT_OUTPUT, CRAFT_INGREDIENT, STATION_CRAFT,
        NATIVE_SORT, NATIVE_QUICK_STACK, BAG_DROP, DEATH, RESPAWN,
        EQUIP, UNEQUIP, HOTBAR_HELD, NPC_EXCHANGE, RECONNECT_RELOAD, TAB_OPEN_CLOSE
    }
    public enum Disposition {
        QUALIFIED_ADAPTER, DISABLED_IN_SPATIAL_MODE, NOT_ENABLED_IN_THIS_BUILD, UNQUALIFIED
    }
    public record Decision(Disposition disposition, String evidence) {
        public Decision {
            if (disposition == null || evidence == null || evidence.isBlank())
                throw new IllegalArgumentException("Every route needs a disposition and evidence");
        }
    }
    private static final Map<Route, Decision> CURRENT = new EnumMap<>(Route.class);
    static {
        for (Route route : Route.values()) CURRENT.put(route,
                new Decision(Disposition.UNQUALIFIED, "Connected admission/recovery proof pending"));
        CURRENT.put(Route.NATIVE_SORT, new Decision(Disposition.UNQUALIFIED,
                "Private bag is separate, but the native UI may still sort retained Storage"));
        CURRENT.put(Route.NATIVE_QUICK_STACK, new Decision(Disposition.UNQUALIFIED,
                "Native external-container quick-stack still needs a pre-mutation policy"));
        CURRENT.put(Route.TAB_OPEN_CLOSE, new Decision(Disposition.UNQUALIFIED,
                "Post-open bridge works; ownership neutrality during native flash is not qualified"));
        CURRENT.put(Route.STOCK_SHOP_GIVE_ITEM, new Decision(Disposition.NOT_ENABLED_IN_THIS_BUILD,
                "0 ShopAsset GiveItemInteraction JSON entries in installed pre.4 Assets.zip; audited at build time"));
        CURRENT.put(Route.TRIGGER_VOLUME_GRANT, new Decision(Disposition.DISABLED_IN_SPATIAL_MODE,
                "Showcase GiveItem effect overridden with HywindSpatialGrant; coordinator refuses spatial grants until durable source receipt proof"));
        CURRENT.put(Route.OBJECTIVE_ITEM_REWARD, new Decision(Disposition.NOT_ENABLED_IN_THIS_BUILD,
                "Eight GiveItems objective templates reachable only through developer test objective line in current asset set"));
        CURRENT.put(Route.VENDOR_PURCHASE, new Decision(Disposition.DISABLED_IN_SPATIAL_MODE,
                "All three stock barter NPC roles use HywindOpenBarterShop producer gate"));
        CURRENT.put(Route.NPC_EXCHANGE, new Decision(Disposition.DISABLED_IN_SPATIAL_MODE,
                "Persistent NPC TAKE_ITEM and GIVE_ITEM producers gate on the shared spatial owner policy"));
        CURRENT.put(Route.TAVERN_ITEM_RETURN, new Decision(Disposition.DISABLED_IN_SPATIAL_MODE,
                "Tavern serving pickup, consumption return and core shard refund producers gate on shared owner policy"));
        CURRENT.put(Route.PROJECT_QA_GRANT, new Decision(Disposition.QUALIFIED_ADAPTER,
                "RpgGearCommand routes generated frozen ItemStack through spatial preflight, issuance receipt and player save"));
        CURRENT.put(Route.PROJECT_AMMO_REFUND, new Decision(Disposition.DISABLED_IN_SPATIAL_MODE,
                "HytaleAmmoAdapter rejects native ammunition use and refund in spatial mode"));
    }
    private SpatialRoutePolicy() { }
    public static Map<Route, Decision> current() { return Map.copyOf(CURRENT); }
    public static void requireReleaseReady() {
        var missing = CURRENT.entrySet().stream()
                .filter(entry -> entry.getValue().disposition() == Disposition.UNQUALIFIED)
                .map(entry -> entry.getKey().name()).sorted().toList();
        if (!missing.isEmpty()) throw new IllegalStateException("Spatial release routes unqualified: " + missing);
    }
}
