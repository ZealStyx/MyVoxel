package com.zeal.voxel.physics;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import com.zeal.voxel.util.Constants;
import com.zeal.voxel.world.ChunkPosition;

/** Helper methods for coordinate transformation. */
public final class CoordinateUtil {

    private CoordinateUtil() {
        // static only
    }

    public static ChunkPosition worldToChunk(Vector3 worldPos) {
        return new ChunkPosition(
                Math.floorDiv((int) worldPos.x, Constants.CHUNK_SIZE),
                Math.floorDiv((int) worldPos.y, Constants.CHUNK_SIZE),
                Math.floorDiv((int) worldPos.z, Constants.CHUNK_SIZE));
    }

    public static Vector3 chunkToWorld(ChunkPosition cp) {
        return new Vector3(
                cp.x * Constants.CHUNK_SIZE,
                cp.y * Constants.CHUNK_SIZE,
                cp.z * Constants.CHUNK_SIZE);
    }

    /** Transforms a world coordinate into the body's local space (relative to selection min). */
    public static Vector3 worldToLocal(Vector3 worldPos, PhysicsBody body) {
        Matrix4 invTransform = new Matrix4(body.getTransform()).inv();
        Vector3 result = new Vector3(worldPos);
        result.mul(invTransform);
        // Correct for the fact that voxel keys are relative to min corner, not CoM
        result.add(body.getCenterOfMassOffset());
        return result;
    }

    /** Transforms a local body coordinate (relative to selection min) into world space. */
    public static Vector3 localToWorld(Vector3 localPos, PhysicsBody body) {
        Vector3 result = new Vector3(localPos);
        result.sub(body.getCenterOfMassOffset());
        result.mul(body.getTransform());
        return result;
    }
}
