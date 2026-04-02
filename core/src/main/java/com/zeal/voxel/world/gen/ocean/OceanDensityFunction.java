package com.zeal.voxel.world.gen.ocean;

/**
 * Converts height intent into a simple density value.
 * Positive density means solid terrain; non-positive means empty/water volume.
 */
public final class OceanDensityFunction {
    public double sampleDensity(OceanColumnSample column, int worldY) {
        // Slightly sharper density gradients for clearer terrain features.
        double seaBandBias = 0.0;
        double dy = worldY - column.surfaceHeight;
        if (Math.abs(dy) < 1.8) {
            seaBandBias = (1.8 - Math.abs(dy)) * 0.08;
        }
        return (column.surfaceHeight - worldY) + seaBandBias;
    }
}
