package com.inigmasgames.taverns;

import com.hypixel.hytale.math.util.ChunkUtil;
import java.util.LinkedHashSet;
import java.util.Set;
import org.joml.Vector3i;

/** Inclusive, axis-aligned block bounds. */
public record Cuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static Cuboid normalized(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Cuboid(
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public static Cuboid fromSelection(Vector3i min, Vector3i max) {
        return normalized(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    public long volume() {
        return (long) (maxX - minX + 1)
                * (long) (maxY - minY + 1)
                * (long) (maxZ - minZ + 1);
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean intersects(Cuboid other) {
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public Set<Long> intersectedChunks() {
        Set<Long> chunks = new LinkedHashSet<>();
        int minChunkX = ChunkUtil.chunkCoordinate(minX);
        int maxChunkX = ChunkUtil.chunkCoordinate(maxX);
        int minChunkZ = ChunkUtil.chunkCoordinate(minZ);
        int maxChunkZ = ChunkUtil.chunkCoordinate(maxZ);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks.add(ChunkUtil.indexChunk(chunkX, chunkZ));
            }
        }
        return Set.copyOf(chunks);
    }

    public String encode() {
        return minX + "," + minY + "," + minZ + "," + maxX + "," + maxY + "," + maxZ;
    }

    public static Cuboid decode(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 6) {
            throw new IllegalArgumentException("Expected six cuboid coordinates");
        }
        return normalized(
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
    }
}

