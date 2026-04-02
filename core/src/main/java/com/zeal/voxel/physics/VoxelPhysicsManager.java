package com.zeal.voxel.physics;

import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.world.Chunk;
import com.zeal.voxel.world.ChunkPosition;
import com.zeal.voxel.util.Constants;

import java.util.HashMap;
import java.util.Map;

/** Manages static chunk colliders in the Bullet world. */
public class VoxelPhysicsManager {
    private final BulletWorld bulletWorld;
    private final Map<ChunkPosition, ChunkPhysicsCollider> colliders = new HashMap<>();

    public VoxelPhysicsManager(BulletWorld bulletWorld) {
        this.bulletWorld = bulletWorld;
    }

    public void addChunk(ChunkPosition cp, Chunk chunk) {
        if (colliders.containsKey(cp)) {
            removeChunk(cp);
        }
        
        Vector3 worldPos = new Vector3(cp.x * Constants.CHUNK_SIZE, 0, cp.z * Constants.CHUNK_SIZE);
        ChunkPhysicsCollider collider = new ChunkPhysicsCollider(worldPos, chunk);
        colliders.put(cp, collider);
        bulletWorld.addRigidBody(collider.getRigidBody());
    }

    public void removeChunk(ChunkPosition cp) {
        ChunkPhysicsCollider collider = colliders.remove(cp);
        if (collider != null) {
            bulletWorld.removeRigidBody(collider.getRigidBody());
            collider.dispose();
        }
    }

    public void updateChunkPhysics(ChunkPosition cp, Chunk chunk) {
        // Simple rebuild on change
        addChunk(cp, chunk);
    }

    public void dispose() {
        for (ChunkPhysicsCollider collider : colliders.values()) {
            bulletWorld.removeRigidBody(collider.getRigidBody());
            collider.dispose();
        }
        colliders.clear();
    }
}
