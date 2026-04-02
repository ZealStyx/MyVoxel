package com.zeal.voxel.world.gen.ocean;

import com.zeal.voxel.block.BlockType;
import com.zeal.voxel.util.Constants;
import com.zeal.voxel.world.BlockColumn;

/**
 * End-to-end deterministic ocean/coast generator (no caves).
 */
public final class OceanCoastWorldGenerator {
    private final OceanTerrainSettings settings;
    private final OceanClimateSampler climateSampler;
    private final CoastalProfile coastalProfile;
    private final OceanHeightComposer heightComposer;
    private final OceanDensityFunction densityFunction;
    private final OceanSurfaceRules surfaceRules;
    private final OceanPopulation population;

    public OceanCoastWorldGenerator(long worldSeed) {
        this.settings = new OceanTerrainSettings();
        this.climateSampler = new OceanClimateSampler(worldSeed);
        this.coastalProfile = new CoastalProfile(settings.seaLevel);
        this.heightComposer = new OceanHeightComposer(worldSeed, settings, coastalProfile);
        this.densityFunction = new OceanDensityFunction();
        this.surfaceRules = new OceanSurfaceRules(settings);
        this.population = new OceanPopulation(worldSeed, settings);
    }

    public void populateColumn(BlockColumn column, int columnX, int columnZ) {
        int baseX = columnX * Constants.COLUMN_SIZE;
        int baseZ = columnZ * Constants.COLUMN_SIZE;

        OceanColumnSample[][] columnSamples = new OceanColumnSample[Constants.COLUMN_SIZE][Constants.COLUMN_SIZE];

        // Stage 1: 2D climate and surface-height sampling per column cell.
        for (int lx = 0; lx < Constants.COLUMN_SIZE; lx++) {
            for (int lz = 0; lz < Constants.COLUMN_SIZE; lz++) {
                int worldX = baseX + lx;
                int worldZ = baseZ + lz;
                OceanClimateSample climate = climateSampler.sample(worldX, worldZ);
                OceanColumnSample sample = heightComposer.sampleColumn(worldX, worldZ, climate);
                columnSamples[lx][lz] = sample;
            }
        }

        // Stage 2: materialize density + surface/fluid rules.
        for (int lx = 0; lx < Constants.COLUMN_SIZE; lx++) {
            for (int lz = 0; lz < Constants.COLUMN_SIZE; lz++) {
                OceanColumnSample sample = columnSamples[lx][lz];
                for (int y = 0; y < Constants.WORLD_HEIGHT; y++) {
                    double density = densityFunction.sampleDensity(sample, y);
                    BlockType block = density > 0.0
                        ? surfaceRules.pickSolidBlock(sample, y)
                        : surfaceRules.fluidFor(y);
                    column.setBlock(lx, y, lz, block.getId());
                }
            }
        }

        // Stage 3: deterministic lightweight population accents.
        population.populateColumn(column, columnSamples);
    }
}
