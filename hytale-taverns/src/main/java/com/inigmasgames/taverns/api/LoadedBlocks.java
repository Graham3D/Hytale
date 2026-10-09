package com.inigmasgames.taverns.api;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

/** U7P5 section lookup only; never loads a column or blocks an ECS tick. */
public final class LoadedBlocks {
    private LoadedBlocks() { }
    public static BlockSection section(World world,int x,int y,int z) {
        var chunks=world.getChunkStore();
        var ref=chunks.getChunkSectionReferenceAtBlock(x,y,z);
        return ref==null||!ref.isValid()?null:chunks.getStore().getComponent(ref,BlockSection.getComponentType());
    }
    public static int getBlock(World world,int x,int y,int z) {
        var section=section(world,x,y,z);
        return section==null?0:section.get(x,y,z);
    }
    public static BlockType type(World world,int x,int y,int z) {
        var section=section(world,x,y,z);
        return section==null?null:BlockType.getAssetMap().getAsset(section.get(x,y,z));
    }
    public static RotationTuple rotation(World world,int x,int y,int z) {
        var section=section(world,x,y,z);
        return section==null?RotationTuple.NONE:section.getRotation(x,y,z);
    }
    public static int filler(World world,int x,int y,int z) {
        var section=section(world,x,y,z);
        return section==null?FillerBlockUtil.NO_FILLER:section.getFiller(x,y,z);
    }
}
