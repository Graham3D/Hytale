package com.inigmasgames.persistentnpcs.command;

import com.inigmasgames.persistentnpcs.hytale.HytaleConversationBridge;
import com.inigmasgames.persistentnpcs.hytale.HytaleNpcAdapter;
import com.inigmasgames.persistentnpcs.hytale.ImmersiveNpcRoleService;
import com.inigmasgames.persistentnpcs.profile.NpcProfileEditorService;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** Immersive extension installed under Hytale's parity-checked native /npc root. */
public final class ImmersiveNpcUpdateCommand extends AbstractImmersiveNpcProfileCommand {
    public ImmersiveNpcUpdateCommand(
            NpcProfileEditorService editor,
            NpcProfileRegistry profiles,
            ImmersiveNpcRoleService roles,
            HytaleNpcAdapter adapter,
            HytaleConversationBridge conversations,
            Supplier<String> runtimeBlocker,
            Consumer<String> diagnostics) {
        super("update", true, editor, profiles, roles, adapter, conversations,
                runtimeBlocker, diagnostics);
    }
}
