package com.zeal.voxel.physics;

import com.badlogic.gdx.math.Vector3;

public class PhysicsConstants {
    public static final Vector3 GRAVITY = new Vector3(0, -9.8f, 0);
    public static final float DEFAULT_RESTITUTION = 0.2f;
    public static final float DEFAULT_FRICTION = 0.7f;
    
    public static final float THRUSTER_FORCE = 500f; // Force applied per active thruster block
    public static final float GYRO_DAMPING = 5.0f; // Resistive torque multiplier

    // Collision filter groups (bitfield)
    public static final short GROUP_WORLD  = 1;   // static terrain chunks
    public static final short GROUP_BODY   = 2;   // dynamic physics bodies
    public static final short GROUP_PLAYER = 4;   // player kinematic ghost
    public static final short GROUP_ALL    = 7;   // 1|2|4

    // Compound shape rebuild
    public static final float SHAPE_REBUILD_COOLDOWN = 0.1f;

    // Continuous Collision Detection
    public static final float CCD_MOTION_THRESHOLD = 0.5f;
    public static final float CCD_SWEPT_SPHERE_RADIUS = 0.3f;

    // Player Physics
    public static final float PLAYER_HEIGHT = 1.8f;
    public static final float PLAYER_RADIUS = 0.4f;
    public static final float PLAYER_SPEED = 8.0f;
    public static final float PLAYER_JUMP_FORCE = 6.0f;
}
