package com.inigmasgames.hytalerpg.diagnostics;

import java.nio.file.Path;

/** Repository/operator CLI for expanding archived traces into ordinary legacy JSONL. */
public final class TraceArchiveExport {
    private TraceArchiveExport() {}
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) {
            throw new IllegalArgumentException("Usage: TraceArchiveExport <trace-directory> <SKILL|UI> <output.jsonl>");
        }
        TraceArchiveReader.exportLegacyJsonl(Path.of(arguments[0]), arguments[1], Path.of(arguments[2]));
    }
}
