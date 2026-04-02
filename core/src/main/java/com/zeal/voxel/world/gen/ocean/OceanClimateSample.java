package com.zeal.voxel.world.gen.ocean;

/**
 * Climate-like control fields used to shape ocean basins and coastlines.
 */
public final class OceanClimateSample {
    public final double continentalness;
    public final double erosion;
    public final double weirdness;

    public OceanClimateSample(double continentalness, double erosion, double weirdness) {
        this.continentalness = continentalness;
        this.erosion = erosion;
        this.weirdness = weirdness;
    }
}
