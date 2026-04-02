package com.zeal.voxel.world;

import com.zeal.voxel.block.BlockType;
import com.zeal.voxel.util.Constants;
import com.zeal.voxel.world.gen.ocean.OceanCoastWorldGenerator;

public class ProceduralColumnTerrainGenerator implements ColumnTerrainGenerator {

    // Ocean owns Y=0 to SEA_HANDOFF (inclusive).
    // WorldGenerator owns Y=SEA_HANDOFF+1 and above.
    // Must match WorldGenerator.SEA_WATER_LEVEL exactly.
    private static final int SEA_HANDOFF = 64;

    private final OceanCoastWorldGenerator oceanGenerator;
    private final WorldGenerator worldGenerator;

    public ProceduralColumnTerrainGenerator() {
        this(12345L);
    }

    public ProceduralColumnTerrainGenerator(long worldSeed) {
        this.oceanGenerator = new OceanCoastWorldGenerator(worldSeed);
        this.worldGenerator = new WorldGenerator(worldSeed);
    }

    @Override
    public void populate(BlockColumn column, int columnX, int columnZ) {
        int baseX = columnX * Constants.COLUMN_SIZE;
        int baseZ = columnZ * Constants.COLUMN_SIZE;

        // ── Pass 1: Ocean generator fills Y=0..SEA_HANDOFF ──────────────────
        // populateColumn writes the full column internally, so we run it first
        // into a temporary column then copy only the sea zone rows.
        BlockColumn oceanTemp = new BlockColumn(columnX, columnZ);
        oceanGenerator.populateColumn(oceanTemp, columnX, columnZ);

        for (int lx = 0; lx < Constants.COLUMN_SIZE; lx++) {
            for (int lz = 0; lz < Constants.COLUMN_SIZE; lz++) {
                for (int y = 0; y <= SEA_HANDOFF; y++) {
                    column.setBlock(lx, y, lz, oceanTemp.getBlock(lx, y, lz));
                }
            }
        }

        // ── Pass 2: WorldGenerator fills Y=SEA_HANDOFF+1..WORLD_HEIGHT ──────
        // Cache all expensive 2D noise values once per (x,z) strip.
        for (int lx = 0; lx < Constants.COLUMN_SIZE; lx++) {
            int worldX = baseX + lx;
            for (int lz = 0; lz < Constants.COLUMN_SIZE; lz++) {
                int worldZ = baseZ + lz;

                int seafloorHeight   = worldGenerator.getSeafloorHeight(worldX, worldZ);
                int localBaseY       = worldGenerator.getPlateauBaseY(worldX, worldZ);
                double edgeDensity   = worldGenerator.getPlateauEdgeDensity(worldX, worldZ);
                int localThickness   = worldGenerator.getLocalPlateauThickness(worldX, worldZ, edgeDensity);
                double radialDist    = Math.sqrt((double) worldX * worldX + (double) worldZ * worldZ);
                int dishBottom       = worldGenerator.getPlateauSlabBottomHeight(worldX, worldZ, radialDist);
                double islandMaskRaw = worldGenerator.getIslandMaskRaw(worldX, worldZ);
                int islandSurface    = islandMaskRaw >= WorldGenerator.islandMaskThreshold()
                    ? worldGenerator.getIslandSurfaceTop(worldX, worldZ, islandMaskRaw)
                    : -1;

                int pillarRootY = Math.max(0, seafloorHeight - 12);
                for (int y = pillarRootY; y < Constants.WORLD_HEIGHT; y++) {
                    BlockType block = worldGenerator.getBlock(
                        worldX, y, worldZ,
                        seafloorHeight,
                        localBaseY,
                        edgeDensity,
                        localThickness,
                        radialDist,
                        dishBottom,
                        islandMaskRaw,
                        islandSurface);

                    if (y <= SEA_HANDOFF) {
                        // Keep ocean baseline while allowing pillar roots to replace it.
                        if (block != BlockType.AIR) {
                            column.setBlock(lx, y, lz, block.getId());
                        }
                    } else {
                        column.setBlock(lx, y, lz, block.getId());
                    }
                }
            }
        }
    }
}