package com.zeal.voxel.physics;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.btGhostPairCallback;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.zeal.voxel.player.CapsuleController;

import java.util.HashMap;
import java.util.Map;

/** Wrapper for the gdx-bullet dynamics world. */
public class BulletWorld {
    private final btDefaultCollisionConfiguration collisionConfig;
    private final btCollisionDispatcher dispatcher;
    private final btDbvtBroadphase broadphase;
    private final btSequentialImpulseConstraintSolver solver;
    private final btDiscreteDynamicsWorld dynamicsWorld;

    // Registry to map native Bullet pointers to our Java wrapper objects
    private final Map<btCollisionObject, Object> objectRegistry = new HashMap<>();

    public BulletWorld() {
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        dynamicsWorld.setGravity(PhysicsConstants.GRAVITY);

        // REQUIRED for btKinematicCharacterController ghost objects.
        // Without this, the ghost object's overlapping pairs are never populated,
        // causing undefined native behaviour (and crashes) when addAction/removeAction
        // is called on the character controller.
        broadphase.getOverlappingPairCache().setInternalGhostPairCallback(new btGhostPairCallback());
    }

    public void update(float delta) {
        // Bullet recommends fixed time step simulation
        dynamicsWorld.stepSimulation(delta, 5, 1f / 60f);
    }

    public void addRigidBody(btRigidBody body) {
        // Fallback or default usage
        dynamicsWorld.addRigidBody(body);
    }
    
    public void addRigidBody(btRigidBody body, short group, short mask) {
        dynamicsWorld.addRigidBody(body, group, mask);
    }
    
    public void addRigidBody(btRigidBody body, short group, short mask, Object javaWrapper) {
        dynamicsWorld.addRigidBody(body, group, mask);
        if (javaWrapper != null) registerObject(body, javaWrapper);
    }

    public void removeRigidBody(btRigidBody body) {
        dynamicsWorld.removeRigidBody(body);
        objectRegistry.remove(body);
    }

    public void addConstraint(btTypedConstraint constraint) {
        dynamicsWorld.addConstraint(constraint);
    }

    public void removeConstraint(btTypedConstraint constraint) {
        dynamicsWorld.removeConstraint(constraint);
    }

    /** Add a collision object. */
    public void addCollisionObject(btCollisionObject obj, short group, short mask) {
        dynamicsWorld.addCollisionObject(obj, group, mask);
    }

    /** Remove a collision object. */
    public void removeCollisionObject(btCollisionObject obj) {
        dynamicsWorld.removeCollisionObject(obj);
        objectRegistry.remove(obj);
    }

    /** Add an action interface (e.g. btKinematicCharacterController). */
    public void addAction(btActionInterface action) {
        dynamicsWorld.addAction(action);
    }

    /** Remove an action interface. */
    public void removeAction(btActionInterface action) {
        dynamicsWorld.removeAction(action);
    }

    /** Perform a primitive ray test. Results are populated in the callback. */
    public void rayTest(Vector3 from, Vector3 to, ClosestRayResultCallback callback) {
        dynamicsWorld.rayTest(from, to, callback);
    }

    /** Advanced raytest returning a custom wrapper for grab targeting. */
    public RaycastResult raycast(Vector3 from, Vector3 dir, float maxDist) {
        Vector3 to = new Vector3(dir).nor().scl(maxDist).add(from);
        ClosestRayResultCallback cb = new ClosestRayResultCallback(from, to);
        dynamicsWorld.rayTest(from, to, cb);

        if (!cb.hasHit()) {
            cb.dispose();
            return null;
        }

        RaycastResult result = new RaycastResult();
        cb.getHitPointWorld(result.pointWorld);
        cb.getHitNormalWorld(result.normalWorld);
        result.distance = result.pointWorld.dst(from);

        btCollisionObject obj = cb.getCollisionObject();
        result.body = getBodyForCollisionObject(obj);

        cb.dispose();
        return result;
    }

    public void registerCapsuleController(CapsuleController ctrl) {
        dynamicsWorld.addCollisionObject(
            ctrl.getGhost(),
            PhysicsConstants.GROUP_PLAYER,
            (short)(PhysicsConstants.GROUP_WORLD | PhysicsConstants.GROUP_BODY)
        );
        dynamicsWorld.addAction(ctrl.getCharController());
    }

    public void registerObject(btCollisionObject collisionObject, Object javaWrapper) {
        objectRegistry.put(collisionObject, javaWrapper);
    }

    public Object getBodyForCollisionObject(btCollisionObject obj) {
        return objectRegistry.get(obj);
    }

    public btDiscreteDynamicsWorld getDynamicsWorld() {
        return dynamicsWorld;
    }

    public btBroadphaseInterface getBroadphase() {
        return broadphase;
    }

    public btDispatcher getDispatcher() {
        return dispatcher;
    }

    public void dispose() {
        dynamicsWorld.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
    }
}
