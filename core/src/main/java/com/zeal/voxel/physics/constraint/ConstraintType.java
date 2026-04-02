package com.zeal.voxel.physics.constraint;

/** Types of physics constraints that can connect two bodies or anchor a body to the world. */
public enum ConstraintType {
    /** Rigid connection — no relative movement allowed. */
    WELD,
    /** Rotation around a single axis. Supports motor and angle limits. */
    HINGE,
    /** Translation along a single axis. Supports travel limits. */
    SLIDER,
    /** Anchors a body to a fixed world position. */
    FIXED_TO_WORLD
}
