package com.zeal.voxel.physics;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState;

/** Physical representation of the player as a 2-block high capsule. */
public class PlayerPhysicsBody {

    private final btCapsuleShape shape;
    private final btDefaultMotionState motionState;
    private final btRigidBody rigidBody;
    
    private boolean flying = true;

    public PlayerPhysicsBody(Vector3 startPos) {
        // Capsule height is the distance between the centers of the two hemispherical ends.
        // Total height = height + 2 * radius.
        // For a 1.8m player, if radius is 0.4m, the height is 1.0m.
        float radius = PhysicsConstants.PLAYER_RADIUS;
        float height = PhysicsConstants.PLAYER_HEIGHT - 2 * radius;
        
        shape = new btCapsuleShape(radius, height);
        
        motionState = new btDefaultMotionState(new Matrix4().setToTranslation(startPos));
        
        float mass = 70f; // 70kg player
        Vector3 localInertia = new Vector3();
        shape.calculateLocalInertia(mass, localInertia);
        
        btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(
            mass, motionState, shape, localInertia
        );
        
        rigidBody = new btRigidBody(info);
        
        // Prevent player from falling over (lock rotation)
        rigidBody.setAngularFactor(Vector3.Zero);
        
        // Damping to prevent sliding infinitely
        rigidBody.setDamping(0.1f, 0f);
        rigidBody.setFriction(0.6f);
        rigidBody.setActivationState(com.badlogic.gdx.physics.bullet.collision.CollisionConstants.DISABLE_DEACTIVATION);
        
        info.dispose();
    }

    public void setFlying(boolean flying) {
        this.flying = flying;
        if (flying) {
            rigidBody.setGravity(Vector3.Zero);
            rigidBody.setLinearVelocity(Vector3.Zero);
            rigidBody.setCollisionFlags(rigidBody.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_NO_CONTACT_RESPONSE);
        } else {
            rigidBody.setGravity(PhysicsConstants.GRAVITY);
            rigidBody.setCollisionFlags(rigidBody.getCollisionFlags() & ~btCollisionObject.CollisionFlags.CF_NO_CONTACT_RESPONSE);
        }
    }

    public boolean isFlying() {
        return flying;
    }

    public btRigidBody getRigidBody() {
        return rigidBody;
    }

    public Vector3 getPosition() {
        Vector3 pos = new Vector3();
        Matrix4 tf = new Matrix4();
        rigidBody.getMotionState().getWorldTransform(tf);
        tf.getTranslation(pos);
        return pos;
    }

    public void setPosition(Vector3 pos) {
        Matrix4 tf = new Matrix4().setToTranslation(pos);
        motionState.setWorldTransform(tf);
        rigidBody.setWorldTransform(tf);
    }

    public void applyMoveForce(Vector3 dir, float speed) {
        if (flying) return;
        
        // Standard walk: set horizontal velocity
        Vector3 currentVel = rigidBody.getLinearVelocity();
        Vector3 targetVel = new Vector3(dir).scl(speed);
        
        // Keep vertical velocity (gravity/jump)
        targetVel.y = currentVel.y;
        
        rigidBody.setLinearVelocity(targetVel);
    }

    public void jump() {
        if (flying) return;
        // Simple raycast check for "on ground" could go here, for now just impulse
        rigidBody.applyCentralImpulse(new Vector3(0, PhysicsConstants.PLAYER_JUMP_FORCE * 70f, 0));
    }

    public void dispose() {
        rigidBody.dispose();
        motionState.dispose();
        shape.dispose();
    }
}
