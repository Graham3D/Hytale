package com.inigmasgames.hytalerpg.ui;

import com.inigmasgames.hytalerpg.combat.attribute.DerivedStatService;
import com.inigmasgames.hytalerpg.combat.attribute.DerivedStats;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.cooldown.RpgCooldownService;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutOperations;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutView;
import com.inigmasgames.hytalerpg.ui.model.CharacterSheetViewModel;
import com.inigmasgames.hytalerpg.ui.model.RpgHudViewModel;
import com.inigmasgames.hytalerpg.ui.model.SkillSlotView;
import com.inigmasgames.hytalerpg.ui.model.XpView;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/** The sole gameplay/state -> immutable Stage-03 UI view-model boundary. */
public final class RpgUiProjectionService {
    private final RpgCatalog catalog;
    private final RpgLoadoutOperations loadouts;
    private final DerivedStatService derivedStats;
    private final RpgCooldownService cooldowns;
    private final CharacterXpProjectionService xp = new CharacterXpProjectionService();
    private final Stage04SkillProfiles stage04;

    public RpgUiProjectionService(RpgCatalog catalog, RpgLoadoutOperations loadouts,
                                  DerivedStatService derivedStats, RpgCooldownService cooldowns) {
        this.catalog = catalog; this.loadouts = loadouts; this.derivedStats = derivedStats; this.cooldowns = cooldowns;
        this.stage04 = Stage04SkillProfiles.loadCanonical(catalog);
    }

    public CharacterSheetViewModel character(UUID player, String displayName,
                                             HytaleResourceViewAdapter.Snapshot resources) {
        RpgLoadoutView view = loadouts.getPresentationView(player);
        DerivedStats derived = derive(view);
        return new CharacterSheetViewModel(view.state().revision, displayName,
                xp.project(view.state().currentXp), view.state().unspentAttributePoints,
                view.state().pendingLevelUpPoints, derived, resources.mana(), resources.health(), resources.stamina());
    }

    public RpgHudViewModel hud(UUID player, HytaleResourceViewAdapter.Snapshot resources, XpView xpOverride) {
        RpgLoadoutView view = loadouts.getPresentationView(player);
        XpView projectedXp = xpOverride == null ? xp.project(view.state().currentXp) : xpOverride;
        List<SkillSlotView> slots = new ArrayList<>(4);
        for (SkillSlot slot : SkillSlot.values()) {
            var id = view.state().skill(slot);
            if (id.isEmpty()) {
                slots.add(new SkillSlotView(slot, abilityAction(slot), "", "Empty", "rpg.icon.skill.empty",
                        0.0, SkillSlotView.State.EMPTY, ""));
                continue;
            }
            var definition = catalog.skill(id.get());
            String name = definition.map(value -> value.name()).orElse("Missing skill");
            String family = definition.map(value -> value.family().toLowerCase(java.util.Locale.ROOT)).orElse("unknown");
            double remaining = cooldowns.remaining(player, id.get().value());
            var plan = view.plans().get(slot);
            boolean ready = stage04.supports(id.get().value()) && plan != null && !plan.degraded()
                    && (stage04.require(id.get().value()).family().name().equals(plan.finalFamily())
                    || plan.finalTags().contains(stage04.require(id.get().value()).family().name()));
            SkillSlotView.State state = remaining > 0.0 ? SkillSlotView.State.COOLDOWN
                    : ready ? SkillSlotView.State.READY : SkillSlotView.State.UNAVAILABLE;
            slots.add(new SkillSlotView(slot, abilityAction(slot), id.get().value(), name,
                    "rpg.icon.skill.family." + family, remaining, state,
                    state == SkillSlotView.State.UNAVAILABLE
                            ? stage04.supports(id.get().value()) ? "COMPILED_PLAN_UNSUPPORTED" : "EXECUTOR_NOT_IMPLEMENTED"
                            : ""));
        }
        return new RpgHudViewModel(view.state().revision, resources.mana(), resources.health(), resources.stamina(),
                projectedXp, view.state().pendingLevelUpPoints, slots);
    }

    public CharacterXpProjectionService xp() { return xp; }

    private DerivedStats derive(RpgLoadoutView view) {
        EnumMap<RpgAttribute, Integer> raw = new EnumMap<>(RpgAttribute.class);
        for (RpgAttribute attribute : RpgAttribute.values())
            raw.put(attribute, view.state().attributes.getOrDefault(attribute.name(), 10));
        return derivedStats.derive(raw);
    }

    private static String abilityAction(SkillSlot slot) { return "Ability" + (slot.index() + 1); }
}
