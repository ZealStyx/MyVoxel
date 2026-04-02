package com.zeal.voxel.util;

public class LatchConstants {
    public static final float MIN_DIST = 1.5f;
    public static final float MAX_DIST = 8.0f;
    public static final float DEFAULT_DIST = 3.0f;
    public static final float SCROLL_SPEED = 2.0f;
    public static final float PULL_STRENGTH = 15.0f;  // velocity correction scale
    public static final float MAX_PULL_SPEED = 12.0f; // max corrective velocity m/s
    public static final float ANGULAR_DAMP = 8.0f;   // spin reduction while held
    public static final float ROTATE_SPEED = 0.03f;  // rad per pixel
    public static final float THROW_FORCE = 20.0f;
    public static final float TAU = 0.1f;            // constraint softness
    public static final float DAMPING = 0.9f;
    public static final float IMPULSE_CLAMP = 30.0f;
    public static final float GHOST_OPACITY = 0.4f;
    public static final float GHOST_REBUILD_DIST = 0.1f;   // units moved before rebuild
    public static final float GHOST_REBUILD_ANGLE = 1.0f;  // degrees rotated before rebuild
    public static final int ASSEMBLE_FILL_CAP = 1024;
}
