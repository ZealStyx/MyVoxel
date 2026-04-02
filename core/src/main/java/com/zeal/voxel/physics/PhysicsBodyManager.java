package com.zeal.voxel.physics;

import com.zeal.voxel.physics.constraint.ConstraintManager;

import java.util.ArrayList;
import java.util.List;

/** Manages lifecycle of active PhysicsBodies. */
public class PhysicsBodyManager {
    private static final int MAX_BODIES = 32;
    private final List<PhysicsBody> activeBodies = new ArrayList<>();
    // OPTIMIZED: Reuse this list each frame to avoid update-loop allocations.
    private final List<PhysicsBody> pendingDestroy = new ArrayList<>();
    private final BulletWorld bulletWorld;
    private final ConstraintManager constraintManager;

    public PhysicsBodyManager(BulletWorld bulletWorld, ConstraintManager constraintManager) {
        this.bulletWorld = bulletWorld;
        this.constraintManager = constraintManager;
    }

    public void update(float delta) {
        // OPTIMIZED: Clear and reuse instead of allocating a new list each frame.
        pendingDestroy.clear();
        
        for (PhysicsBody body : activeBodies) {
             if (body.shouldBeDestroyed()) {
                 pendingDestroy.add(body);
             } else {
                 body.update(delta);
             }
        }
        
        for (PhysicsBody body : pendingDestroy) {
             destroyBody(body);
        }
    }

    public void addBody(PhysicsBody body) {
        if (activeBodies.size() >= MAX_BODIES) {
            // Recycle oldest body
            destroyBody(activeBodies.get(0));
        }
        activeBodies.add(body);
        
        // Add to world with specific collision filtering so it hits the floor.
        // Also registers the body in BulletWorld's objectRegistry.
        bulletWorld.addRigidBody(
            body.getRigidBody(), 
            PhysicsConstants.GROUP_BODY, 
            PhysicsConstants.GROUP_ALL, 
            body
        );
    }

    /** Destroys a body, removing all constraints FIRST to prevent Bullet crashes. */
    public void destroyBody(PhysicsBody body) {
        // CRITICAL: remove all constraints referencing this body BEFORE
        // destroying the native rigid body to avoid undefined behaviour.
        constraintManager.removeAll(body);
        
        activeBodies.remove(body);
        bulletWorld.removeRigidBody(body.getRigidBody());
        body.dispose();
    }

    public List<PhysicsBody> getActiveBodies() {
        return activeBodies;
    }
    
    public ConstraintManager getConstraintManager() {
        return constraintManager;
    }

    public void dispose() {
        for(PhysicsBody body : activeBodies) {
             bulletWorld.removeRigidBody(body.getRigidBody());
             body.dispose();
        }
        activeBodies.clear();
    }
}
