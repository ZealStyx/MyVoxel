package com.zeal.voxel.physics;

import com.badlogic.gdx.math.Vector3;

/** Represents a single hit result from a physics raycast. */
public class RaycastResult {
    public Vector3 pointWorld = new Vector3();
    public Vector3 normalWorld = new Vector3();
    public float distance;
    public Object body; // Can be PhysicsBody, StaticChunkBody, etc.
}
