package com.zeal.voxel.player;

public interface Selector {
    void handleInput();
    VoxelSelection getSelection(); // null if incomplete
    void clear();
}
