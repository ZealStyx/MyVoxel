package com.zeal.voxel.world.gen.ocean;

/**
 * Converts continentalness into macro region classification and smooth coastal profile values.
 * This class does not place blocks directly; it only provides continuous shaping helpers.
 */
public final class CoastalProfile {
    // Typical continentalness thresholds for ocean/land partitioning.
    public static final double DEEP_OCEAN_MAX = -0.45;
    public static final double SHALLOW_OCEAN_MAX = -0.20;
    public static final double NEARSHORE_MAX = -0.05;
    public static final double COAST_MAX = 0.08;

    private final int seaLevel;

    public CoastalProfile(int seaLevel) {
        this.seaLevel = seaLevel;
    }

    public OceanRegion regionFor(double continentalness) {
        if (continentalness < DEEP_OCEAN_MAX) {
            return OceanRegion.DEEP_OCEAN;
        }
        if (continentalness < SHALLOW_OCEAN_MAX) {
            return OceanRegion.SHALLOW_OCEAN;
        }
        if (continentalness < NEARSHORE_MAX) {
            return OceanRegion.NEARSHORE;
        }
        if (continentalness < COAST_MAX) {
            return OceanRegion.COAST;
        }
        return OceanRegion.INLAND;
    }

    /**
     * Blends ocean macro height into land macro height around the coastline.
     * 0 = full ocean model, 1 = full land model.
     */
    public double coastBlend(double continentalness) {
        return smoothStep(NEARSHORE_MAX, COAST_MAX, continentalness);
    }

    /**
     * Returns a smooth base macro height around sea level before adding detail.
     * Target behavior:
     * - Deep ocean floor mostly far below sea level
     * - Continental shelf rises gradually near coast
     * - Beach/coast hovers near sea level
     * - Inland starts with low positive elevation
     */
    public double baseHeightFromContinentalness(double c) {
        double deepOceanY = seaLevel - 72.0;
        double shallowOceanY = seaLevel - 42.0;
        double nearshoreY = seaLevel - 13.0;
        double coastY = seaLevel - 1.0;
        double inlandLowY = seaLevel + 20.0;

        if (c <= DEEP_OCEAN_MAX) {
            // Keep abyssal areas broad and relatively flat in macro form.
            double t = smoothStep(-1.0, DEEP_OCEAN_MAX, c);
            return lerp(deepOceanY - 14.0, deepOceanY, t);
        }
        if (c <= SHALLOW_OCEAN_MAX) {
            double t = smoothStep(DEEP_OCEAN_MAX, SHALLOW_OCEAN_MAX, c);
            return lerp(deepOceanY, shallowOceanY, t);
        }
        if (c <= NEARSHORE_MAX) {
            double t = smoothStep(SHALLOW_OCEAN_MAX, NEARSHORE_MAX, c);
            return lerp(shallowOceanY, nearshoreY, t);
        }
        if (c <= COAST_MAX) {
            double t = smoothStep(NEARSHORE_MAX, COAST_MAX, c);
            return lerp(nearshoreY, coastY, t);
        }

        double inlandUpperC = 0.35;
        double t = smoothStep(COAST_MAX, inlandUpperC, c);
        return lerp(coastY, inlandLowY, t);
    }

    /**
     * Region-aware relief scaling for ocean floor detail.
     * Higher erosion values flatten deep ocean and soften coastal roughness.
     */
    public double oceanReliefScale(double continentalness, double erosion) {
        // Map erosion from [-1,1] to [0,1]
        double e01 = clamp01((erosion + 1.0) * 0.5);

        // Ocean mask fades out as continentalness moves inland.
        double oceanMask = 1.0 - smoothStep(NEARSHORE_MAX, COAST_MAX, continentalness);

        // High erosion still flattens but less aggressively; low erosion creates rugged terrain.
        double erosionScale = lerp(1.6, 0.65, e01);

        return oceanMask * erosionScale;
    }

    private static double smoothStep(double edge0, double edge1, double x) {
        if (edge0 == edge1) {
            return x < edge0 ? 0.0 : 1.0;
        }
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }
}
