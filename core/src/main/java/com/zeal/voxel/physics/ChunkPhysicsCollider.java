package com.zeal.voxel.physics;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btCompoundShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState;
import com.zeal.voxel.render.ao.VoxelGrid;
import com.zeal.voxel.util.Constants;
import com.zeal.voxel.world.Chunk;

/** Physics collider for a single 16x16x16 static voxel chunk. Uses greedy merge. */
public class ChunkPhysicsCollider {
    private final btCompoundShape compoundShape;
    private final btDefaultMotionState motionState;
    private final btRigidBody rigidBody;

    public ChunkPhysicsCollider(Vector3 worldPos, Chunk chunk) {
        // Adapter: chunk local coords → VoxelGrid interface
        VoxelGrid grid = (x, y, z) -> {
            if (x < 0 || y < 0 || z < 0 ||
                x >= Constants.CHUNK_SIZE || y >= Constants.CHUNK_SIZE || z >= Constants.CHUNK_SIZE) {
                return false;
            }
            return chunk.getBlock(x, y, z) != 0;
        };

        // Build optimized compound shape with greedy merge
        // Offset is zero because static chunks don't need CoM centering
        this.compoundShape = CompoundShapeBuilder.build(
            grid, Constants.CHUNK_SIZE, Constants.CHUNK_SIZE, Constants.CHUNK_SIZE, Vector3.Zero);

        // Static object (mass = 0)
        motionState = new btDefaultMotionState(new Matrix4().setToTranslation(worldPos));
        btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(
            0f, motionState, compoundShape, Vector3.Zero
        );
        rigidBody = new btRigidBody(info);
        rigidBody.setFriction(0.8f);
        rigidBody.setRestitution(0.1f);
        
        info.dispose();
    }

    public btRigidBody getRigidBody() {
        return rigidBody;
    }

    public void dispose() {
        rigidBody.dispose();
        motionState.dispose();
        // Dispose children
        for (int i = 0; i < compoundShape.getNumChildShapes(); i++) {
            compoundShape.getChildShape(i).dispose();
        }
        compoundShape.dispose();
    }
}
