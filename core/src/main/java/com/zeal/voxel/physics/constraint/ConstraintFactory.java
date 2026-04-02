package com.zeal.voxel.physics.constraint;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.zeal.voxel.physics.BulletWorld;
import com.zeal.voxel.physics.PhysicsBody;

/**
 * Factory for creating typed physics constraints between bodies.
 * Each method returns a fully constructed PhysicsConstraint ready for ConstraintManager.
 */
public class ConstraintFactory {

    private final BulletWorld bulletWorld;

    public ConstraintFactory(BulletWorld bulletWorld) {
        this.bulletWorld = bulletWorld;
    }

    /**
     * WELD — rigid connection with no relative movement.
     * @param bodyA first body
     * @param bodyB second body
     * @param weldPoint world-space point where the weld occurs
     */
    public PhysicsConstraint createWeld(PhysicsBody bodyA, PhysicsBody bodyB, Vector3 weldPoint) {
        // Compute frame transforms relative to each body's center of mass
        Matrix4 invA = new Matrix4(bodyA.getTransform()).inv();
        Matrix4 invB = new Matrix4(bodyB.getTransform()).inv();

        Matrix4 frameA = new Matrix4().setToTranslation(
            new Vector3(weldPoint).mul(invA));
        Matrix4 frameB = new Matrix4().setToTranslation(
            new Vector3(weldPoint).mul(invB));

        btFixedConstraint constraint = new btFixedConstraint(
            bodyA.getRigidBody(), bodyB.getRigidBody(), frameA, frameB);

        return new PhysicsConstraint(ConstraintType.WELD, constraint, bodyA, bodyB, bulletWorld);
    }

    /**
     * HINGE — rotation around one axis.
     * @param bodyA      first body
     * @param bodyB      second body
     * @param pivotA     pivot point in bodyA local space
     * @param pivotB     pivot point in bodyB local space
     * @param axisA      hinge axis in bodyA local space
     * @param axisB      hinge axis in bodyB local space
     */
    public PhysicsConstraint createHinge(PhysicsBody bodyA, PhysicsBody bodyB,
                                          Vector3 pivotA, Vector3 pivotB,
                                          Vector3 axisA, Vector3 axisB) {
        btHingeConstraint constraint = new btHingeConstraint(
            bodyA.getRigidBody(), bodyB.getRigidBody(),
            pivotA, pivotB, axisA, axisB);

        return new PhysicsConstraint(ConstraintType.HINGE, constraint, bodyA, bodyB, bulletWorld);
    }

    /**
     * SLIDER — translation along one axis.
     * @param bodyA  first body
     * @param bodyB  second body
     * @param frameA frame transform in bodyA space
     * @param frameB frame transform in bodyB space
     */
    public PhysicsConstraint createSlider(PhysicsBody bodyA, PhysicsBody bodyB,
                                           Matrix4 frameA, Matrix4 frameB) {
        btSliderConstraint constraint = new btSliderConstraint(
            bodyA.getRigidBody(), bodyB.getRigidBody(),
            frameA, frameB, true);

        return new PhysicsConstraint(ConstraintType.SLIDER, constraint, bodyA, bodyB, bulletWorld);
    }

    /**
     * FIXED_TO_WORLD — anchors a body to a world position.
     * @param body           the body to anchor
     * @param pivotInBody    pivot point in body local space
     */
    public PhysicsConstraint createFixedToWorld(PhysicsBody body, Vector3 pivotInBody) {
        btPoint2PointConstraint constraint = new btPoint2PointConstraint(
            body.getRigidBody(), pivotInBody);

        return new PhysicsConstraint(ConstraintType.FIXED_TO_WORLD, constraint, body, null, bulletWorld);
    }
}
