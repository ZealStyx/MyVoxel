package com.zeal.voxel.render.ao;

/** Abstraction over any voxel grid (world chunks or physics body) for AO sampling. */
public interface VoxelGrid {
    boolean isSolid(int x, int y, int z);
}
