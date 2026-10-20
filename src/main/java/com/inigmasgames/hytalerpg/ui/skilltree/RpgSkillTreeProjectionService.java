package com.inigmasgames.hytalerpg.ui.skilltree;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveDefinition;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.SkillDefinition;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutOperations;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Canonical catalog/state -> static Skill Tree projection; it never mutates authority. */
public final class RpgSkillTreeProjectionService {
    public static final String PLACEHOLDER_ICON = "Common/Icons/Abilities/Debug_Rune_Tornado.png";
    public static final String CURRENT_WEAPON_FILTER = "Compatible with current weapon";
    private final RpgCatalog catalog;
    private final RpgLoadoutOperations loadouts;
    private final StaticSkillTreeLayout layout;
    private final boolean developmentEntitlements;

    public RpgSkillTreeProjectionService(RpgCatalog catalog, RpgLoadoutOperations loadouts,
                                         StaticSkillTreeLayout layout, boolean developmentEntitlements) {
        this.catalog = catalog; this.loadouts = loadouts; this.layout = layout;
        this.developmentEntitlements = developmentEntitlements;
    }

    public StaticSkillTreeViewModel project(UUID player, StaticSkillTreeViewModel.Tab tab, String query,
                                            String weaponFilter, String currentWeaponKind,
                                            LinkNodeId selectedNode, String selectedItemId) {
        RpgLoadoutView view = loadouts.getPresentationView(player);
        List<StaticSkillTreeViewModel.LibraryItem> library = tab == StaticSkillTreeViewModel.Tab.SKILLS
                ? skills(view, query, weaponFilter, currentWeaponKind) : passives(view, query);
        Map<LinkNodeId, StaticSkillTreeViewModel.TreeNode> nodes = nodes(view);
        return new StaticSkillTreeViewModel(view.state().revision, tab, clean(query), clean(weaponFilter),
                weaponFilters(currentWeaponKind), library, nodes,
                details(view, tab, selectedNode, selectedItemId), selectedNode, clean(selectedItemId));
    }

