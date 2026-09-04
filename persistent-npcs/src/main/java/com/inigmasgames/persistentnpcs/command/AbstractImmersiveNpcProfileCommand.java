package com.inigmasgames.persistentnpcs.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.protocol.EquipmentUpdate;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.hytale.HytaleConversationBridge;
import com.inigmasgames.persistentnpcs.hytale.HytaleNpcAdapter;
import com.inigmasgames.persistentnpcs.hytale.ImmersiveNpcRoleService;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileEditorService;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringPermissions;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringSession;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringSessionRegistry;
import com.inigmasgames.persistentnpcs.ui.NpcMeshPreviewSession;
import com.inigmasgames.persistentnpcs.ui.NpcProfilePage;
import com.inigmasgames.persistentnpcs.ui.NativeNpcInventoryController;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceRecordingService;
import java.util.function.Supplier;
import java.util.function.Consumer;

abstract class AbstractImmersiveNpcProfileCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> nameArg;
    private final boolean update;
    private final NpcProfileEditorService editor;
    private final NpcProfileRegistry profiles;
    private final ImmersiveNpcRoleService roles;
    private final HytaleNpcAdapter adapter;
    private final HytaleConversationBridge conversations;
    private final NpcVoiceRecordingService voiceRecorder;
    private final Supplier<String> runtimeBlocker;
    private final Consumer<String> diagnostics;

    AbstractImmersiveNpcProfileCommand(
            String commandName,
            boolean update,
            NpcProfileEditorService editor,
            NpcProfileRegistry profiles,
            ImmersiveNpcRoleService roles,
            HytaleNpcAdapter adapter,
            HytaleConversationBridge conversations,
            NpcVoiceRecordingService voiceRecorder,
            Supplier<String> runtimeBlocker,
            Consumer<String> diagnostics) {
        super(commandName, "Immersive NPC profile " + (update ? "update" : "creation"));
        this.update = update;
        this.editor = editor;
        this.profiles = profiles;
        this.roles = roles;
        this.adapter = adapter;
        this.conversations = conversations;
        this.voiceRecorder = voiceRecorder;
        this.runtimeBlocker = runtimeBlocker == null ? () -> "" : runtimeBlocker;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
        requirePermission(NpcAuthoringPermissions.OPEN);
        this.nameArg = withRequiredArg("name", "Immersive NPC profile name",
                ArgTypes.GREEDY_STRING);
    }

    @Override
    protected final void execute(
            CommandContext context,
            Store<EntityStore> store,
            Ref<EntityStore> playerEntityRef,
            PlayerRef playerRef,
            World world) {
        try {
            String blocker = runtimeBlocker.get();
            if (blocker != null && !blocker.isBlank()) {
                context.sendMessage(Message.raw(blocker));
                return;
            }
            String name = ProfileRepository.sanitizeProfileName(context.get(nameArg));
            if (update) editor.requireExisting(name);
            else editor.beginCreate(name);
            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            if (player == null) throw new IllegalStateException(
                    "Player page manager is unavailable.");
            InventoryComponent.Storage storage = store.getComponent(
                    playerEntityRef, InventoryComponent.Storage.getComponentType());
            if (storage == null || storage.getInventory() == null) {
                throw new IllegalStateException("Player storage inventory is unavailable.");
            }
            ItemContainer playerInventory = storage.getInventory();
            NpcProfile selectedProfile = update ? profiles.requireName(name)
                    : editor.currentProfile(name).orElseThrow(() ->
                            new IllegalStateException("Created profile identity is unavailable."));
            NativeNpcInventoryController.LiveStorageAuthority resolvedStorage = null;
            if (update) {
                try {
                    resolvedStorage = NativeNpcInventoryController.resolve(
                            selectedProfile, adapter, editor.inventories(),
                            playerRef, store, diagnostics);
                } catch (IllegalStateException unavailable) {
                    String message = safeMessage(unavailable);
                    if (!message.equals(selectedProfile.name() + " is not spawned.")) {
                        throw unavailable;
                    }
                    diagnostics.accept("NPC_PROFILE_LIVE_STORAGE_UNAVAILABLE npc=" + name
                            + " profileId=" + selectedProfile.id()
                            + " reason=NOT_SPAWNED"
                            + " inventoryMode=MUTABLE_PERSISTED_AUTHORING_STORAGE");
                }
            }
            NativeNpcInventoryController.LiveStorageAuthority liveStorageAuthority =
                    resolvedStorage;
            NpcMeshPreviewSession preview = null;
            NpcAuthoringSession authoringSession = null;
            NpcProfilePage page = null;
            try {
                if (update) {
                    var appearance = editor.previewAppearance(name).orElseThrow(() ->
                            new IllegalStateException("No valid NPC appearance is available for preview."));
                    var equipment = editor.previewEquipment(name);
                    preview = NpcMeshPreviewSession.begin(
                            playerRef, playerEntityRef, store, name, appearance.model(),
                            appearance.playerSkin(), new EquipmentUpdate(
                                    equipment.visibleArmorIds(), equipment.rightHandItemId(),
                                    equipment.leftHandItemId()), diagnostics);
                }
                authoringSession = NpcAuthoringSessionRegistry.shared().acquire(
                        playerRef.getUuid(), selectedProfile.stableId(),
                        liveStorageAuthority == null ? null : liveStorageAuthority.npcEntityId(),
                        editor.authoringRevisionSnapshot(name),
                        permission -> com.hypixel.hytale.server.core.permissions.PermissionsModule
                                .get().hasPermission(playerRef.getUuid(), permission),
                        diagnostics);
                page = new NpcProfilePage(
                        playerRef, name, update, editor, playerInventory,
                        liveStorageAuthority, authoringSession, preview,
                        voiceRecorder,
                        profile -> commit(store, playerRef, profile),
                        (viewerRef, eventStore) -> delete(
                                eventStore, selectedProfile),
                        diagnostics);
                if (!player.getPageManager().openCustomPageWithWindows(
                        playerEntityRef, store, page, page.windows())) {
                    page.onDismiss(playerEntityRef, store);
                    page = null;
                    throw new IllegalStateException(
                            "Hytale could not open the NPC authoring inventory windows.");
                }
                diagnostics.accept("NPC_PROFILE_NATIVE_WINDOWS_BOUND npc=" + name
                        + " " + page.nativeInventoryDiagnostics());
                authoringSession.ready();
                page.applyPreviewAfterPageMount();
            } catch (RuntimeException failure) {
                if (page != null) page.onDismiss(playerEntityRef, store);
                else {
                    if (preview != null) preview.close();
                    if (authoringSession != null) authoringSession.close();
                }
                throw failure;
            }
        } catch (RuntimeException failure) {
            context.sendMessage(Message.raw("NPC " + (update ? "update" : "create")
                    + " failed: " + safeMessage(failure)));
        }
    }

    private void commit(Store<EntityStore> store, PlayerRef playerRef, NpcProfile profile) {
        profiles.register(profile);
        roles.registerOrUpdate(profile);
        if (!update) return;
        try {
            HytaleNpcAdapter.RefreshResult refreshed = adapter.refreshNpc(
                    store, playerRef, profile);
            conversations.entityRefreshed(profile.id(), refreshed.entityId());
        } catch (IllegalStateException notActive) {
            String message = safeMessage(notActive);
            if (!message.contains("not spawned")) throw notActive;
        }
    }

    private void delete(Store<EntityStore> store, NpcProfile profile) {
        if (!update || profile == null) {
            throw new IllegalStateException("Only an existing NPC profile can be deleted.");
        }
        try {
            adapter.removeNpc(store, profile);
        } catch (IllegalStateException notActive) {
            if (!safeMessage(notActive).contains("not spawned")) throw notActive;
        }
        conversations.entityRemoved(profile.id());
        roles.unregister(profile);
        profiles.unregister(profile);
        editor.deleteProfile(profile.name());
        diagnostics.accept("NPC_PROFILE_DELETED npc=" + profile.name()
                + " profileId=" + profile.id()
                + " profileFolderDeleted=true runtimeEntitiesRemovedOrAbsent=true");
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
