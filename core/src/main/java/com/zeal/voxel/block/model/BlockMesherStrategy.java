package com.zeal.voxel.block.model;

import com.zeal.voxel.render.ao.FaceDirection;

public interface BlockMesherStrategy {
    @FunctionalInterface
    interface NeighbourSolidChecker {
        boolean isSolid(FaceDirection face);
    }

    void emitBlock(MeshBuilder builder,
                   int blockId,
                   int worldX,
                   int worldY,
                   int worldZ,
                   NeighbourSolidChecker neighbours);
}
