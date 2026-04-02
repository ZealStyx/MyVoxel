package com.zeal.voxel.world.gen;

/**
 * Stateless 64-bit seed mixing helpers for deterministic sub-seed derivation.
 */
public final class SeedMixer {
    private SeedMixer() {
    }

    public static long mix(long worldSeed, long salt) {
        long x = worldSeed ^ (salt * 0x9E3779B97F4A7C15L);
        return splitMix64(x);
    }

    private static long splitMix64(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }
}
