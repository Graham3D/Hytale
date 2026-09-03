package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.npc.commands.NPCCommand;
import com.hypixel.hytale.server.npc.commands.NPCSpawnCommand;
import com.inigmasgames.persistentnpcs.compat.NativeNpcCommandCompatibility;
import com.inigmasgames.persistentnpcs.hytale.ImmersiveNpcRoleService;
import com.inigmasgames.persistentnpcs.hytale.ManagedNpcRoles;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class R027NativeNpcIntegrationTest {
    private R027NativeNpcIntegrationTest() { }

    public static void main(String[] args) throws Exception {
        nativeRootReplacementPreservesSpawnAndRestores();
        profilesBecomeNativeRolesThroughTheRoleRegistrar();
        sourceContainsNoCompetingCommandRoots();
        System.out.println("R027.1 native /npc and role integration tests passed.");
    }

    private static void nativeRootReplacementPreservesSpawnAndRestores() {
        CommandManager manager = new CommandManager();
        NPCCommand original = new NPCCommand();
        manager.registerSystemCommand(original);
        List<String> nativeCommands = original.getSubCommands().keySet().stream()
                .sorted().toList();
        assert original.getSubCommand("spawn") instanceof NPCSpawnCommand;

        NativeNpcCommandCompatibility shim = new NativeNpcCommandCompatibility(
                manager, ignored -> { }, ignored -> { });
        shim.install(new DummyCommand("create"), new DummyCommand("update"),
                new DummyCommand("trace"));
        assert shim.installed();
        AbstractCommand combined = manager.getCommandRegistration().get("npc");
        assert combined != original;
        assert combined.getSubCommand("spawn") instanceof NPCSpawnCommand;
        assert combined.getSubCommand("create") != null;
        assert combined.getSubCommand("update") != null;
        assert combined.getSubCommand("trace") != null;
        assert combined.getSubCommand("place") == null;
        assert combined.getSubCommand("add") == null;
        assert combined.getSubCommands().keySet().stream()
                .filter(name -> !name.equals("create") && !name.equals("update")
                        && !name.equals("trace"))
                .sorted().toList().equals(nativeCommands);

        shim.close();
        assert manager.getCommandRegistration().get("npc") == original;
        manager.shutdown();
    }

    private static void profilesBecomeNativeRolesThroughTheRoleRegistrar() throws Exception {
        Path data = Files.createTempDirectory("immersive-native-role");
        ProfileRepository repository = new ProfileRepository(data);
        NpcProfileRegistry profiles = new NpcProfileRegistry(repository);
        NpcProfile mara = profile("Mara");
        profiles.register(mara);
        ArrayList<String> registered = new ArrayList<>();
        ImmersiveNpcRoleService roles = new ImmersiveNpcRoleService(
                data, profiles,
                (roleName, roleFile) -> {
                    assert Files.isRegularFile(roleFile);
                    registered.add(roleName);
                }, ignored -> { });
        roles.registerAll();
        assert registered.equals(List.of("Mara"));
        Path roleFile = data.resolve("profiles/Mara/native-role/Mara.json");
        assert Files.isRegularFile(roleFile);
        String json = Files.readString(roleFile);
        assert json.contains("\"Type\": \"Generic\"");
        assert json.contains("ImmersiveNPCs_Mara");
        assert roles.profileForRole("mara").orElseThrow().id().equals(mara.id());
        assert ManagedNpcRoles.contains("Mara");
    }

    private static void sourceContainsNoCompetingCommandRoots() throws Exception {
        Path source = Path.of("src/main/java/com/inigmasgames/persistentnpcs");
        String plugin = Files.readString(source.resolve("PersistentNpcsPlugin.java"));
        String shim = Files.readString(source.resolve(
                "compat/NativeNpcCommandCompatibility.java"));
        String page = Files.readString(source.resolve("ui/NpcProfilePage.java"));
        assert !Files.exists(source.resolve("command/AiNpcCommand.java"));
        assert !Files.exists(source.resolve("command/NpcCommand.java"));
        assert !plugin.contains("registerCommand(new NpcCommand");
        assert !plugin.contains("registerCommand(new AiNpcCommand");
        assert shim.contains("new NPCCommand()");
        assert shim.contains("nativeTree(existing)");
        assert shim.contains("NPCSpawnCommand");
        assert page.contains("Type \\\"/npc spawn ");
        assert !page.contains("/npc place");
    }

    private static NpcProfile profile(String name) {
        return new NpcProfile(UUID.randomUUID(), name, "Blacksmith", "Observant",
                "A persistent village resident.", "Live truthfully.", "", "",
                List.of(), List.of(), List.of(), List.of(), 0).validated();
    }

    private static final class DummyCommand extends AbstractCommand {
        private DummyCommand(String name) {
            super(name, "test");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext context) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
