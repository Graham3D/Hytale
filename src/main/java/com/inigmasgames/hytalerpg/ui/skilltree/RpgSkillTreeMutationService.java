package com.inigmasgames.hytalerpg.ui.skilltree;

import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.EdgeId;
import com.inigmasgames.hytalerpg.progress.MutationResult;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutView;

import java.util.UUID;

/** Converts a static content-node assignment into one authoritative compile/save transaction. */
public final class RpgSkillTreeMutationService {
    private final RpgLoadoutService loadouts;
    private final StaticSkillTreeLayout layout;

    public RpgSkillTreeMutationService(RpgLoadoutService loadouts, StaticSkillTreeLayout layout) {
        this.loadouts = loadouts;
        this.layout = layout;
    }

    public MutationResult assign(UUID player, long expectedRevision, LinkNodeId node, String contentId) {
        return loadouts.mutateStaticTree(player, expectedRevision, node, contentId,
                candidate -> candidate.linkEdges(layout.authoritativeEdges(candidate)));
    }

    public MutationResult clear(UUID player, long expectedRevision, LinkNodeId node) {
        return assign(player, expectedRevision, node, "");
    }

    /** Canvas assignment preserves the player's authored topology; linking is a separate gesture. */
    public MutationResult assignCanvas(UUID player, long expectedRevision, LinkNodeId node, String contentId) {
        return loadouts.mutateStaticTree(player, expectedRevision, node, contentId, candidate -> { });
    }

    public MutationResult link(UUID player, LinkNodeId source, LinkNodeId target) {
        return loadouts.link(player, source, target);
    }

    public MutationResult unlinkSource(UUID player, LinkNodeId source) {
        return loadouts.unlinkSource(player, source);
    }

    public MutationResult unlinkEdge(UUID player, String edgeId) {
        return loadouts.unlink(player, new EdgeId(edgeId));
    }

    public RpgLoadoutView view(UUID player) { return loadouts.getPresentationView(player); }
}
