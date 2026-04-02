package com.zeal.voxel.world.gen.ocean;

import com.zeal.voxel.block.BlockType;

/**
 * Material rules for seabed, beaches, and inland top layers.
 */
public final class OceanSurfaceRules {
    private final OceanTerrainSettings settings;

    public OceanSurfaceRules(OceanTerrainSettings settings) {
        this.settings = settings;
    }

    public BlockType pickSolidBlock(OceanColumnSample column, int worldY) {
        int topY = (int) Math.floor(column.surfaceHeight);
        int depth = topY - worldY;

        if (depth <= 0) {
            return topBlock(column, topY);
        }

        if (isBeachBand(column, topY)) {
            if (depth <= settings.beachBandDepth) {
                return BlockType.SAND;
            }
            if (depth <= settings.gravelDepth) {
                return BlockType.GRAVEL;
            }
            return BlockType.STONE;
        }

        if (isUnderwater(topY)) {
            if (column.region == OceanRegion.DEEP_OCEAN) {
                if (depth <= 1) {
                    return BlockType.GRAVEL;
                }
                return depth <= settings.gravelDepth ? BlockType.DARK_STONE : BlockType.STONE;
            }
            if (depth <= 2) {
                return BlockType.SAND;
            }
            return depth <= settings.gravelDepth ? BlockType.GRAVEL : BlockType.STONE;
        }

        // Inland
        if (depth <= settings.dirtDepth) {
            return BlockType.DIRT;
        }
        return BlockType.STONE;
    }

    public BlockType fluidFor(int worldY) {
        return worldY <= settings.seaLevel ? BlockType.WATER : BlockType.AIR;
    }

    private BlockType topBlock(OceanColumnSample column, int topY) {
        if (isBeachBand(column, topY)) {
            return BlockType.SAND;
        }
        if (isUnderwater(topY)) {
            return column.region == OceanRegion.DEEP_OCEAN ? BlockType.GRAVEL : BlockType.SAND;
        }
        return BlockType.GRASS;
    }

    private boolean isBeachBand(OceanColumnSample column, int topY) {
        if (topY < settings.seaLevel - 2 || topY > settings.seaLevel + 3) {
            return false;
        }
        return column.region == OceanRegion.NEARSHORE || column.region == OceanRegion.COAST;
    }

    private boolean isUnderwater(int topY) {
        return topY < settings.seaLevel;
    }
}
