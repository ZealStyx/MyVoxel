package com.zeal.voxel.physics.constraint;

import com.badlogic.gdx.physics.bullet.dynamics.btTypedConstraint;
import com.zeal.voxel.physics.BulletWorld;
import com.zeal.voxel.physics.PhysicsBody;

/**
 * Wraps a Bullet btTypedConstraint with metadata about its type and connected bodies.
 * Responsible for removing itself from the dynamics world and freeing native memory on dispose.
 */
public class PhysicsConstraint {
    private final ConstraintType type;
    private final btTypedConstraint bulletConstraint;
    private final PhysicsBody bodyA;
    private final PhysicsBody bodyB; // null = anchored to world
    private final BulletWorld bulletWorld;
    private boolean disposed = false;

    public PhysicsConstraint(ConstraintType type, btTypedConstraint bulletConstraint,
                              PhysicsBody bodyA, PhysicsBody bodyB, BulletWorld bulletWorld) {
        this.type = type;
        this.bulletConstraint = bulletConstraint;
        this.bodyA = bodyA;
        this.bodyB = bodyB;
        this.bulletWorld = bulletWorld;
    }

    public ConstraintType getType() {
        return type;
    }

    public btTypedConstraint getBulletConstraint() {
        return bulletConstraint;
    }

    public PhysicsBody getBodyA() {
        return bodyA;
    }

    /** @return bodyB, or null if this constraint anchors bodyA to the world. */
    public PhysicsBody getBodyB() {
        return bodyB;
    }

    /** Returns true if this constraint involves the given body (as A or B). */
    public boolean involves(PhysicsBody body) {
        return body == bodyA || body == bodyB;
    }

    /** Removes the constraint from the Bullet world and frees native memory. */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        bulletWorld.removeConstraint(bulletConstraint);
        bulletConstraint.dispose();
    }

    public boolean isDisposed() {
        return disposed;
    }
}
