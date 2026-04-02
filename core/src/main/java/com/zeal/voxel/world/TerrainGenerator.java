package com.zeal.voxel.world;

public interface TerrainGenerator {
    void populate(Chunk chunk, ChunkPosition pos);
}