    public List<String> weaponFilters(String currentWeaponKind) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (!clean(currentWeaponKind).isBlank() && !"UNKNOWN".equalsIgnoreCase(currentWeaponKind))
            result.add(CURRENT_WEAPON_FILTER);
        catalog.skills().stream().map(SkillDefinition::weaponRequirement).filter(value -> value != null && !value.isBlank())
                .flatMap(value -> List.of(value.split("\\s*/\\s*")).stream()).map(String::trim)
                .filter(value -> !value.isBlank()).sorted(String.CASE_INSENSITIVE_ORDER).forEach(result::add);
        return List.copyOf(result);
    }

    List<StaticSkillTreeViewModel.LibraryItem> skills(RpgLoadoutView view, String query,
                                                       String weaponFilter, String currentWeaponKind) {
        String needle = clean(query).toLowerCase(Locale.ROOT);
        return catalog.skills().stream()
                .filter(def -> developmentEntitlements || view.state().learnedSkills.contains(def.id().value()))
                .filter(def -> matches(def.name(), def.description(), def.tags(), needle))
                .filter(def -> weaponMatches(def.weaponRequirement(), weaponFilter, currentWeaponKind))
                .sorted(Comparator.comparing(SkillDefinition::name))
                .map(def -> new StaticSkillTreeViewModel.LibraryItem(def.id().value(), def.name(), def.family(),
                        def.description(), PLACEHOLDER_ICON, def.weaponRequirement())).toList();
    }

    List<StaticSkillTreeViewModel.LibraryItem> passives(RpgLoadoutView view, String query) {
        String needle = clean(query).toLowerCase(Locale.ROOT);
        return catalog.passives().stream()
                .filter(def -> developmentEntitlements || view.state().ownedPassives.getOrDefault(def.id().value(), 0) > 0)
                .filter(def -> matches(def.name(), def.description(), union(def.compatibleTags(), def.requiredFamilies(),
                        def.requiredCapabilities(), def.aliases()), needle))
                .sorted(Comparator.comparing(PassiveDefinition::name))
                .map(def -> new StaticSkillTreeViewModel.LibraryItem(def.id().value(), def.name(), def.tier(),
                        def.description(), PLACEHOLDER_ICON, "")).toList();
    }

    private Map<LinkNodeId, StaticSkillTreeViewModel.TreeNode> nodes(RpgLoadoutView view) {
        Map<LinkNodeId, StaticSkillTreeViewModel.TreeNode> result = new EnumMap<>(LinkNodeId.class);
        for (LinkNodeId node : StaticSkillTreeLayout.CONTENT_NODES) {
            if (node.kind() == LinkNodeId.NodeKind.SKILL) {
                var id = view.state().skill(node.skillSlot());
                String name = id.flatMap(catalog::skill).map(SkillDefinition::name).orElse("Empty Skill");
                String family = id.flatMap(catalog::skill).map(SkillDefinition::family).orElse("Select to assign");
                result.put(node, new StaticSkillTreeViewModel.TreeNode(node, name, family, id.isPresent()));
            } else {
                var id = view.state().passive(node.passiveSlot());
                String name = id.flatMap(catalog::passive).map(PassiveDefinition::name).orElse("Empty Passive");
                String category = id.flatMap(catalog::passive).map(PassiveDefinition::tier).orElse("Select to assign");
                result.put(node, new StaticSkillTreeViewModel.TreeNode(node, name, category, id.isPresent()));
            }
        }
        for (LinkNodeId joint : StaticSkillTreeLayout.JOINTS)
            result.put(joint, new StaticSkillTreeViewModel.TreeNode(joint,
                    joint == LinkNodeId.JOINT01 ? "Joint A" : "Joint B",
                    joint == LinkNodeId.JOINT01 ? "3 Passive inputs" : "2 / 3 Passive inputs", true));
        return Map.copyOf(result);
    }

    private StaticSkillTreeViewModel.Details details(RpgLoadoutView view, StaticSkillTreeViewModel.Tab tab,
                                                     LinkNodeId node, String itemId) {
        if (!clean(itemId).isBlank()) {
            if (tab == StaticSkillTreeViewModel.Tab.SKILLS)
                return catalog.skill(new SkillId(itemId)).map(def -> skillDetails(view, def)).orElse(emptyDetails());
            return catalog.passive(new PassiveId(itemId)).map(def -> passiveDetails(view, def)).orElse(emptyDetails());
        }
        if (node != null && node.kind() == LinkNodeId.NodeKind.SKILL)
            return view.state().skill(node.skillSlot()).flatMap(catalog::skill).map(def -> skillDetails(view, def)).orElse(emptyDetails());
        if (node != null && node.kind() == LinkNodeId.NodeKind.PASSIVE)
            return view.state().passive(node.passiveSlot()).flatMap(catalog::passive).map(def -> passiveDetails(view, def)).orElse(emptyDetails());
        return emptyDetails();
    }

    private StaticSkillTreeViewModel.Details skillDetails(RpgLoadoutView view, SkillDefinition def) {
        List<String> linked = new ArrayList<>();
        view.routes().forEach((slot, route) -> {
            if (!route.isEmpty() && route.getLast().kind() == LinkNodeId.NodeKind.SKILL
                    && view.state().skill(route.getLast().skillSlot()).map(def.id()::equals).orElse(false))
                view.state().passive(slot).flatMap(catalog::passive).map(PassiveDefinition::name).ifPresent(linked::add);
        });
        return new StaticSkillTreeViewModel.Details("SKILL", def.id().value(), def.name(), def.family(), def.description(),
                List.of("Weapon: " + value(def.weaponRequirement()), "Resource: " + value(def.castCost()),
                        "Cooldown: " + value(def.cooldown()), "Cast: " + value(def.castTime()),
                        "Range: " + value(def.maxRange()), "Geometry: " + value(def.geometry()),
                        "Power: " + value(def.powerCoefficient()), "Status/control: " + join(def.statusApplications()),
                        "Linked Passives: " + (linked.isEmpty() ? "None" : String.join(", ", linked))),
                view.warnings().isEmpty() ? "Compile: PASS" : "Compile: " + String.join("; ", view.warnings()));
    }

    private StaticSkillTreeViewModel.Details passiveDetails(RpgLoadoutView view, PassiveDefinition def) {
        String assigned = "Not assigned"; String parent = "None";
        for (var slot : com.inigmasgames.hytalerpg.domain.PassiveSlot.values()) {
            if (view.state().passive(slot).map(def.id()::equals).orElse(false)) {
                assigned = slot.externalId();
                var skill = view.state().skill(layout.parentSkill(slot)).flatMap(catalog::skill);
                parent = skill.map(SkillDefinition::name).orElse("Empty " + layout.parentSkill(slot).externalId());
                break;
            }
        }
        return new StaticSkillTreeViewModel.Details("PASSIVE", def.id().value(), def.name(), def.tier(), def.description(),
                List.of("Compatible: " + join(def.compatibleTags()), "Required families: " + join(def.requiredFamilies()),
                        "Incompatible: " + join(def.incompatibleTags()), "Effect: " + join(def.modifierOps()),
                        "Assigned node: " + assigned, "Effective parent Skill: " + parent),
                "Validation occurs atomically on Apply");
    }

    private static StaticSkillTreeViewModel.Details emptyDetails() {
        return new StaticSkillTreeViewModel.Details("NONE", "", "Select content", "",
                "Choose a tree node or a library entry to inspect canonical details.", List.of(), "");
    }

    private static boolean matches(String name, String description, Iterable<String> keywords, String needle) {
        if (needle.isBlank()) return true;
        if ((name + " " + description).toLowerCase(Locale.ROOT).contains(needle)) return true;
        for (String keyword : keywords) if (keyword.toLowerCase(Locale.ROOT).contains(needle)) return true;
        return false;
    }
    private static boolean weaponMatches(String requirement, String filter, String currentKind) {
        String selected = clean(filter);
        if (selected.isBlank()) return true;
        if (CURRENT_WEAPON_FILTER.equals(selected)) selected = humanWeapon(currentKind);
        if (selected.equalsIgnoreCase("None")) return "None".equalsIgnoreCase(clean(requirement));
        return clean(requirement).toLowerCase(Locale.ROOT).contains(singular(selected).toLowerCase(Locale.ROOT));
    }
    private static String humanWeapon(String kind) {
        if (kind == null) return "";
        return switch (kind.toUpperCase(Locale.ROOT)) {
            case "SWORD" -> "Swords"; case "LONGSWORD" -> "Longswords"; case "DAGGER" -> "Daggers";
            case "BOW" -> "Bows"; case "CROSSBOW" -> "Crossbows"; case "MACE" -> "Maces";
            case "BATTLEAXE" -> "Battleaxes"; case "SHIELD" -> "Shield"; case "STAFF" -> "Staffs";
            case "WAND" -> "Wands"; case "SPELLBOOK" -> "Spellbooks"; default -> kind;
        };
    }
    private static String singular(String value) { return value.endsWith("s") ? value.substring(0, value.length() - 1) : value; }
    private static List<String> union(Set<String> a, Set<String> b, Set<String> c, List<String> d) {
        LinkedHashSet<String> result = new LinkedHashSet<>(a); result.addAll(b); result.addAll(c); result.addAll(d); return List.copyOf(result);
    }
    private static String join(Iterable<?> values) { List<String> out = new ArrayList<>(); values.forEach(v -> out.add(String.valueOf(v))); return out.isEmpty() ? "None" : String.join(", ", out); }
    private static String value(String value) { return value == null || value.isBlank() ? "None" : value; }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
