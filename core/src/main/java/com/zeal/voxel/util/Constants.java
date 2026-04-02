package com.zeal.voxel.util;

public class Constants {
    public static final boolean DEBUG = false;

    // Column-based storage: 16×16 XZ footprint, full world height
    public static final int COLUMN_SIZE = 16;
    public static final int WORLD_HEIGHT = 512;
    
    // Legacy chunk constants (kept for transition compatibility)
    public static final int CHUNK_SIZE = 16;
    public static final int CHUNK_SIZE_SQUARED = CHUNK_SIZE * CHUNK_SIZE;
    public static final int CHUNK_SIZE_CUBED = CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE;

    public static final float CAMERA_FOV = 67f;
    public static final float CAMERA_NEAR = 0.1f;
    public static final float CAMERA_FAR = 1000f;

    // UI Constants
    public static final float CROSSHAIR_SIZE = 20f;
    public static final float CROSSHAIR_THICKNESS = 2f;

    public static final float MOUSE_SENSITIVITY = 0.15f;
    public static final float MIN_PITCH = -89f;
    public static final float MAX_PITCH = 89f;

    // Shadow mapping
    public static final int SHADOW_MAP_SIZE = 2048;
    public static final float SHADOW_BIAS = 0.005f;
    public static final float SHADOW_RADIUS = 64f;
    public static final float SHADOW_UPDATE_THRESHOLD = 8f;

    // Post-processing
    public static final float BLOOM_STRENGTH = 0.8f;
    public static final float SHUTTER_SPEED = 0.5f;
    public static final float BLUR_THRESHOLD = 20f;

    // Particles
    public static final int MAX_PARTICLES = 512;
    public static final float PARTICLE_GRAVITY = -9.8f;
}
