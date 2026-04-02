package com.zeal.voxel.world.gen.ocean;

import com.zeal.voxel.world.gen.FractalNoise2D;
import com.zeal.voxel.world.gen.SeedMixer;

/**
 * Composes macro and detail height for ocean floor and coastal terrain.
 */
public final class OceanHeightComposer {
    private static final long RELIEF_LOW_SALT = 0x52454C49454631L;
    private static final long RELIEF_HIGH_SALT = 0x52454C49454632L;
    private static final long TRENCH_SALT = 0x5452454E434831L;
    private static final long SEAMOUNT_SALT = 0x5345414D4F554EL;

    private final OceanTerrainSettings settings;
    private final CoastalProfile coastalProfile;
    private final FractalNoise2D reliefLow;
    private final FractalNoise2D reliefHigh;
    private final FractalNoise2D trenchNoise;
    private final FractalNoise2D seamountNoise;

    public OceanHeightComposer(long worldSeed, OceanTerrainSettings settings, CoastalProfile coastalProfile) {
        this.settings = settings;
        this.coastalProfile = coastalProfile;
        this.reliefLow = new FractalNoise2D(SeedMixer.mix(worldSeed, RELIEF_LOW_SALT), 0.0024, 5, 0.52, 2.1);
        this.reliefHigh = new FractalNoise2D(SeedMixer.mix(worldSeed, RELIEF_HIGH_SALT), 0.0185, 4, 0.55, 2.0);
        this.trenchNoise = new FractalNoise2D(SeedMixer.mix(worldSeed, TRENCH_SALT), 0.0015, 4, 0.54, 2.2);
        this.seamountNoise = new FractalNoise2D(SeedMixer.mix(worldSeed, SEAMOUNT_SALT), 0.0032, 4, 0.52, 2.0);
    }

    public OceanColumnSample sampleColumn(int worldX, int worldZ, OceanClimateSample climate) {
        double c = climate.continentalness;
        double e = climate.erosion;

        OceanRegion region = coastalProfile.regionFor(c);
        double coastBlend = coastalProfile.coastBlend(c);
        double macroHeight = coastalProfile.baseHeightFromContinentalness(c);

        double regionAmp = regionAmplitude(region);
        double erosionRelief = coastalProfile.oceanReliefScale(c, e);

        double low = reliefLow.sample(worldX, worldZ);
        double high = reliefHigh.sample(worldX, worldZ);
        double detail = (low * 0.55 + high * 0.45) * regionAmp;

        // Trenches are more continuous and span wider frequency range offshore.
        double trenchMask = smoothStep(0.45, 0.78, Math.abs(trenchNoise.sample(worldX, worldZ)));
        double offshoreMask = 1.0 - smoothStep(CoastalProfile.NEARSHORE_MAX, CoastalProfile.COAST_MAX, c);
        double trenchDepth = trenchMask * offshoreMask * settings.trenchDepthAmp;

        // Seamount chains are prominent and extend into mid-shelf areas.
        double seaMountMask = smoothStep(0.48, 0.80, seamountNoise.sample(worldX + 912.3, worldZ - 417.2));
        double seamount = seaMountMask * offshoreMask * settings.seamountHeightAmp;

        // Minimal coastal damping: only smooth the most immediate coast band to prevent micro-cliffs.
        double coastDetailDamping = 1.0 - smoothStep(CoastalProfile.NEARSHORE_MAX, CoastalProfile.COAST_MAX, c) * 0.25;

        double finalHeight = macroHeight
            + detail * erosionRelief * coastDetailDamping
            - trenchDepth
            + seamount;

        // Clamp ocean generator to keep land at or below sea level (no floating islands in sky).
        finalHeight = Math.min(finalHeight, settings.seaLevel + 1.0);
        finalHeight = Math.max(finalHeight, settings.seaLevel - 64.0);

        return new OceanColumnSample(worldX, worldZ, climate, region, finalHeight, coastBlend);
    }

    private double regionAmplitude(OceanRegion region) {
        return switch (region) {
            case DEEP_OCEAN -> settings.deepOceanReliefAmp;
            case SHALLOW_OCEAN -> settings.shallowOceanReliefAmp;
            case NEARSHORE, COAST -> settings.nearshoreReliefAmp;
            case INLAND -> settings.inlandReliefAmp;
        };
    }

    private static double smoothStep(double edge0, double edge1, double x) {
        if (edge0 == edge1) {
            return x < edge0 ? 0.0 : 1.0;
        }
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }
}
