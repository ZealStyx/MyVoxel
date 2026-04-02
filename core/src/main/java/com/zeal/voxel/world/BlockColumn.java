package com.zeal.voxel.world;

import com.zeal.voxel.util.Constants;

/**
 * Represents a 16×16×WORLD_HEIGHT column of blocks.
 * Owns the full vertical slice of the world from Y=0 to Y=WORLD_HEIGHT-1.
 * Storage uses Y-major ordering for cache efficiency during generation and meshing.
 */
public class BlockColumn {
    public final int columnX;
    public final int columnZ;
    
    private final short[] blocks;
    private boolean dirty = true;
    
    public BlockColumn(int columnX, int columnZ) {
        this.columnX = columnX;
        this.columnZ = columnZ;
        this.blocks = new short[Constants.COLUMN_SIZE * Constants.COLUMN_SIZE * Constants.WORLD_HEIGHT];
    }
    
    /**
     * Get block at local position. Local coordinates must be in valid ranges:
     * localX, localZ in [0, COLUMN_SIZE), localY in [0, WORLD_HEIGHT)
     */
    public int getBlock(int localX, int localY, int localZ) {
        if (!isValidLocal(localX, localY, localZ)) {
            return 0;
        }
        return blocks[getIndexYMajor(localX, localY, localZ)] & 0xFFFF;
    }
    
    /**
     * Set block at local position.
     */
    public void setBlock(int localX, int localY, int localZ, int blockId) {
        if (!isValidLocal(localX, localY, localZ)) {
            return;
        }
        blocks[getIndexYMajor(localX, localY, localZ)] = (short) blockId;
        dirty = true;
    }
    
    /**
     * Check if all blocks in the column are air (block ID 0).
     */
    public boolean isEmpty() {
        for (short b : blocks) {
            if (b != 0) return false;
        }
        return true;
    }
    
    public int worldX(int localX) {
        return columnX * Constants.COLUMN_SIZE + localX;
    }
    
    public int worldZ(int localZ) {
        return columnZ * Constants.COLUMN_SIZE + localZ;
    }
    
    public boolean isDirty() {
        return dirty;
    }
    
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    /**
     * Extract a chunk-height slice from this column for chunk-based collision building.
     * sliceY is a world Y start and should usually be a multiple of CHUNK_SIZE.
     */
    public Chunk extractSlice(int sliceY) {
        Chunk slice = new Chunk();
        for (int lx = 0; lx < Constants.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < Constants.CHUNK_SIZE; lz++) {
                for (int dy = 0; dy < Constants.CHUNK_SIZE; dy++) {
                    int worldY = sliceY + dy;
                    if (worldY >= Constants.WORLD_HEIGHT) {
                        break;
                    }
                    int blockId = getBlock(lx, worldY, lz);
                    if (blockId != 0) {
                        slice.setBlock(lx, dy, lz, blockId);
                    }
                }
            }
        }
        return slice;
    }
    
    private boolean isValidLocal(int localX, int localY, int localZ) {
        return localX >= 0 && localX < Constants.COLUMN_SIZE &&
               localY >= 0 && localY < Constants.WORLD_HEIGHT &&
               localZ >= 0 && localZ < Constants.COLUMN_SIZE;
    }
    
    /**
     * Y-major indexing: iterate Y in outer loop for cache efficiency.
     * index = localX * COLUMN_SIZE * WORLD_HEIGHT + localZ * WORLD_HEIGHT + localY
     */
    private int getIndexYMajor(int localX, int localY, int localZ) {
        return localX * Constants.COLUMN_SIZE * Constants.WORLD_HEIGHT +
               localZ * Constants.WORLD_HEIGHT +
               localY;
    }
}
