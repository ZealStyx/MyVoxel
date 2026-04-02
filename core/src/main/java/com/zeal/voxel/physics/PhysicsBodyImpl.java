package com.zeal.voxel.physics;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btCompoundShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState;
import com.zeal.voxel.block.BlockBehaviour;
import com.zeal.voxel.block.BlockRegistry;
import com.zeal.voxel.block.ResolvedBehaviour;
import com.zeal.voxel.block.BlockType;
import com.zeal.voxel.render.ao.VoxelGrid;

import java.util.HashMap;
import java.util.Map;

public class PhysicsBodyImpl implements PhysicsBody {

    private final Map<Vector3, Integer> voxels = new HashMap<>(); // local coordinate -> block type ID
    private final Map<Integer, BlockBehaviour> behaviours; // shared behaviours

    private btCompoundShape compoundShape;
    private btDefaultMotionState motionState;
    private btRigidBody rigidBody;
    
    private final Vector3 centerOfMassOffset = new Vector3();
    private final Vector3 worldOrigin;
    private boolean actionActive = false;
    private float hitTimer = 0f;

    // Rebuild cooldown
    private boolean pendingRebuild = false;
    private float rebuildCooldownTimer = 0f;

    public PhysicsBodyImpl(Vector3 worldCoM, Map<Vector3, Integer> localVoxels, Map<Integer, BlockBehaviour> behaviours) {
        this.behaviours = behaviours;
        this.voxels.putAll(localVoxels);
        this.worldOrigin = new Vector3(worldCoM);

        // Calculate CoM relative to selection min corner (the local keys)
        calculateCenterOfMass();

        // Build optimized compound shape using greedy merge
        compoundShape = buildOptimizedShape();

        // Native memory: root transform is the world-space center of mass
        Matrix4 startTransform = new Matrix4().setToTranslation(worldCoM);
        motionState = new btDefaultMotionState(startTransform, new Matrix4());
        
        // Compute inertia
        Vector3 localInertia = new Vector3();
        float totalMass = calculateTotalMass();
        if (totalMass > 0f) {
            compoundShape.calculateLocalInertia(totalMass, localInertia);
        }

        btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(
            totalMass, motionState, compoundShape, localInertia
        );
        
        info.setRestitution(PhysicsConstants.DEFAULT_RESTITUTION);
        info.setFriction(PhysicsConstants.DEFAULT_FRICTION);
        
        rigidBody = new btRigidBody(info);

        // Enable CCD to prevent tunnelling at high velocity
        rigidBody.setCcdMotionThreshold(PhysicsConstants.CCD_MOTION_THRESHOLD);
        rigidBody.setCcdSweptSphereRadius(PhysicsConstants.CCD_SWEPT_SPHERE_RADIUS);
        
        // Clean up info
        info.dispose();

        BlockRegistry registry = BlockRegistry.getActive();
        if (registry != null) {
            for (Map.Entry<Vector3, Integer> entry : voxels.entrySet()) {
                BlockBehaviour behaviour = behaviours.get(entry.getValue());
                if (behaviour == null) {
                    continue;
                }
                ResolvedBehaviour resolved = registry.getBehaviour(entry.getValue());
                if (resolved == null) {
                    continue;
                }
                Vector3 p = entry.getKey();
                behaviour.onAttach(this, (int) p.x, (int) p.y, (int) p.z, resolved);
            }
        }
    }

    private void calculateCenterOfMass() {
        centerOfMassOffset.setZero();
        float totalMass = 0f;
        for (Map.Entry<Vector3, Integer> entry : voxels.entrySet()) {
            float mass = blockMass(entry.getValue());
            Vector3 pos = entry.getKey();
            // Use cell center (pos + 0.5) for accurate CoM
            centerOfMassOffset.add((pos.x + 0.5f) * mass, (pos.y + 0.5f) * mass, (pos.z + 0.5f) * mass);
            totalMass += mass;
        }
        if (totalMass > 0f) {
            centerOfMassOffset.scl(1f / totalMass);
        }
    }

    private float calculateTotalMass() {
        float mass = 0f;
        for (Integer type : voxels.values()) {
            mass += blockMass(type);
        }
        return mass;
    }

    private float blockMass(int blockId) {
        BlockRegistry registry = BlockRegistry.getActive();
        if (registry != null) {
            return registry.getMass(blockId);
        }
        return BlockType.fromId(blockId).getMass();
    }

    /** Builds a compound shape using CompoundShapeBuilder's greedy merge algorithm. */
    private btCompoundShape buildOptimizedShape() {
        // Determine bounds of voxel data
        int maxX = 0, maxY = 0, maxZ = 0;
        for (Vector3 pos : voxels.keySet()) {
            maxX = Math.max(maxX, (int) pos.x + 1);
            maxY = Math.max(maxY, (int) pos.y + 1);
            maxZ = Math.max(maxZ, (int) pos.z + 1);
        }
        if (maxX == 0 || maxY == 0 || maxZ == 0) {
            return new btCompoundShape();
        }

        // Adapter from our voxel map to the VoxelGrid interface
        VoxelGrid grid = (x, y, z) -> voxels.containsKey(new Vector3(x, y, z));

        return CompoundShapeBuilder.build(grid, maxX, maxY, maxZ, centerOfMassOffset);
    }

