package com.zeal.voxel.util;

/** Constants for cascaded shadow map configuration. */
public class CsmConstants {
    public static final int CASCADE_COUNT = 4;
    public static final float SPLIT_LAMBDA = 0.75f;

    /** Shadow map resolution per cascade. */
    public static final int[] CASCADE_RESOLUTIONS = { 2048, 2048, 1024, 1024 };

    /** Per-cascade depth bias (closer cascades have smaller bias = more texel density). */
    public static final float[] CASCADE_BIASES = { 0.0003f, 0.0008f, 0.002f, 0.005f };
}
