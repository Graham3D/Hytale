package com.inigmasgames.hytalerpg.ui.skilltree;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleEquipmentAdapter;
import com.inigmasgames.hytalerpg.progress.MutationResult;
import com.inigmasgames.hytalerpg.ui.trace.RpgUiTraceService;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

/** Static, click-driven CustomUI frontend for the authoritative Link Tree backend. */
public final class RpgSkillTreePage extends InteractiveCustomUIPage<RpgSkillTreePage.Data> {
    private final PlayerRef player;
    private final RpgSkillTreeProjectionService projection;
    private final RpgSkillTreeMutationService mutations;
    private final RpgUiTraceService trace;
    private StaticSkillTreeViewModel.Tab tab = StaticSkillTreeViewModel.Tab.SKILLS;
    private String query = "";
    private String weaponFilter = "";
    private String currentWeaponKind = "";
    private LinkNodeId selectedNode = LinkNodeId.SKILL01;
    private String selectedItemId = "";
    private boolean filterOpen;
    private String status = "Ready";
    private StaticSkillTreeViewModel model;

    public RpgSkillTreePage(PlayerRef player, RpgSkillTreeProjectionService projection,
                            RpgSkillTreeMutationService mutations, RpgUiTraceService trace) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.player = player; this.projection = projection; this.mutations = mutations; this.trace = trace;
    }

    @Override public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                                @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("RpgSkillTree.ui");
        currentWeaponKind = currentWeapon(ref, store);
        project();
        render(commands, events);
        trace("SKILLTREE_OPENED", "page", Map.of("result", "PASS", "schemaRevision", model.revision(),
                "currentWeaponKind", currentWeaponKind, "hotkey", "BLOCKED_PUBLIC_API"));
    }

    @Override public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                          @Nonnull Data data) {
        currentWeaponKind = currentWeapon(ref, store);
        String action = clean(data.action);
        switch (action) {
            case "tab-skills" -> { tab = StaticSkillTreeViewModel.Tab.SKILLS; selectedItemId = ""; }
            case "tab-passives" -> { tab = StaticSkillTreeViewModel.Tab.PASSIVES; selectedItemId = ""; }
            case "search" -> query = clean(data.value);
            case "filter-open" -> filterOpen = !filterOpen;
            case "filter" -> weaponFilter = clean(data.id).equals(weaponFilter) ? "" : clean(data.id);
            case "node" -> selectNode(data.id);
            case "item" -> selectedItemId = clean(data.id);
            case "apply" -> apply();
            case "clear" -> clear();
            default -> { return; }
        }
        project();
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        render(commands, events);
        sendUpdate(commands, events, false);
        trace("SKILLTREE_INTERACTION", action, Map.of("nodeId", nodeId(), "itemId", selectedItemId,
                "tab", tab.name(), "query", query, "weaponFilter", weaponFilter, "result", status));
    }

    private void apply() {
        if (selectedNode == null || selectedNode.kind() == LinkNodeId.NodeKind.JOINT || selectedItemId.isBlank()) {
            status = "Select a content node and a matching library entry first.";
            return;
        }
        boolean matching = (selectedNode.kind() == LinkNodeId.NodeKind.SKILL && tab == StaticSkillTreeViewModel.Tab.SKILLS)
                || (selectedNode.kind() == LinkNodeId.NodeKind.PASSIVE && tab == StaticSkillTreeViewModel.Tab.PASSIVES);
        if (!matching) { status = "Library tab does not match the selected node type."; return; }
        MutationResult result = mutations.assign(player.getUuid(), model.revision(), selectedNode, selectedItemId);
        status = result.success() ? "Applied " + selectedItemId + " to " + selectedNode.externalId() + ". Compile: PASS."
                : result.code() + ": " + result.message();
        trace(result.success() ? "SKILLTREE_ASSIGN_COMMITTED" : "SKILLTREE_ASSIGN_REJECTED", "mutation",
                Map.of("nodeId", selectedNode.externalId(), "itemId", selectedItemId,
                        "result", result.code().name(), "reason", result.message(), "stateRevision", result.revision()));
    }

    private void clear() {
        if (selectedNode == null || selectedNode.kind() == LinkNodeId.NodeKind.JOINT) {
            status = "Select a Skill or Passive node to clear."; return;
        }
        MutationResult result = mutations.clear(player.getUuid(), model.revision(), selectedNode);
        status = result.success() ? "Cleared " + selectedNode.externalId() + ". Compile: PASS."
                : result.code() + ": " + result.message();
        selectedItemId = "";
        trace(result.success() ? "SKILLTREE_CLEAR_COMMITTED" : "SKILLTREE_CLEAR_REJECTED", "mutation",
                Map.of("nodeId", selectedNode.externalId(), "result", result.code().name(),
                        "reason", result.message(), "stateRevision", result.revision()));
    }

    private void selectNode(String value) {
        try {
            LinkNodeId node = LinkNodeId.parse(value);
            if (node.kind() == LinkNodeId.NodeKind.JOINT) { status = "Joints are fixed routing nodes, not content slots."; return; }
            selectedNode = node;
            tab = node.kind() == LinkNodeId.NodeKind.SKILL
                    ? StaticSkillTreeViewModel.Tab.SKILLS : StaticSkillTreeViewModel.Tab.PASSIVES;
            selectedItemId = "";
            status = "Selected " + node.externalId() + ".";
        } catch (RuntimeException ignored) { status = "Unknown tree node."; }
    }

    private void project() {
        model = projection.project(player.getUuid(), tab, query, weaponFilter, currentWeaponKind,
                selectedNode, selectedItemId);
    }

    private void render(UICommandBuilder commands, UIEventBuilder events) {
        commands.set("#LibraryHeading.TextSpans", Message.raw(tab == StaticSkillTreeViewModel.Tab.SKILLS
                ? "Available Skills" : "Available Passives"));
        commands.set("#FilterButton.Visible", tab == StaticSkillTreeViewModel.Tab.SKILLS);
        commands.set("#FilterPanel.Visible", tab == StaticSkillTreeViewModel.Tab.SKILLS && filterOpen);
        commands.set("#LibraryCount.TextSpans", Message.raw(model.library().size() + " entries"));
        commands.set("#TreeStatus.TextSpans", Message.raw(status));
        bind(events, CustomUIEventBindingType.Activating, "#SkillsTab", "tab-skills", "", "");
        bind(events, CustomUIEventBindingType.Activating, "#PassivesTab", "tab-passives", "", "");
        bind(events, CustomUIEventBindingType.Activating, "#FilterButton", "filter-open", "", "");
        bind(events, CustomUIEventBindingType.ValueChanged, "#SearchInput", "search", "", "#SearchInput.Value");
        bind(events, CustomUIEventBindingType.Activating, "#Apply", "apply", "", "");
        bind(events, CustomUIEventBindingType.Activating, "#Clear", "clear", "", "");
        renderNodes(commands, events);
        renderLibrary(commands, events);
        renderFilters(commands, events);
        renderDetails(commands);
    }

    private void renderNodes(UICommandBuilder commands, UIEventBuilder events) {
        for (LinkNodeId node : StaticSkillTreeLayout.CONTENT_NODES) {
            StaticSkillTreeViewModel.TreeNode value = model.nodes().get(node);
            String selector = "#" + title(node.externalId());
            commands.set(selector + ".Text", value.title() + "\n" + value.subtitle());
            bind(events, CustomUIEventBindingType.Activating, selector, "node", node.externalId(), "");
        }
    }

    private void renderLibrary(UICommandBuilder commands, UIEventBuilder events) {
        commands.clear("#LibraryRows");
        for (int index = 0; index < model.library().size(); index++) {
            StaticSkillTreeViewModel.LibraryItem item = model.library().get(index);
            commands.append("#LibraryRows", "RpgSkillTreeLibraryRow.ui");
            String row = "#LibraryRows[" + index + "]";
            commands.set(row + " #Select.Text", item.name() + "\n" + item.category());
            bind(events, CustomUIEventBindingType.Activating, row + " #Select", "item", item.id(), "");
        }
    }

    private void renderFilters(UICommandBuilder commands, UIEventBuilder events) {
        commands.clear("#FilterRows");
        for (int index = 0; index < model.weaponFilters().size(); index++) {
            String filter = model.weaponFilters().get(index);
            commands.append("#FilterRows", "RpgSkillTreeFilterRow.ui");
            String row = "#FilterRows[" + index + "]";
            commands.set(row + " #Name.TextSpans", Message.raw(filter));
            commands.set(row + " #Toggle.Value", filter.equals(weaponFilter));
            bind(events, CustomUIEventBindingType.ValueChanged, row + " #Toggle", "filter", filter, "");
        }
    }

    private void renderDetails(UICommandBuilder commands) {
        var details = model.details();
        commands.set("#DetailsName.TextSpans", Message.raw(details.name()));
        commands.set("#DetailsCategory.TextSpans", Message.raw(details.category()));
        commands.set("#DetailsDescription.TextSpans", Message.raw(details.description()));
        commands.set("#DetailsFacts.TextSpans", Message.raw(String.join("\n\n", details.facts())));
        commands.set("#DetailsValidation.TextSpans", Message.raw(details.validation()));
        boolean selectedContentNode = selectedNode != null && selectedNode.kind() != LinkNodeId.NodeKind.JOINT;
        commands.set("#Apply.Disabled", !selectedContentNode || selectedItemId.isBlank());
        commands.set("#Clear.Disabled", !selectedContentNode || !model.nodes().get(selectedNode).occupied());
    }

    @Override public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        trace("SKILLTREE_CLOSED", "page", Map.of("result", "PASS", "stateRevision",
                model == null ? -1 : model.revision()));
    }

    private String currentWeapon(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            var item = new HytaleEquipmentAdapter().read(ref, store).mainHand();
            return item == null ? "" : item.weaponKind();
        } catch (RuntimeException ignored) { return ""; }
    }
    private void trace(String event, String component, Map<String, ?> details) {
        trace.trace(player.getUuid(), event, id(), merge(component, details));
    }
    private static Map<String, Object> merge(String component, Map<String, ?> details) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("page", "skilltree"); result.put("component", component); result.putAll(details); return result;
    }
    private String nodeId() { return selectedNode == null ? "" : selectedNode.externalId(); }
    private static String title(String externalId) { return Character.toUpperCase(externalId.charAt(0)) + externalId.substring(1); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String id() { return UUID.randomUUID().toString().substring(0, 12); }
    private static void bind(UIEventBuilder events, CustomUIEventBindingType type, String selector,
                             String action, String id, String dynamicValue) {
        EventData data = new EventData().append("Action", action).append("Id", id);
        if (!dynamicValue.isBlank()) data.append("@Value", dynamicValue);
        events.addEventBinding(type, selector, data, false);
    }

    public static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("Id", Codec.STRING), (d, v) -> d.id = v, d -> d.id).add()
                .append(new KeyedCodec<>("Value", Codec.STRING), (d, v) -> d.value = v, d -> d.value).add()
                .build();
        private String action = "";
        private String id = "";
        private String value = "";
    }
}
