package com.zeal.voxel.world.gen.ocean;

/**
 * Cached per-column data used by Y-loop materialization.
 */
public final class OceanColumnSample {
    public final int worldX;
    public final int worldZ;
    public final OceanClimateSample climate;
    public final OceanRegion region;
    public final double surfaceHeight;
    public final double coastBlend;

    public OceanColumnSample(
        int worldX,
        int worldZ,
        OceanClimateSample climate,
        OceanRegion region,
        double surfaceHeight,
        double coastBlend
    ) {
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.climate = climate;
        this.region = region;
        this.surfaceHeight = surfaceHeight;
        this.coastBlend = coastBlend;
    }
}
