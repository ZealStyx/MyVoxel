package com.zeal.voxel.world.gen;

import com.zeal.voxel.world.PerlinNoise;

/**
 * Deterministic 2D fractal noise wrapper over PerlinNoise.
 * Keeps noise configuration in one place so terrain systems stay modular.
 */
public final class FractalNoise2D {
    private final PerlinNoise baseNoise;
    private final double baseFrequency;
    private final int octaves;
    private final double gain;
    private final double lacunarity;

    public FractalNoise2D(long seed, double baseFrequency, int octaves, double gain, double lacunarity) {
        this.baseNoise = new PerlinNoise(seed);
        this.baseFrequency = baseFrequency;
        this.octaves = octaves;
        this.gain = gain;
        this.lacunarity = lacunarity;
    }

    /**
     * Returns a value in approximately [-1, 1].
     */
    public double sample(double x, double z) {
        return baseNoise.fractal2(x * baseFrequency, z * baseFrequency, octaves, gain, lacunarity);
    }
}
