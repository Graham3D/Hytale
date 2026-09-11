package com.inigmasgames.hytalerpg.diagnostics;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Discovers verified archives and reconstructs the legacy ordered JSONL byte stream. */
public final class TraceArchiveReader {
    private static final Pattern VERIFIED_STATE = Pattern.compile("\\\"archiveState\\\"\\s*:\\s*\\\"VERIFIED\\\"");
    private static final Pattern SEGMENT_SEQUENCE = Pattern.compile("\\\"segmentSequence\\\"\\s*:\\s*(\\d+)");
    private TraceArchiveReader() {}

    public static List<Path> discover(Path traceDirectory, String traceKind) throws IOException {
        if (Files.isRegularFile(traceDirectory)) return List.of(traceDirectory);
        String base = traceKind.toLowerCase(Locale.ROOT) + "-trace";
        Path active = traceDirectory.resolve(base + ".jsonl");
        var result = new ArrayList<Path>();

        // Legacy numbered rotation uses .1 as newest, so read the largest suffix first.
        Pattern legacy = Pattern.compile(Pattern.quote(base + ".jsonl") + "\\.(\\d+)");
        if (Files.isDirectory(traceDirectory)) {
            try (var paths = Files.list(traceDirectory)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> legacy.matcher(path.getFileName().toString()).matches())
                        .sorted(Comparator.comparingInt((Path path) -> {
                            var match = legacy.matcher(path.getFileName().toString());
                            return match.matches() ? Integer.parseInt(match.group(1)) : 0;
                        }).reversed())
                        .forEach(result::add);
            }
        }

        Path archive = traceDirectory.resolve("archive");
        if (Files.isDirectory(archive)) {
            var verified = new ArrayList<ArchiveEntry>();
            try (var paths = Files.list(archive)) {
                for (Path manifestPath : paths.filter(path -> path.getFileName().toString().startsWith(base + "-"))
                        .filter(path -> path.getFileName().toString().endsWith(".manifest.json")).toList()) {
                    try {
                        String manifest = Files.readString(manifestPath);
                        var sequence = SEGMENT_SEQUENCE.matcher(manifest);
                        if (!VERIFIED_STATE.matcher(manifest).find() || !sequence.find()) continue;
                        String stem = manifestPath.getFileName().toString().replace(".manifest.json", "");
                        Path gzip = archive.resolve(stem + ".jsonl.gz");
                        Path plain = archive.resolve(stem + ".jsonl");
                        Path artifact = Files.isRegularFile(gzip) ? gzip : plain;
                        if (Files.isRegularFile(artifact)) verified.add(new ArchiveEntry(Long.parseLong(sequence.group(1)), artifact));
                    } catch (RuntimeException ignored) {
                        // A malformed/unverified manifest is recovery input, not normal reader input.
                    }
                }
            }
            verified.stream().sorted(Comparator.comparingLong(ArchiveEntry::sequence)).map(ArchiveEntry::path).forEach(result::add);
        }
        if (Files.isRegularFile(active)) result.add(active);
        return List.copyOf(result);
    }

    public static void exportLegacyJsonl(Path traceDirectory, String traceKind, Path output) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (OutputStream target = Files.newOutputStream(output, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            for (Path segment : discover(traceDirectory, traceKind)) {
                try (InputStream input = TraceInput.openBytes(segment)) {
                    input.transferTo(target);
                }
            }
        }
    }

    private record ArchiveEntry(long sequence, Path path) {}
}
