package com.inigmasgames.persistentnpcs.command;

import com.inigmasgames.persistentnpcs.hytale.HytaleConversationBridge;
import com.inigmasgames.persistentnpcs.hytale.HytaleNpcAdapter;
import com.inigmasgames.persistentnpcs.hytale.ImmersiveNpcRoleService;
import com.inigmasgames.persistentnpcs.profile.NpcProfileEditorService;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import java.util.function.Supplier;
import java.util.function.Consumer;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceRecordingService;

/** Immersive extension installed under Hytale's parity-checked native /npc root. */
public final class ImmersiveNpcCreateCommand extends AbstractImmersiveNpcProfileCommand {
    public ImmersiveNpcCreateCommand(
            NpcProfileEditorService editor,
            NpcProfileRegistry profiles,
            ImmersiveNpcRoleService roles,
            HytaleNpcAdapter adapter,
            HytaleConversationBridge conversations,
            NpcVoiceRecordingService voiceRecorder,
            Supplier<String> runtimeBlocker,
            Consumer<String> diagnostics) {
        super("create", false, editor, profiles, roles, adapter, conversations, voiceRecorder,
                runtimeBlocker, diagnostics);
    }
}
