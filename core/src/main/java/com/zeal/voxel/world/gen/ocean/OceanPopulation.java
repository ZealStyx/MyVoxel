package com.zeal.voxel.world.gen.ocean;

import com.zeal.voxel.block.BlockType;
import com.zeal.voxel.world.BlockColumn;
import com.zeal.voxel.world.gen.SeedMixer;

/**
 * Lightweight deterministic post-pass for extra seabed detail.
 */
public final class OceanPopulation {
    private static final long POP_SALT = 0x504F50554C4154L;
    private static final long IRON_SALT = 0x49524F4E564549L;

    private final long worldSeed;
    private final OceanTerrainSettings settings;

    public OceanPopulation(long worldSeed, OceanTerrainSettings settings) {
        this.worldSeed = worldSeed;
        this.settings = settings;
    }

    public void populateColumn(BlockColumn column, OceanColumnSample[][] samples) {
        for (int lx = 0; lx < samples.length; lx++) {
            for (int lz = 0; lz < samples[lx].length; lz++) {
                OceanColumnSample sample = samples[lx][lz];
                int topY = (int) Math.floor(sample.surfaceHeight);

                long popHash = coordHash(POP_SALT, sample.worldX, sample.worldZ);
                if ((popHash & 0xFFL) < 10 && topY < settings.seaLevel - 8) {
                    // Small dark-stone seabed patch in deeper water.
                    column.setBlock(lx, clampY(topY), lz, BlockType.DARK_STONE.getId());
                }

                long ironHash = coordHash(IRON_SALT, sample.worldX, sample.worldZ);
                if ((ironHash & 0x3FFL) == 0) {
                    int oreY = Math.max(6, Math.min(topY - 6, settings.seaLevel - 20));
                    if (oreY > 5) {
                        column.setBlock(lx, clampY(oreY), lz, BlockType.IRON.getId());
                    }
                }
            }
        }
    }

    private long coordHash(long salt, int x, int z) {
        long a = SeedMixer.mix(worldSeed, salt);
        long b = SeedMixer.mix(((long) x << 32) ^ (z & 0xFFFFFFFFL), salt ^ 0x9E3779B97F4A7C15L);
        return a ^ b;
    }

    private static int clampY(int y) {
        if (y < 0) {
            return 0;
        }
        return Math.min(y, 511);
    }
}
