package com.zeal.voxel.render.culling;

import com.zeal.voxel.world.ChunkPosition;

import java.util.Collections;
import java.util.List;

public class ChunkVisibility {
    public final ChunkPosition chunkPosition;
    public final List<SubChunkSection> visibleSections;

    public ChunkVisibility(ChunkPosition chunkPosition, List<SubChunkSection> visibleSections) {
        this.chunkPosition = chunkPosition;
        this.visibleSections = Collections.unmodifiableList(visibleSections);
    }
}
