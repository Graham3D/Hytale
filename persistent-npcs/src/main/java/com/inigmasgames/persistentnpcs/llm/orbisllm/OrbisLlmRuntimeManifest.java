package com.inigmasgames.persistentnpcs.llm.orbisllm;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Verified allow-list for the pinned native runtime, model, template, and profiles. */
public record OrbisLlmRuntimeManifest(
        int manifestVersion, int protocolMajor, int protocolMinor, String platform,
        String runtimeBuild, String llamaTag, String llamaCommit, String executablePath,
        String runtimeDirectory, Model model, Template template,
        Map<String, Profile> profiles, List<Binary> binaries) {
    public static final String PINNED_COMMIT =
            "cc231cb0da565440cf6a3e5b55dfeba477972cb6";
    public static final String PINNED_TEMPLATE =
            "ab7813c3abdd9cb655905a410728b26c7884eca45ddfab8d9f931553485a7862";
    public static final String PINNED_MODEL =
            "527db2cf6c705d8fabb95693d038d9c06b4a2b0b8b0a4bbdbd01212d37242970";

    public record Model(String id, String path, String sha256, long bytes,
            String quantization, long parameterCount) { }
    public record Template(String revision, String sha256, String renderer) { }
    public record Profile(int gpuLayers, int contextSize, int batchSize,
            int microbatchSize, int threads) { }
    public record Binary(String name, String path, long bytes, String sha256) { }

    public static Loaded loadVerified(Path path) {
        try {
            Path canonical = path.toAbsolutePath().normalize();
            if (!Files.isRegularFile(canonical)) {
                throw new IllegalStateException("OrbisLLM runtime manifest is missing: " + canonical);
            }
            String manifestHash = sha256(canonical);
            OrbisLlmRuntimeManifest value = JsonFiles.read(canonical,
                    OrbisLlmRuntimeManifest.class);
            value.validate();
            verify(Path.of(value.executablePath()), value.binaries().stream()
                    .filter(binary -> "OrbisLLM.exe".equalsIgnoreCase(binary.name()))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "OrbisLLM executable is absent from manifest allow-list")));
            verifyModel(value.model());
            for (Binary binary : value.binaries()) verify(Path.of(binary.path()), binary);
            return new Loaded(canonical, manifestHash, value);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not verify OrbisLLM runtime", failure);
        }
    }

    private void validate() {
        if (manifestVersion != 1 || protocolMajor != OrbisLlmProtocol.MAJOR
                || protocolMinor != OrbisLlmProtocol.MINOR
                || !PINNED_COMMIT.equalsIgnoreCase(llamaCommit)
                || model == null || !PINNED_MODEL.equalsIgnoreCase(model.sha256())
                || template == null || !PINNED_TEMPLATE.equalsIgnoreCase(template.sha256())
                || profiles == null || !profiles.containsKey("BALANCED")) {
            throw new IllegalStateException("OrbisLLM manifest does not match the pinned Phase 1 runtime");
        }
        Profile profile = profiles.get("BALANCED");
        if (profile.gpuLayers() != 4 || profile.contextSize() != 4096
                || profile.batchSize() <= 0 || profile.microbatchSize() <= 0
                || profile.threads() <= 0) {
            throw new IllegalStateException("OrbisLLM BALANCED profile violates R064 parity");
        }
    }

    private static void verifyModel(Model model) throws IOException {
        Path path = Path.of(model.path());
        if (!Files.isRegularFile(path) || Files.size(path) != model.bytes()
                || !sha256(path).equalsIgnoreCase(model.sha256())) {
            throw new IllegalStateException("Pinned Nemotron model hash/size mismatch: " + path);
        }
    }

    private static void verify(Path path, Binary binary) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) != binary.bytes()
                || !sha256(path).equalsIgnoreCase(binary.sha256())) {
            throw new IllegalStateException("OrbisLLM binary hash/size mismatch: " + path);
        }
    }

    public static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[1024 * 1024];
                for (int count; (count = input.read(buffer)) >= 0;) {
                    if (count > 0) digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Loaded(Path path, String sha256, OrbisLlmRuntimeManifest manifest) { }
}
