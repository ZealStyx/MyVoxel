package com.zeal.voxel.world;

import com.zeal.voxel.render.culling.SubChunkSection;
import com.zeal.voxel.util.Constants;

import java.util.Arrays;

/** Represents a 16x16x16 localized grid of blocks. */
public class Chunk {
    public static final int LOD_NEAR = 0;
    public static final int LOD_MID = 1;
    public static final int LOD_FAR = 2;

    private final byte[] blocks;
    private boolean dirty = true; // For mesher
    private boolean occlusionDirty = true;
    private ChunkPosition chunkPosition;
    private SubChunkSection[] sections = new SubChunkSection[0];
    public int currentLOD = LOD_NEAR;
    public boolean isOccluded = false;

    public Chunk() {
        blocks = new byte[Constants.CHUNK_SIZE_CUBED];
    }

    public void initializeSections(ChunkPosition chunkPosition) {
        this.chunkPosition = chunkPosition;
        this.sections = new SubChunkSection[8];

        int idx = 0;
        for (int sx = 0; sx <= 8; sx += 8) {
            for (int sy = 0; sy <= 8; sy += 8) {
                for (int sz = 0; sz <= 8; sz += 8) {
                    sections[idx++] = new SubChunkSection(chunkPosition, sx, sy, sz);
                }
            }
        }
    }

    public ChunkPosition getChunkPosition() {
        return chunkPosition;
    }

    public SubChunkSection[] getSections() {
        return sections;
    }

    public int getBlock(int x, int y, int z) {
        if (!isValid(x, y, z)) return 0;
        return blocks[getIndex(x, y, z)];
    }

    public void setBlock(int x, int y, int z, int type) {
        if (!isValid(x, y, z)) return;
        blocks[getIndex(x, y, z)] = (byte) type;
        dirty = true;
        occlusionDirty = true;
        markSectionsDirtyForVoxel(x, y, z);
    }

    private boolean isValid(int x, int y, int z) {
        return x >= 0 && x < Constants.CHUNK_SIZE &&
               y >= 0 && y < Constants.CHUNK_SIZE &&
               z >= 0 && z < Constants.CHUNK_SIZE;
    }

    private int getIndex(int x, int y, int z) {
        return x + (y * Constants.CHUNK_SIZE) + (z * Constants.CHUNK_SIZE_SQUARED);
    }
    
    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
        if (dirty) {
            Arrays.stream(sections).forEach(section -> section.isDirty = true);
        }
    }

    public void setCurrentLOD(int lod) {
        if (this.currentLOD == lod) {
            return;
        }
        this.currentLOD = lod;
        setDirty(true);
    }

    public boolean isOcclusionDirty() {
        return occlusionDirty;
    }

    public void markOcclusionDirty() {
        this.occlusionDirty = true;
    }

    public void setOccluded(boolean occluded) {
        this.isOccluded = occluded;
        this.occlusionDirty = false;
    }

    private void markSectionsDirtyForVoxel(int x, int y, int z) {
        SubChunkSection owner = sectionForLocalVoxel(x, y, z);
        if (owner != null) {
            owner.isDirty = true;
        }

        if (x == 7) markSectionDirtyAt(8, y, z);
        else if (x == 8) markSectionDirtyAt(0, y, z);

        if (y == 7) markSectionDirtyAt(x, 8, z);
        else if (y == 8) markSectionDirtyAt(x, 0, z);

        if (z == 7) markSectionDirtyAt(x, y, 8);
        else if (z == 8) markSectionDirtyAt(x, y, 0);
    }

    private void markSectionDirtyAt(int x, int y, int z) {
        SubChunkSection section = sectionForLocalVoxel(x, y, z);
        if (section != null) {
            section.isDirty = true;
        }
    }

    private SubChunkSection sectionForLocalVoxel(int x, int y, int z) {
        if (sections == null || sections.length == 0) {
            return null;
        }
        int sx = x < 8 ? 0 : 1;
        int sy = y < 8 ? 0 : 1;
        int sz = z < 8 ? 0 : 1;
        int idx = sx * 4 + sy * 2 + sz;
        if (idx < 0 || idx >= sections.length) {
            return null;
        }
        return sections[idx];
    }
}
