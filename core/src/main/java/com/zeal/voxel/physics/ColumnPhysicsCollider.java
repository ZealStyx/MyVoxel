package com.zeal.voxel.physics;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btCompoundShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState;
import com.zeal.voxel.render.ao.VoxelGrid;
import com.zeal.voxel.util.Constants;
import com.zeal.voxel.world.BlockColumn;

/**
 * Physics collider for a FULL column (Y=0 .. WORLD_HEIGHT-1).
 * Uses the same greedy-merge CompoundShapeBuilder as before — just taller.
 */
public class ColumnPhysicsCollider {
    private final btCompoundShape compoundShape;
    private final btDefaultMotionState motionState;
    private final btRigidBody rigidBody;

    public ColumnPhysicsCollider(Vector3 worldPos, BlockColumn column) {
        // VoxelGrid adapter for the full column height
        VoxelGrid grid = (x, y, z) -> {
            if (x < 0 || x >= Constants.COLUMN_SIZE ||
                z < 0 || z >= Constants.COLUMN_SIZE ||
                y < 0 || y >= Constants.WORLD_HEIGHT) {
                return false;
            }
            return column.getBlock(x, y, z) != 0;
        };

        // Build the compound shape (greedy merge works perfectly on any height)
        this.compoundShape = CompoundShapeBuilder.build(
                grid,
                Constants.COLUMN_SIZE,
                Constants.WORLD_HEIGHT,
                Constants.COLUMN_SIZE,
                Vector3.Zero);   // static world object → no CoM offset needed

        motionState = new btDefaultMotionState(new Matrix4().setToTranslation(worldPos));

        btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(
                0f, motionState, compoundShape, Vector3.Zero);

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
        for (int i = 0; i < compoundShape.getNumChildShapes(); i++) {
            compoundShape.getChildShape(i).dispose();
        }
        compoundShape.dispose();
    }
}