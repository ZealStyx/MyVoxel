package com.zeal.voxel.physics.constraint;

import com.zeal.voxel.physics.BulletWorld;
import com.zeal.voxel.physics.PhysicsBody;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages the lifecycle of all active physics constraints.
 * CRITICAL: removeAll(body) MUST be called before any body is destroyed
 * to prevent dangling constraint references that crash Bullet.
 */
public class ConstraintManager {

    private final List<PhysicsConstraint> constraints = new ArrayList<>();
    private final BulletWorld bulletWorld;

    public ConstraintManager(BulletWorld bulletWorld) {
        this.bulletWorld = bulletWorld;
    }

    /** Adds a constraint to the world and tracks it. */
    public void add(PhysicsConstraint constraint) {
        constraints.add(constraint);
        bulletWorld.addConstraint(constraint.getBulletConstraint());
    }

    /** Removes and disposes a single constraint. */
    public void remove(PhysicsConstraint constraint) {
        constraints.remove(constraint);
        constraint.dispose();
    }

    /**
     * Removes and disposes ALL constraints involving the given body.
     * Must be called BEFORE destroying the body's rigid body native object.
     */
    public void removeAll(PhysicsBody body) {
        Iterator<PhysicsConstraint> it = constraints.iterator();
        while (it.hasNext()) {
            PhysicsConstraint c = it.next();
            if (c.involves(body)) {
                c.dispose();
                it.remove();
            }
        }
    }

    /** Returns an unmodifiable view of all active constraints. */
    public List<PhysicsConstraint> getConstraints() {
        return constraints;
    }

    /** Disposes all constraints. Called on shutdown. */
    public void dispose() {
        for (PhysicsConstraint c : constraints) {
            c.dispose();
        }
        constraints.clear();
    }
}
