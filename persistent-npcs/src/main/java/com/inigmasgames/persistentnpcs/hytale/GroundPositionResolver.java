package com.inigmasgames.persistentnpcs.hytale;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockReader;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn;
import java.util.Optional;
import org.joml.Vector3d;

/** Resolves feet-on-terrain positions from already-loaded authoritative chunks. */
public final class GroundPositionResolver {
    private GroundPositionResolver() { }

    public static Optional<Vector3d> resolve(World world, Vector3d candidate) {
        if (world == null || candidate == null) return Optional.empty();
        int x = (int) Math.floor(candidate.x);
        int z = (int) Math.floor(candidate.z);
        WorldChunk chunk = world.getChunkStore().getChunkComponent(
                ChunkUtil.indexChunkFromBlock(x, z), WorldChunk.getComponentType());
        if (chunk == null) return Optional.empty();
        int localX = ChunkUtil.localCoordinate(x);
        int localZ = ChunkUtil.localCoordinate(z);
        HeightmapColumn heights = world.getChunkStore().getStore().getComponent(
                chunk.getReference(), HeightmapColumn.getComponentType());
        if (heights == null) return Optional.empty();
        int height = heights.getHeight(localX, localZ);
        BlockReader blocks = new SectionReader(world.getChunkStore());
        int preferred = (int) Math.floor(candidate.y) - 1;
        int top = Math.min(ChunkUtil.HEIGHT_MINUS_1,
                Math.max(height + 3, preferred));
        int bottom = Math.max(ChunkUtil.MIN_Y, Math.min(height - 6, preferred - 96));
        for (int groundY = top; groundY >= bottom; groundY--) {
            if (walkable(blocks, x, groundY, z)) {
                return Optional.of(new Vector3d(candidate.x, groundY + 1.0, candidate.z));
            }
        }
        return Optional.empty();
    }

    public static boolean isGrounded(World world, Vector3d feet) {
        return resolve(world, feet).map(resolved -> Math.abs(resolved.y - feet.y) <= 0.35)
                .orElse(false);
    }

    private static boolean walkable(BlockReader chunk, int x, int y, int z) {
        BlockType ground = BlockType.getAssetMap().getAsset(chunk.getBlock(x, y, z));
        BlockType feet = BlockType.getAssetMap().getAsset(chunk.getBlock(x, y + 1, z));
        BlockType head = BlockType.getAssetMap().getAsset(chunk.getBlock(x, y + 2, z));
        return ground != null && ground.getMaterial() == BlockMaterial.Solid
                && empty(feet) && empty(head);
    }

    private static boolean empty(BlockType block) {
        return block == null || block == BlockType.EMPTY
                || block.getMaterial() == BlockMaterial.Empty;
    }
}
