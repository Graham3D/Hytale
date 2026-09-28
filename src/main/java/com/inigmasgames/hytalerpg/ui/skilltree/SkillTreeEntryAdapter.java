package com.inigmasgames.hytalerpg.ui.skilltree;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.canvasui.CanvasUI;
import com.inigmasgames.canvasui.api.editor.CursorEditorOpenResult;

/** One entry to the established Canvas Skill Tree, shared by command and workspace. */
public final class SkillTreeEntryAdapter {
    private final RpgSkillTreeProjectionService projection;
    private final RpgSkillTreeMutationService mutations;
    private final SkillTreePortBindingStore portBindings;

    public SkillTreeEntryAdapter(RpgSkillTreeProjectionService projection,
                                 RpgSkillTreeMutationService mutations,
                                 SkillTreePortBindingStore portBindings) {
        this.projection = projection;
        this.mutations = mutations;
        this.portBindings = portBindings;
    }

    public CursorEditorOpenResult open(PlayerRef playerRef, Ref<EntityStore> ref,
                                       Store<EntityStore> store, World world) {
        if (!ref.isValid()) return new CursorEditorOpenResult(false, "Player is no longer available.", null);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return new CursorEditorOpenResult(false, "Player UI manager is unavailable.", null);
        return CanvasUI.openCursorEditor(
                new RpgCanvasSkillTreeEditor(playerRef.getUuid(), projection, mutations, portBindings),
                player, playerRef, world, store, ref);
    }
}
