package com.inigmasgames.hytalerpg.ui.skilltree;

import com.inigmasgames.hytalerpg.domain.LinkNodeId;

import java.util.List;
import java.util.Map;

/** Immutable projection consumed by the static CustomUI page. */
public record StaticSkillTreeViewModel(
        long revision,
        Tab tab,
        String query,
        String weaponFilter,
        List<String> weaponFilters,
        List<LibraryItem> library,
        Map<LinkNodeId, TreeNode> nodes,
        Details details,
        LinkNodeId selectedNode,
        String selectedItemId) {
    public enum Tab { SKILLS, PASSIVES }
    public record LibraryItem(String id, String name, String category, String description,
                              String iconPath, String weaponRequirement) {}
    public record TreeNode(LinkNodeId id, String title, String subtitle, boolean occupied) {}
    public record Details(String kind, String id, String name, String category, String description,
                          List<String> facts, String validation) {}
}
