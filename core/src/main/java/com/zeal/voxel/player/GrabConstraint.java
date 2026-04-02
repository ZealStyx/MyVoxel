package com.zeal.voxel.player;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.Collision;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btSphereShape;
import com.badlogic.gdx.physics.bullet.dynamics.btGeneric6DofConstraint;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.zeal.voxel.physics.BulletWorld;
import com.zeal.voxel.physics.PhysicsBody;
import com.zeal.voxel.physics.CoordinateUtil;

public class GrabConstraint {
    public final btRigidBody anchorGhost;
    public final btSphereShape shape;
    public final btGeneric6DofConstraint constraint;
    public final PhysicsBody heldBody;
    public final Vector3 localPivot;

    public static GrabConstraint create(PhysicsBody body, Vector3 hitPointWorld, BulletWorld bw) {
        Vector3 localPivot = CoordinateUtil.worldToLocal(hitPointWorld, body);

        // Create a tiny kinematic ghost
        btSphereShape shape = new btSphereShape(0.01f);
        btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(0, null, shape, new Vector3());
        btRigidBody anchor = new btRigidBody(info);
        info.dispose();

        anchor.setCollisionFlags(anchor.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_KINEMATIC_OBJECT);
        anchor.setActivationState(Collision.DISABLE_DEACTIVATION);
        anchor.setWorldTransform(new Matrix4().setTranslation(hitPointWorld));

        // Spawn anchor but zero out its collision mask so the invisible grabber
        // does not bump into actual physics world/bodies while moving around!
        bw.addRigidBody(anchor, (short)0, (short)0);

        // Constrain body's local pivot to anchor with orientation lock
        Matrix4 frameInA = new Matrix4().setToTranslation(localPivot);
        Matrix4 frameInB = new Matrix4().idt();
        btGeneric6DofConstraint c = new btGeneric6DofConstraint(
                body.getRigidBody(), anchor, frameInA, frameInB, true);

        // Lock all 6 degrees of freedom (3 linear, 3 angular)
        c.setLinearLowerLimit(Vector3.Zero);
        c.setLinearUpperLimit(Vector3.Zero);
        c.setAngularLowerLimit(Vector3.Zero);
        c.setAngularUpperLimit(Vector3.Zero);

        // Add soft constraint globally
        bw.getDynamicsWorld().addConstraint(c, true);

        return new GrabConstraint(anchor, shape, c, body, localPivot);
    }

    private GrabConstraint(btRigidBody anchorGhost, btSphereShape shape, btGeneric6DofConstraint constraint,
            PhysicsBody heldBody, Vector3 localPivot) {
        this.anchorGhost = anchorGhost;
        this.shape = shape;
        this.constraint = constraint;
        this.heldBody = heldBody;
        this.localPivot = localPivot;
    }

    public void updateAnchorPosition(Vector3 targetWorldPos) {
        Matrix4 transform = anchorGhost.getWorldTransform();
        transform.setTranslation(targetWorldPos);
        updateAnchorTransform(transform);
    }

    public void updateAnchorTransform(Matrix4 transform) {
        anchorGhost.setWorldTransform(transform);
        anchorGhost.activate();
        heldBody.getRigidBody().activate();
    }

    public void release(BulletWorld bw) {
        bw.getDynamicsWorld().removeConstraint(constraint);
        // CRITICAL BUG FIX (hs_err_pid JVM Crash):
        // anchorGhost was added as a RigidBody, and MUST be removed as one.
        // removeCollisionObject leaves a dangling C++ pointer in the rigid body array
        // which triggers EXCEPTION_ACCESS_VIOLATION during the next stepSimulation.
        bw.removeRigidBody(anchorGhost);
        constraint.dispose();
        shape.dispose();
        anchorGhost.dispose();
    }
}
