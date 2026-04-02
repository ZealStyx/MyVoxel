package com.zeal.voxel.render.culling;

import com.badlogic.gdx.graphics.Mesh;
import com.zeal.voxel.util.Constants;
import com.zeal.voxel.world.ChunkPosition;

import java.util.HashMap;
import java.util.Map;

public class SubChunkSection {
    public static final int SECTION_SIZE = 8;

    public final ChunkPosition chunkPosition;
    public final int localX;
    public final int localY;
    public final int localZ;

    public final float centerX;
    public final float centerY;
    public final float centerZ;

    public Mesh mesh;
    public final Map<Integer, Mesh> meshesByBlockType = new HashMap<>();

    public boolean isEmpty = true;
    public boolean isDirty = true;

    public SubChunkSection(ChunkPosition chunkPosition, int localX, int localY, int localZ) {
        this.chunkPosition = chunkPosition;
        this.localX = localX;
        this.localY = localY;
        this.localZ = localZ;

        float baseX = chunkPosition.x * Constants.CHUNK_SIZE + localX;
        float baseY = chunkPosition.y * Constants.CHUNK_SIZE + localY;
        float baseZ = chunkPosition.z * Constants.CHUNK_SIZE + localZ;

        float half = SECTION_SIZE * 0.5f;
        this.centerX = baseX + half;
        this.centerY = baseY + half;
        this.centerZ = baseZ + half;
    }

    public int index() {
        return (localX / SECTION_SIZE)
                + (localY / SECTION_SIZE) * 2
                + (localZ / SECTION_SIZE) * 4;
    }
}
