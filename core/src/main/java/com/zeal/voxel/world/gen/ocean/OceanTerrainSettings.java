package com.zeal.voxel.world.gen.ocean;

/**
 * Tunable constants for ocean basin shaping and coastal smoothing.
 */
public final class OceanTerrainSettings {
    public final int seaLevel;

    // Detail amplitudes in blocks.
    public final double deepOceanReliefAmp;
    public final double shallowOceanReliefAmp;
    public final double nearshoreReliefAmp;
    public final double inlandReliefAmp;

    // Trench and seamount modulation.
    public final double trenchDepthAmp;
    public final double seamountHeightAmp;

    // Surface stratification.
    public final int beachBandDepth;
    public final int dirtDepth;
    public final int gravelDepth;

    public OceanTerrainSettings() {
        this(
            64,
            24.0,
            18.0,
            9.0,
            18.0,
            36.0,
            22.0,
            4,
            4,
            8
        );
    }

    public OceanTerrainSettings(
        int seaLevel,
        double deepOceanReliefAmp,
        double shallowOceanReliefAmp,
        double nearshoreReliefAmp,
        double inlandReliefAmp,
        double trenchDepthAmp,
        double seamountHeightAmp,
        int beachBandDepth,
        int dirtDepth,
        int gravelDepth
    ) {
        this.seaLevel = seaLevel;
        this.deepOceanReliefAmp = deepOceanReliefAmp;
        this.shallowOceanReliefAmp = shallowOceanReliefAmp;
        this.nearshoreReliefAmp = nearshoreReliefAmp;
        this.inlandReliefAmp = inlandReliefAmp;
        this.trenchDepthAmp = trenchDepthAmp;
        this.seamountHeightAmp = seamountHeightAmp;
        this.beachBandDepth = beachBandDepth;
        this.dirtDepth = dirtDepth;
        this.gravelDepth = gravelDepth;
    }
}