    @Override
    public void rebuildCollisionShape() {
        if (shouldBeDestroyed()) return;
        // Defer rebuild using cooldown to batch multiple removals
        pendingRebuild = true;
    }

    /** Called from update() when the cooldown has elapsed and a rebuild is pending. */
    private void executeRebuild() {
        pendingRebuild = false;
        
        calculateCenterOfMass();
        
        // Dispose old compound shape and all its children
        if (compoundShape != null) {
            for (int i = 0; i < compoundShape.getNumChildShapes(); i++) {
                compoundShape.getChildShape(i).dispose();
            }
            compoundShape.dispose();
        }

        // Build new optimized shape
        compoundShape = buildOptimizedShape();
        rigidBody.setCollisionShape(compoundShape);
        
        // Recalculate inertia
        float totalMass = calculateTotalMass();
        Vector3 localInertia = new Vector3();
        if (totalMass > 0f) {
            compoundShape.calculateLocalInertia(totalMass, localInertia);
        }
        rigidBody.setMassProps(totalMass, localInertia);
        rigidBody.updateInertiaTensor();
    }

    @Override
    public void update(float delta) {
        if (!rigidBody.isActive()) rigidBody.activate();
        
        // Handle deferred shape rebuild
        if (pendingRebuild) {
            rebuildCooldownTimer += delta;
            if (rebuildCooldownTimer >= PhysicsConstants.SHAPE_REBUILD_COOLDOWN) {
                executeRebuild();
                rebuildCooldownTimer = 0f;
            }
        }
        
        for (Map.Entry<Vector3, Integer> entry : voxels.entrySet()) {
            int blockId = entry.getValue();
            BlockBehaviour behaviour = behaviours.get(blockId);
            if (behaviour == null) {
                continue;
            }

            BlockRegistry registry = BlockRegistry.getActive();
            if (registry == null) {
                continue;
            }

            ResolvedBehaviour resolved = registry.getBehaviour(blockId);
            if (resolved == null) {
                continue;
            }

            Vector3 p = entry.getKey();
            behaviour.onTick(this, (int) p.x, (int) p.y, (int) p.z, resolved, delta);
        }
        
        if (hitTimer > 0) hitTimer -= delta;
    }

    @Override
    public void setActionActive(boolean active) {
        this.actionActive = active;
        if (active) rigidBody.activate();
    }

    @Override
    public boolean isActionActive() {
        return actionActive;
    }

    @Override
    public Vector3 getPosition() {
        return rigidBody.getCenterOfMassPosition();
    }

    @Override
    public Quaternion getRotation() {
        Matrix4 tf = new Matrix4();
        rigidBody.getMotionState().getWorldTransform(tf);
        Quaternion q = new Quaternion();
        tf.getRotation(q);
        return q;
    }

    @Override
    public Matrix4 getTransform() {
        Matrix4 tf = new Matrix4();
        rigidBody.getMotionState().getWorldTransform(tf);
        return tf;
    }

    @Override
    public Vector3 getLinearVelocity() {
        return rigidBody.getLinearVelocity();
    }

    @Override
    public Vector3 getAngularVelocity() {
        return rigidBody.getAngularVelocity();
    }

    @Override
    public Vector3 getScreenVelocity(Camera cam) {
        Vector3 vel = getLinearVelocity();
        if (vel.len() < com.zeal.voxel.util.Constants.BLUR_THRESHOLD) {
            return Vector3.Zero;
        }
        // Project velocity direction into screen space
        Vector3 pos = getPosition();
        Vector3 screenA = cam.project(new Vector3(pos));
        Vector3 screenB = cam.project(new Vector3(pos).add(vel.x * 0.016f, vel.y * 0.016f, vel.z * 0.016f));
        // Return as normalized screen-space delta
        float w = com.badlogic.gdx.Gdx.graphics.getWidth();
        float h = com.badlogic.gdx.Gdx.graphics.getHeight();
        return new Vector3((screenB.x - screenA.x) / w, (screenB.y - screenA.y) / h, 0);
    }

    @Override
    public void applyCentralForce(Vector3 force) {
        rigidBody.applyCentralForce(force);
    }

    @Override
    public void applyForce(Vector3 force, Vector3 relativePosition) {
        rigidBody.applyForce(force, relativePosition);
    }

    @Override
    public void applyTorque(Vector3 torque) {
        rigidBody.applyTorque(torque);
    }

    @Override
    public btRigidBody getRigidBody() {
        return rigidBody;
    }

    @Override
    public Map<Vector3, Integer> getVoxels() {
        return voxels;
    }

    @Override
    public Vector3 getCenterOfMassOffset() {
        return centerOfMassOffset;
    }

    @Override
    public float getHitTimer() {
        return hitTimer;
    }

    @Override
    public Vector3 getWorldOrigin() {
        return worldOrigin;
    }

    @Override
    public boolean shouldBeDestroyed() {
        return voxels.isEmpty();
    }

    @Override
    public void dispose() {
        if (rigidBody != null) {
            rigidBody.dispose();
        }
        if (motionState != null) {
            motionState.dispose();
        }
        if (compoundShape != null) {
            for(int i=0; i<compoundShape.getNumChildShapes(); i++) {
                 compoundShape.getChildShape(i).dispose();
            }
            compoundShape.dispose();
        }
    }
}
