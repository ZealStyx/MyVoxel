package com.zeal.voxel.world;

/**
 * 2D grid coordinate for columns. Columns are identified only by (x, z) in column units.
 * No Y coordinate for columns since they span the full world height.
 */
public class ColumnPosition {
    public final int x;
    public final int z;
    
    public ColumnPosition(int x, int z) {
        this.x = x;
        this.z = z;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColumnPosition)) return false;
        ColumnPosition that = (ColumnPosition) o;
        return x == that.x && z == that.z;
    }
    
    @Override
    public int hashCode() {
        return Long.hashCode(key(x, z));
    }
    
    @Override
    public String toString() {
        return "ColumnPos(" + x + "," + z + ")";
    }
    
    /**
     * Convert to a compact long key for HashMap storage.
     */
    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
