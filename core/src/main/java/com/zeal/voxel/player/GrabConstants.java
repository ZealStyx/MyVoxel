package com.zeal.voxel.player;

public class GrabConstants {
    public static final float MIN_GRAB_DIST  = 1.5f;
    public static final float MAX_GRAB_DIST  = 6.0f;
    public static final float DEFAULT_DIST   = 3.0f;
    public static final float SCROLL_SPEED   = 2.0f;  // units per scroll notch
    public static final float TAU            = 0.1f;   // Softeness (0.0 to 1.0)
    public static final float DAMPING        = 0.9f;   // Damping (0.0 to 1.0)
    public static final float IMPULSE_CLAMP  = 20.0f;  // Max impulse per step
    public static final float THROW_FORCE    = 20.0f;
}
