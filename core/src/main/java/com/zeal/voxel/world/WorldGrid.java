package com.zeal.voxel.world;

import com.zeal.voxel.util.Constants;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages the static voxel world as a 2D grid of BlockColumns.
 * Each column owns the full vertical slice (Y=0 to Y=WORLD_HEIGHT-1).
 * Block lookups route through columns and handle out-of-bounds Y safely.
 */
public class WorldGrid {
    private static final int WORLD_MIN_Y = 0;
    private static final int WORLD_MAX_Y_EXCLUSIVE = Constants.WORLD_HEIGHT;
    
    private final Map<Long, BlockColumn> columns = new HashMap<>();
    private final Set<Long> modifiedColumnKeys = new HashSet<>();

    public WorldGrid() {
        // Will be populated externally or by generator
    }

    /**
     * Get block at world position. Coordinates are in integer block units (world space).
     * Out-of-bounds Y (< 0 or >= WORLD_HEIGHT) always returns air (0).
     * Unloaded column (neighbor) always returns air (0).
     */
    public int getBlock(int worldX, int worldY, int worldZ) {
        // Y bounds check first
        if (worldY < WORLD_MIN_Y || worldY >= WORLD_MAX_Y_EXCLUSIVE) {
            return 0;  // Out of bounds = air
        }

        // Find column and local coordinates
        int columnX = Math.floorDiv(worldX, Constants.COLUMN_SIZE);
        int columnZ = Math.floorDiv(worldZ, Constants.COLUMN_SIZE);
        long columnKey = ColumnPosition.key(columnX, columnZ);
        
        BlockColumn column = columns.get(columnKey);
        if (column != null) {
            int localX = Math.floorMod(worldX, Constants.COLUMN_SIZE);
            int localZ = Math.floorMod(worldZ, Constants.COLUMN_SIZE);
            return column.getBlock(localX, worldY, localZ);
        }
        return 0;
    }

    /**
     * Set block at world position. Creates the column if needed on non-air writes.
     * Marks the column as dirty. Marks adjacent columns dirty if setting a boundary block.
     */
    public void setBlock(int worldX, int worldY, int worldZ, int blockId) {
        // Y bounds check
        if (worldY < WORLD_MIN_Y || worldY >= WORLD_MAX_Y_EXCLUSIVE) {
            return;  // Silently ignore out-of-bounds Y writes
        }

        int columnX = Math.floorDiv(worldX, Constants.COLUMN_SIZE);
        int columnZ = Math.floorDiv(worldZ, Constants.COLUMN_SIZE);
        long columnKey = ColumnPosition.key(columnX, columnZ);
        
        BlockColumn column = columns.get(columnKey);
        if (column == null) {
            if (blockId == 0) return;  // Don't create columns just to set air
            column = new BlockColumn(columnX, columnZ);
            columns.put(columnKey, column);
        }

        int localX = Math.floorMod(worldX, Constants.COLUMN_SIZE);
        int localZ = Math.floorMod(worldZ, Constants.COLUMN_SIZE);
        
        column.setBlock(localX, worldY, localZ, blockId);
        modifiedColumnKeys.add(columnKey);
        
        // Mark adjacent columns dirty if setting a boundary block
        if (localX == 0) {
            modifiedColumnKeys.add(ColumnPosition.key(columnX - 1, columnZ));
        } else if (localX == Constants.COLUMN_SIZE - 1) {
            modifiedColumnKeys.add(ColumnPosition.key(columnX + 1, columnZ));
        }

        if (localZ == 0) {
            modifiedColumnKeys.add(ColumnPosition.key(columnX, columnZ - 1));
        } else if (localZ == Constants.COLUMN_SIZE - 1) {
            modifiedColumnKeys.add(ColumnPosition.key(columnX, columnZ + 1));
        }
    }

    /**
     * Remove a block and return its old type. Returns 0 if already air.
     */
    public int removeBlock(int worldX, int worldY, int worldZ) {
        int oldType = getBlock(worldX, worldY, worldZ);
        if (oldType != 0) {
            setBlock(worldX, worldY, worldZ, 0);
        }
        return oldType;
    }

    /**
     * Get column at column grid position (returns null if not loaded).
     */
    public BlockColumn getColumn(int columnX, int columnZ) {
        return columns.get(ColumnPosition.key(columnX, columnZ));
    }

    /**
     * Put column at column grid position.
     */
    public void putColumn(int columnX, int columnZ, BlockColumn column) {
        columns.put(ColumnPosition.key(columnX, columnZ), column);
        modifiedColumnKeys.add(ColumnPosition.key(columnX, columnZ));
    }

    /**
     * Get all currently loaded columns.
     */
    public Map<Long, BlockColumn> getColumns() {
        return columns;
    }

    /**
     * Get the set of column keys that have been modified since last clear.
     */
    public Set<Long> getModifiedColumnKeys() {
        return modifiedColumnKeys;
    }

    /**
     * Clear modified column tracking.
     */
    public void clearModifiedColumnKeys() {
        modifiedColumnKeys.clear();
    }
}
