package com.zeal.voxel.physics;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import java.util.Map;

/** Represents any detached voxel cluster capable of rigid body physics. */
public interface PhysicsBody {
    /** Gets the position of the center of mass in world space. */
    Vector3 getPosition();

    /** Gets the world position of the local (0,0,0) coordinate at detach time. */
    Vector3 getWorldOrigin();

    /** Gets the orientation in world space. */
    Quaternion getRotation();

    /** Gets the transform matrix (useful for rendering). */
    Matrix4 getTransform();

    /** Gets the linear velocity of the body. */
    Vector3 getLinearVelocity();

    /** Gets the angular velocity of the body. */
    Vector3 getAngularVelocity();

    /** Projects linear velocity into screen space for motion blur. Returns zero if below threshold. */
    Vector3 getScreenVelocity(Camera cam);

    /** Applies a central force to the body. */
    void applyCentralForce(Vector3 force);

    /** Applies a force at a specific world position. */
    void applyForce(Vector3 force, Vector3 relativePosition);
    
    /** Applies a torque (rotational force) to the body. */
    void applyTorque(Vector3 torque);

    /** Gets the underlying bullet rigid body implementation. */
    btRigidBody getRigidBody();
    
    /** Gets the local voxels making up this body. Map of local coordinate (x,y,z packed or Vector3) to Block ID. */
    Map<Vector3, Integer> getVoxels();
    
    /** Indicates if this body has no voxels left and should be scheduled for destruction. */
    boolean shouldBeDestroyed();
    
    /** Forces a rebuild of the compound shape (e.g. after blocks are added or removed). */
    void rebuildCollisionShape();
    
    /** Triggers active mechanisms (like thrusters) attached to this body. */
    void setActionActive(boolean active);

    /** Returns true while the body is receiving player action input (power/activate). */
    boolean isActionActive();
    
    /** Gets the offset of the center of mass relative to the world origin at detach time. */
    Vector3 getCenterOfMassOffset();

    /** Returns the current hit flash timer value. */
    float getHitTimer();

    /** Called every tick to give custom behaviors a chance to update. */
    void update(float delta);
    
    /** Destroys and cleans up native resources. */
    void dispose();
}

