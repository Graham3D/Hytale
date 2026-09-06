package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record RpgLoadoutView(RpgPlayerState state, Map<SkillSlot, CompiledSkillPlan> plans,
                             Map<PassiveSlot, List<LinkNodeId>> routes, List<String> warnings) {
    public RpgLoadoutView {
        state = state.copy(); plans = Map.copyOf(plans); routes = Map.copyOf(routes); warnings = List.copyOf(warnings);
    }

    public String format(RpgCatalog catalog) {
        StringBuilder out = new StringBuilder("RPG loadout revision ").append(state.revision).append(':');
        for (SkillSlot slot : SkillSlot.values()) {
            out.append('\n').append(slot.externalId()).append(" = ");
            var skillId = state.skill(slot);
            if (skillId.isEmpty()) { out.append("(empty)"); continue; }
            String name = catalog.skill(skillId.get()).map(definition -> definition.name()).orElse("[missing:" + skillId.get().value() + ']');
            out.append(name);
            for (var route : routes.entrySet()) {
                if (route.getValue().isEmpty() || route.getValue().getLast().skillSlot() != slot) continue;
                var passiveId = state.passive(route.getKey());
                String passiveName = passiveId.flatMap(catalog::passive).map(definition -> definition.name())
                        .orElseGet(() -> passiveId.map(id -> "[missing:" + id.value() + ']').orElse("(empty)"));
                out.append("\n  <- ").append(route.getKey().externalId()).append(' ').append(passiveName);
                if (route.getValue().size() > 1) {
                    out.append(" via ");
                    out.append(String.join(" -> ", route.getValue().subList(0, route.getValue().size() - 1)
                            .stream().map(LinkNodeId::externalId).toList()));
                }
            }
            CompiledSkillPlan plan = plans.get(slot);
            if (plan != null) out.append("\n  compile=").append(plan.degraded() ? "DEGRADED" : "PASS")
                    .append(" family=").append(plan.finalFamily()).append(" continuation=").append(plan.continuation());
        }
        for (LinkNodeId joint : List.of(LinkNodeId.JOINT01, LinkNodeId.JOINT02)) {
            List<String> jointRoutes = new ArrayList<>();
            routes.forEach((passive, route) -> {
                if (route.contains(joint)) jointRoutes.add(passive.externalId() + " -> "
                        + String.join(" -> ", route.stream().map(LinkNodeId::externalId).toList()));
            });
            if (!jointRoutes.isEmpty()) out.append('\n').append(joint.externalId()).append(":\n  ")
                    .append(String.join("\n  ", jointRoutes));
        }
        if (!warnings.isEmpty()) out.append("\nDEGRADED: ").append(String.join("; ", warnings));
        return out.toString();
    }
}
