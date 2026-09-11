package com.inigmasgames.hytalerpg.diagnostics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/** The single trace input abstraction for legacy JSONL and verified GZIP JSONL. */
public final class TraceInput {
    private TraceInput() {}

    public static InputStream openBytes(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        if (path.getFileName().toString().endsWith(".gz")) {
            try {
                return new GZIPInputStream(input);
            } catch (IOException failure) {
                input.close();
                throw failure;
            }
        }
        return input;
    }

    public static BufferedReader open(Path path) throws IOException {
        return new BufferedReader(new java.io.InputStreamReader(openBytes(path), StandardCharsets.UTF_8));
    }
}
