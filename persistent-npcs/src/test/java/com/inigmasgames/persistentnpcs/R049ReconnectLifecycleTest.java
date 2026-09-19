package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Regression coverage for menu-exit/local-server reconnect worker ownership. */
public final class R049ReconnectLifecycleTest {
    private static final String WORKER = "/tools/immersive_voice_worker.py";

    private R049ReconnectLifecycleTest() { }

    public static void main(String[] args) throws Exception {
        identicalInstallIsARealNoOp();
        concurrentInstallIsSerializedAndClean();
        startupAndShutdownOwnTheWorkerLifecycle();
        System.out.println("R049 reconnect lifecycle tests passed.");
    }

    private static void identicalInstallIsARealNoOp() throws Exception {
        Path directory = Files.createTempDirectory("immersive-r049-install-");
        try {
            Path target = directory.resolve("immersive_voice_worker.py");
            JsonFiles.copyResourceReplacing(JsonFiles.class, WORKER, target);
            byte[] expected = Files.readAllBytes(target);
            FileTime sentinel = FileTime.fromMillis(978_307_200_000L);
            Files.setLastModifiedTime(target, sentinel);

            try (FileChannel openWorker = FileChannel.open(target, StandardOpenOption.READ);
                    var ignored = openWorker.lock(0L, Long.MAX_VALUE, true)) {
                JsonFiles.copyResourceReplacing(JsonFiles.class, WORKER, target);
            }

            assert Files.getLastModifiedTime(target).equals(sentinel)
                    : "identical reconnect install rewrote the live worker";
            assert java.util.Arrays.equals(expected, Files.readAllBytes(target));
        } finally {
            deleteTree(directory);
        }
    }

    private static void concurrentInstallIsSerializedAndClean() throws Exception {
        Path directory = Files.createTempDirectory("immersive-r049-concurrent-");
        try {
            Path target = directory.resolve("immersive_voice_worker.py");
            List<CompletableFuture<Void>> installs = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                installs.add(CompletableFuture.runAsync(() ->
                        JsonFiles.copyResourceReplacing(JsonFiles.class, WORKER, target)));
            }
            CompletableFuture.allOf(installs.toArray(CompletableFuture[]::new)).join();
            assert Files.isRegularFile(target);
            try (var files = Files.list(directory)) {
                assert files.noneMatch(path -> path.getFileName().toString().contains(".install-"))
                        : "temporary installer file leaked";
            }
        } finally {
            deleteTree(directory);
        }
    }

    private static void startupAndShutdownOwnTheWorkerLifecycle() throws Exception {
        String plugin = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/PersistentNpcsPlugin.java"));
        int install = plugin.indexOf("voicePresets.installWorkerScript()");
        int providers = plugin.indexOf("AiServiceRouterFactory.createSelectable");
        assert install >= 0 && providers > install
                : "worker must be installed before provider processes launch";

        String repository = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/voice/VoicePresetRepository.java"));
        assert repository.contains("public void installWorkerScript()")
                : "worker install must be explicit and reusable";

        String worker = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/voice/TurboVoiceWorker.java"));
        assert worker.contains("process.waitFor(2, TimeUnit.SECONDS)")
                : "shutdown must wait for the acknowledged worker exit";
        assert worker.contains("process.destroyForcibly()")
                : "shutdown needs a bounded last-resort termination";
        assert worker.contains("closed.compareAndSet(false, true)")
                : "worker close must be idempotent";
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
