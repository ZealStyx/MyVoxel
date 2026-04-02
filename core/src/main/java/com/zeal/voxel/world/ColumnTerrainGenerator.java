package com.zeal.voxel.world;

/**
 * Interface for terrain generators that populate BlockColumns.
 */
public interface ColumnTerrainGenerator {
    /**
     * Populate a BlockColumn with terrain data.
     * The column is at grid position (columnX, columnZ) and spans the full world height.
     */
    void populate(BlockColumn column, int columnX, int columnZ);
}
