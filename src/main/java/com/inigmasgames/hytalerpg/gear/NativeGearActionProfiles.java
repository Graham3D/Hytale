package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import java.util.Objects;
import java.util.UUID;

/** Native asset binding for an accepted, valid primary chain. The profile is immutable
 * for the whole chain; callers must select it before native chain acceptance. */
public final class NativeGearActionProfiles {
    private NativeGearActionProfiles() {}

    public record Profile(UUID itemId, String snapshotRevision, String nativeBaseId,
                          String rootId, String animationsId, double frozenRatePercent,
                          double effectiveRatePercent, double reachMetres) {
        public Profile {
            Objects.requireNonNull(itemId);
            Objects.requireNonNull(snapshotRevision);
            Objects.requireNonNull(nativeBaseId);
            Objects.requireNonNull(rootId);
            Objects.requireNonNull(animationsId);
        }
        public RootInteraction requireLoadedRoot() {
            var root = RootInteraction.getAssetMap().getAsset(rootId);
            if (root == null) throw new IllegalStateException("Native gear action profile is not loaded: " + rootId);
            return root;
        }
    }

    /** WA-008 retains its frozen 0.1 percentage-point resolution. WA-014 retains 0.01 m.
     * A caller must bind the returned native root to the managed carrier before acceptance;
     * this method never edits a shared root or changes the managed item identity. */
    public static Profile swordPrimary(GearEffectSnapshot accepted, UUID actualMainhand) {
        return primary(accepted, actualMainhand);
    }

    public static String family(String baseId, boolean twin) {
        if (twin && baseId.startsWith("gm.daggers_")) return "twin";
        for (String family : java.util.List.of("sword", "battleaxe", "mace", "daggers", "longsword",
                "shortbow", "crossbow", "staff", "wand", "book", "bomb"))
            if (baseId.startsWith("gm." + family + "_")) return family;
        throw new IllegalArgumentException("No audited native primary action for " + baseId);
    }

    public static Profile primary(GearEffectSnapshot accepted, UUID actualMainhand) {
        Objects.requireNonNull(accepted);
        Objects.requireNonNull(actualMainhand);
        var local = accepted.forItem(actualMainhand);
        if (local.empty()) throw new IllegalArgumentException("No valid active mainhand in accepted snapshot");
        var item = local.items().getFirst();
        boolean twin = item.baseId().startsWith("gm.daggers_") && local.value("WA-141") > 0;
        String family = family(item.baseId(), twin);
        double rate = local.value("WA-008");
        double reach = local.value("WA-014");
        if (!Double.isFinite(rate) || !Double.isFinite(reach))
            throw new IllegalArgumentException("Nonfinite frozen action roll");
        if (rate == 0 && reach == 0 && !twin)
            throw new IllegalArgumentException("Unaffixed weapon keeps its original native root");
        boolean melee = java.util.Set.of("sword", "battleaxe", "mace", "daggers", "twin", "longsword").contains(family);
        if (!melee && reach != 0) throw new IllegalArgumentException("Only audited melee geometry accepts WA-014");
        if ((rate != 0 && (rate < 4.8 || rate > 18)) || (reach != 0 && (reach < .12 || reach > .5)))
            throw new IllegalArgumentException("Frozen action roll outside authored bounds");
        int rateTenths = (int) Math.round(rate * 10);
        if (Math.abs(rateTenths / 10.0 - rate) > 1e-7)
            throw new IllegalArgumentException("Attack rate not on authored tenth-percent grid");
        int reachCm = (int) Math.round(reach * 100);
        if (Math.abs(reachCm / 100.0 - reach) > 1e-7)
            throw new IllegalArgumentException("Melee reach not on authored centimetre grid");
        // Retain the already packaged exact sword sample; new families use the frozen tenth-percent key.
        String speed = family.equals("sword") && rateTenths == 150 ? "115" : Integer.toString(1000 + rateTenths);
        String stem = "RPG_Action_" + Character.toUpperCase(family.charAt(0)) + family.substring(1)
                + "_S" + speed + "_R" + String.format(java.util.Locale.ROOT, "%02d", reachCm);
        return new Profile(item.identity(), accepted.revision(), item.baseId(),
                stem + "_Root", stem + "_Animations", rate, rate, reachCm / 100.0);
    }
}
