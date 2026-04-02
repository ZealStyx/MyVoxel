package com.zeal.voxel.world;

import java.util.Objects;

/** Immutable chunk grid position */
public class ChunkPosition {
    public final int x;
    public final int y;
    public final int z;

    public ChunkPosition(int x, int z) {
        this(x, 0, z);
    }

    public ChunkPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int chebyshevDist(ChunkPosition other) {
        return Math.max(Math.abs(x - other.x), Math.abs(z - other.z));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkPosition that = (ChunkPosition) o;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}
