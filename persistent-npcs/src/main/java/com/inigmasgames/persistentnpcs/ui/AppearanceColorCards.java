package com.inigmasgames.persistentnpcs.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Closed packaged material catalog. No NPC/player data, file writes or client access. */
public final class AppearanceColorCards {
    public static final int WIDTH = 184, HEIGHT = 298, MAX_CARD_BYTES = 128 * 1024;
    public static final int SOURCE_CACHE_LIMIT = 32, PNG_CACHE_LIMIT = 128;
    public static final long PNG_BYTE_LIMIT = 12 * 1024 * 1024;
    private static final String ROOT = "/appearance-color-sources/";
    private final JsonObject entries;
    private final JsonObject palettes;
    private final LinkedHashMap<String, Pixels> sources = new LinkedHashMap<>(32, .75f, true);
    private final LinkedHashMap<String, Rendered> images = new LinkedHashMap<>(128, .75f, true);
    private long cachedBytes;

    public record Request(int slot, String category, String cosmetic, String variant, String color) {
        public Request {
            Objects.requireNonNull(category); Objects.requireNonNull(cosmetic);
            variant = variant == null ? "" : variant;
            color = color == null ? "" : color;
        }
    }
    public record Rendered(byte[] png, boolean selectedColor, String resolvedColor) { }
    private record Pixels(int[] base, int[] mask) { }

    public AppearanceColorCards() {
        JsonObject index = json("index.json");
        if (index.get("version").getAsInt() != 1 || index.get("sourceCount").getAsInt() > 1000)
            throw new IllegalStateException("Unsupported appearance material catalog");
        entries = index.getAsJsonObject("entries");
        byte[] paletteBytes = read("palettes.json");
        verify(paletteBytes, index.get("paletteSha256").getAsString());
        palettes = JsonParser.parseString(new String(paletteBytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Unknown mod cosmetics/variants fall back to the existing card; never synthesize IDs. */
    public synchronized Rendered render(Request request) {
        JsonObject entry = entries.getAsJsonObject(request.category() + ":" + request.cosmetic());
        if (entry == null) return null;
        String variant = request.variant().isEmpty() ? entry.get("defaultVariant").getAsString() : request.variant();
        JsonObject rows = entry.getAsJsonObject("sources");
        JsonObject source = rows.getAsJsonObject(variant + "\t" + request.color());
        boolean directColor = source != null && !request.color().isEmpty();
        if (source == null) source = rows.getAsJsonObject(variant + "\t");
        if (source == null) {
            // A direct texture category has no LUT. Choose its actual default texture.
            String prefix = variant + "\t";
            String first = rows.keySet().stream().filter(k -> k.startsWith(prefix)).sorted().findFirst().orElse(null);
            if (first == null) return null;
            source = rows.getAsJsonObject(first);
        }
        String gradient = source.get("gradient").getAsString();
        var paletteGroup = palettes.getAsJsonObject(gradient);
        var palette = paletteGroup == null ? null : paletteGroup.getAsJsonArray(request.color());
        String resolved = palette != null || directColor ? request.color() : "";
        String baseName = source.get("base").getAsString();
        String cacheKey = baseName + "\t" + resolved;
        Rendered cached = images.get(cacheKey);
        if (cached != null) return cached;
        Pixels material = sources.get(baseName);
        if (material == null) {
            material = new Pixels(pixels(source, "base"), pixels(source, "mask"));
            sources.put(baseName, material);
            while (sources.size() > SOURCE_CACHE_LIMIT) sources.remove(sources.keySet().iterator().next());
        }
        int[] rgb = material.base().clone();
        if (palette != null) {
            if (palette.size() != 768) throw new IllegalStateException("Invalid native palette");
            int[] lut = new int[768];
            for (int i = 0; i < lut.length; i++) {
                lut[i] = palette.get(i).getAsInt();
                if (lut[i] < 0 || lut[i] > 255) throw new IllegalStateException("Invalid palette value");
            }
            for (int i = 0; i < rgb.length; i++) {
                int mask = material.mask()[i];
                if ((mask & 255) != 255 || (mask >>> 24) == 0) continue;
                int gray = (mask >>> 16) & 255, light = (mask >>> 8) & 255;
                rgb[i] = (rgb[i] & 0xff000000) | (lut[gray*3]*light/255 << 16)
                        | (lut[gray*3+1]*light/255 << 8) | (lut[gray*3+2]*light/255);
            }
        }
        try {
            BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, WIDTH, HEIGHT, rgb, 0, WIDTH);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (var memory = new MemoryCacheImageOutputStream(out)) {
                if (!ImageIO.write(image, "png", memory)) throw new IOException("PNG encoder missing");
            }
            byte[] png = out.toByteArray();
            if (png.length > MAX_CARD_BYTES) throw new IOException("Card exceeds byte budget");
            Rendered result = new Rendered(png, !resolved.isEmpty(), resolved);
            images.put(cacheKey, result); cachedBytes += png.length;
            while (images.size() > PNG_CACHE_LIMIT || cachedBytes > PNG_BYTE_LIMIT)
                cachedBytes -= images.remove(images.keySet().iterator().next()).png().length;
            return result;
        } catch (IOException e) { throw new IllegalStateException("Appearance card encoding failed", e); }
    }

    public synchronized int cachedImages() { return images.size(); }
    public synchronized int cachedSources() { return sources.size(); }
    public synchronized long cachedBytes() { return cachedBytes; }
    public int catalogSize() { return entries.size(); }

    private static int[] pixels(JsonObject row, String key) {
        String file = row.get(key).getAsString();
        if (!file.matches("[a-f0-9]{24}(-mask)?\\.png")) throw new IllegalStateException("Invalid material path");
        byte[] data = read(file);
        verify(data, row.get(key + "Sha256").getAsString());
        try (var memory = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            var readers = ImageIO.getImageReaders(memory);
            if (!readers.hasNext()) throw new IOException("Invalid material PNG");
            var reader = readers.next();
            try {
                reader.setInput(memory);
                // Bound dimensions before decompression; own stream lifetime explicitly.
                if (reader.getWidth(0) != WIDTH || reader.getHeight(0) != HEIGHT)
                    throw new IOException("Invalid material dimensions");
                BufferedImage image = reader.read(0);
                return image.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH);
            } finally { reader.dispose(); }
        } catch (IOException e) { throw new IllegalStateException("Invalid material", e); }
    }
    private static JsonObject json(String file) {
        return JsonParser.parseString(new String(read(file), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static byte[] read(String file) {
        try (InputStream in = AppearanceColorCards.class.getResourceAsStream(ROOT + file)) {
            if (in == null) throw new IOException("Missing material " + file);
            byte[] bytes = in.readNBytes(2 * 1024 * 1024 + 1);
            if (bytes.length > 2 * 1024 * 1024) throw new IOException("Oversized material");
            return bytes;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    private static void verify(byte[] bytes, String expected) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!hash.equalsIgnoreCase(expected)) throw new IllegalStateException("Material hash mismatch");
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
