package com.zeal.voxel.world;

import com.zeal.voxel.block.BlockType;
import com.zeal.voxel.util.Constants;
import com.zeal.voxel.util.TerrainConstants;

public class FlatTerrainGenerator implements TerrainGenerator {

    @Override
    public void populate(Chunk chunk, ChunkPosition pos) {
        // We map the world's Y=0 to local Y = GROUND_DEPTH to fit within the 16x16x16 chunk.
        // Therefore, grass is at local Y = GROUND_DEPTH.
        int localSurfaceY = TerrainConstants.GROUND_DEPTH;
        
        for (int x = 0; x < Constants.CHUNK_SIZE; x++) {
            for (int z = 0; z < Constants.CHUNK_SIZE; z++) {
                // Grass at surface
                chunk.setBlock(x, localSurfaceY, z, BlockType.GRASS.getId());
                
                // Dirt 1-3 blocks below surface
                chunk.setBlock(x, localSurfaceY - 1, z, BlockType.DIRT.getId());
                chunk.setBlock(x, localSurfaceY - 2, z, BlockType.DIRT.getId());
                chunk.setBlock(x, localSurfaceY - 3, z, BlockType.DIRT.getId());
                
                // Stone below that, down to local Y=0 (which will be world Y=-GROUND_DEPTH)
                for (int y = localSurfaceY - 4; y >= 0; y--) {
                    chunk.setBlock(x, y, z, BlockType.STONE.getId());
                }
                
                // Air above surface (defaults to 0, so no action needed)
            }
        }
        
        // Add a floating stone block at center of chunk (0,0) at world Y=5 (local Y=13)
        if (pos.x == 0 && pos.z == 0) {
            chunk.setBlock(8, localSurfaceY + 5, 8, BlockType.STONE.getId());
        }
    }
}
