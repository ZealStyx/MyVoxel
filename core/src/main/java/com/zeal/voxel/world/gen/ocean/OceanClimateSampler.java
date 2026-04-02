package com.zeal.voxel.world.gen.ocean;

import com.zeal.voxel.world.gen.FractalNoise2D;
import com.zeal.voxel.world.gen.SeedMixer;

/**
 * Samples deterministic macro terrain control fields for ocean/land transitions.
 */
public final class OceanClimateSampler {
    private static final long CONTINENTALNESS_SALT = 0x434F4E54494EL; // "CONTIN"
    private static final long EROSION_SALT = 0x45524F53494FL;          // "EROSIO"
    private static final long WEIRDNESS_SALT = 0x57454952444EL;        // "WEIRDN"

    private final FractalNoise2D continentalnessNoise;
    private final FractalNoise2D erosionNoise;
    private final FractalNoise2D weirdnessNoise;

    public OceanClimateSampler(long worldSeed) {
        this.continentalnessNoise = new FractalNoise2D(
            SeedMixer.mix(worldSeed, CONTINENTALNESS_SALT),
            0.00075,
            5,
            0.52,
            2.05
        );
        this.erosionNoise = new FractalNoise2D(
            SeedMixer.mix(worldSeed, EROSION_SALT),
            0.0016,
            4,
            0.55,
            2.0
        );
        this.weirdnessNoise = new FractalNoise2D(
            SeedMixer.mix(worldSeed, WEIRDNESS_SALT),
            0.0028,
            3,
            0.58,
            2.0
        );
    }

    public OceanClimateSample sample(double worldX, double worldZ) {
        return new OceanClimateSample(
            continentalnessNoise.sample(worldX, worldZ),
            erosionNoise.sample(worldX, worldZ),
            weirdnessNoise.sample(worldX, worldZ)
        );
    }
}
