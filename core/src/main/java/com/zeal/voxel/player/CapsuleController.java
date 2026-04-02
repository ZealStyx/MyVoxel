package com.zeal.voxel.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.btKinematicCharacterController;
import com.zeal.voxel.physics.BulletWorld;
import com.zeal.voxel.physics.PhysicsBody;
import com.zeal.voxel.physics.PhysicsBodyManager;

/**
 * Player movement controller using Bullet's btKinematicCharacterController.
 * Owns all native Bullet character controller objects — single owner, single disposer.
 * Replaces manual position/velocity mutation with proper physics-driven movement.
 */
public class CapsuleController {
    private static final float MAX_SANE_COORD = 100000f;
    private static final float MAX_SANE_SPEED = 10000f;
    private static final float MAX_RIDE_SPEED = 25f;
    private static final float MAX_RIDE_STEP = 0.5f;
    private static final float JUMP_REARM_SECONDS = 0.08f;

    private final btCapsuleShape capsule;
    private final btPairCachingGhostObject ghost;
    private final btKinematicCharacterController charController;
    private final BulletWorld bulletWorld;
    private final ClosestRayResultCallback rideCallback;

    // Temp vectors (reused to avoid GC)
    private final Vector3 eyePos = new Vector3();
    private final Vector3 feetPos = new Vector3();
    private final Vector3 rayFrom = new Vector3();
    private final Vector3 rayTo = new Vector3();
    private final Vector3 gravityVec = new Vector3();
    private final Vector3 jumpVector = new Vector3();
    private final Vector3 walkZero = new Vector3();
    private final Vector3 callbackFrom = new Vector3();
    private final Vector3 callbackTo = new Vector3();
    private final Vector3 lastValidFeetPos = new Vector3();
    private final Vector3 safeLinearVel = new Vector3();
    private final Vector3 currentWalkDir = new Vector3();
    private final Vector3 currentPos = new Vector3();
    private final PhysicsBodyManager physicsBodyManager;
    private float smoothedY;
    private boolean smoothInit = false;
    private float lastDelta = 1f / 60f;
    private boolean logPostJumpVelocity = false;
    private boolean jumpedLastFrame = false;
    private float jumpRearmTimer = 0f;

    private boolean flyMode = false;

    public CapsuleController(BulletWorld bulletWorld, Vector3 startPos, PhysicsBodyManager physicsBodyManager) {
        this.bulletWorld = bulletWorld;
        this.physicsBodyManager = physicsBodyManager;
        this.lastValidFeetPos.set(startPos);

        capsule = new btCapsuleShape(PlayerConstants.CAPSULE_RADIUS, PlayerConstants.CAPSULE_HEIGHT);

        ghost = new btPairCachingGhostObject();
        ghost.setCollisionShape(capsule);
        ghost.setCollisionFlags(btCollisionObject.CollisionFlags.CF_CHARACTER_OBJECT);
        ghost.setActivationState(CollisionConstants.DISABLE_DEACTIVATION);
        ghost.setContactProcessingThreshold(0.0f);

        // Set initial position
        Matrix4 startTransform = new Matrix4().setToTranslation(startPos);
        ghost.setWorldTransform(startTransform);

        charController = new btKinematicCharacterController(
            ghost, capsule, PlayerConstants.STEP_HEIGHT);
        setCharacterGravity(PlayerConstants.CHAR_GRAVITY);
        charController.setMaxSlope(MathUtils.degreesToRadians * PlayerConstants.MAX_SLOPE_DEG);
        charController.setFallSpeed(55f);
         rideCallback = new ClosestRayResultCallback(callbackFrom, callbackTo);
        // Registration with dynamics world is now handled by BulletWorld.registerCapsuleController()
    }

    /**
     * Updates the character controller. Called once per frame.
     * @param wishDir  desired movement direction (not normalized, not scaled by dt)
     * @param jump     true if jump was requested this frame
     * @param delta    frame delta time
     */
    public void update(Vector3 wishDir, boolean jump, float delta) {
        if (flyMode) return;
        lastDelta = delta;

        if (!Float.isFinite(delta) || delta <= 0f) return;

        if (!Float.isFinite(wishDir.x) || !Float.isFinite(wishDir.y) || !Float.isFinite(wishDir.z)) {
            wishDir.setZero();
        }

        if (logPostJumpVelocity) {
            Gdx.app.log("JumpDebug", "post-jump linearVel=" + charController.getLinearVelocity());
            logPostJumpVelocity = false;
        }

        // OPTIMIZED: removed per-frame allocation — reusing pooled/static instances.
        currentWalkDir.set(wishDir).scl(PlayerConstants.MOVE_SPEED * delta);
        currentWalkDir.y = 0f;

        boolean grounded = charController.onGround();
        jumpRearmTimer = Math.max(0f, jumpRearmTimer - delta);

        if (jump) {
            Gdx.app.log("JumpDebug", "jump=true | grounded=" + grounded
                    + " | wishDir=" + wishDir
                    + " | walkDir=" + currentWalkDir);
        }

        boolean canJump = grounded && jump && !jumpedLastFrame && jumpRearmTimer <= 0f;
        boolean jumpedThisFrame = false;
        if (canJump) {
            // Flush walk state on jump frame to prevent walk vector from bleeding into jump state.
            charController.setWalkDirection(walkZero);
            jumpVector.set(0f, PlayerConstants.JUMP_SPEED, 0f);
            charController.jump(jumpVector);
            logPostJumpVelocity = true;
            jumpRearmTimer = JUMP_REARM_SECONDS;
            jumpedThisFrame = true;
        }
        jumpedLastFrame = jump;

        // Ride BEFORE setWalkDirection so warp() doesn't clobber the walk vector.
        if (grounded && !jump && !jumpedThisFrame) {
            rideMovingBodies(delta);
        }

        // Keep jump impulse frame isolated from walk updates to avoid immediate cancellation.
        if (!jumpedThisFrame) {
            // Always set walk direction LAST — after any warp() calls
            charController.setWalkDirection(currentWalkDir);
        }
    }

    /**
     * Returns the eye position: ghost position + eye height offset.
     */
    public Vector3 getPosition() {
        ghost.getWorldTransform().getTranslation(eyePos);
        if (!isSanePosition(eyePos)) {
            // Recover from occasional Bullet transform corruption by warping back.
            charController.warp(lastValidFeetPos);
            eyePos.set(lastValidFeetPos);
            smoothInit = false;
        } else {
            lastValidFeetPos.set(eyePos);
        }

        float targetY = eyePos.y + PlayerConstants.EYE_HEIGHT;
        if (!smoothInit) {
            smoothedY = targetY;
            smoothInit = true;
        }
        float alpha = Math.min(1f, PlayerConstants.STEP_SMOOTH * lastDelta);
        smoothedY = MathUtils.lerp(smoothedY, targetY, alpha);
        eyePos.y = smoothedY;
        return eyePos;
    }

    /** Returns the raw feet position for raycasting. */
    public Vector3 getFeetPosition() {
        ghost.getWorldTransform().getTranslation(feetPos);
        if (!isSanePosition(feetPos)) {
            feetPos.set(lastValidFeetPos);
        }
        return feetPos;
    }

    /** Returns true if the character is on the ground. */
    public boolean isOnGround() {
        return charController.onGround();
    }

    /**
     * Rides moving physics bodies by casting a short downward ray.
     * If the ray hits a dynamic PhysicsBody, adds its velocity * dt to position.
     */
    private void rideMovingBodies(float delta) {
        Vector3 feetPos = getFeetPosition();
        rayFrom.set(feetPos);
        rayTo.set(feetPos).sub(0, PlayerConstants.RIDE_RAY_LENGTH, 0);

        rideCallback.setCollisionObject(null);
        rideCallback.setClosestHitFraction(1f);
        bulletWorld.rayTest(rayFrom, rayTo, rideCallback);

        if (!rideCallback.hasHit()) {
            return;
        }

        btCollisionObject hitObj = rideCallback.getCollisionObject();
        if (hitObj == null || hitObj.getActivationState() == CollisionConstants.ISLAND_SLEEPING) {
            return;
        }

        for (PhysicsBody body : physicsBodyManager.getActiveBodies()) {
            if (body.getRigidBody() != hitObj) {
                continue;
            }

            Vector3 bodyVel = body.getLinearVelocity();
            if (!isSaneBodyVelocity(bodyVel)) {
                break;
            }

            float rideX = MathUtils.clamp(bodyVel.x, -MAX_RIDE_SPEED, MAX_RIDE_SPEED) * delta;
            float rideZ = MathUtils.clamp(bodyVel.z, -MAX_RIDE_SPEED, MAX_RIDE_SPEED) * delta;
            float rideLen2 = rideX * rideX + rideZ * rideZ;
            if (!Float.isFinite(rideLen2) || !(rideLen2 > 0.0001f)) {
                break;
            }

            // Clamp per-frame ride displacement to avoid sudden physics spikes.
            float maxRideStep2 = MAX_RIDE_STEP * MAX_RIDE_STEP;
            if (rideLen2 > maxRideStep2) {
                float sqrtLen = (float) Math.sqrt(rideLen2);
                if (!Float.isFinite(sqrtLen) || sqrtLen < 0.0001f) {
                    break;
                }

                float scale = MAX_RIDE_STEP / sqrtLen;
                if (!Float.isFinite(scale)) {
                    break;
                }

                rideX *= scale;
                rideZ *= scale;
            }

            if (!Float.isFinite(rideX) || !Float.isFinite(rideZ)) {
                break;
            }

            // Shift the char controller by the body's movement this frame.
            ghost.getWorldTransform().getTranslation(currentPos);
            // Only inherit horizontal platform motion; vertical injection can launch the player.
            currentPos.add(rideX, 0f, rideZ);

            // Never warp to a corrupt position.
            if (!isSanePosition(currentPos)) {
                break;
            }

            charController.warp(currentPos);
            lastValidFeetPos.set(currentPos);
            break;
        }
    }

    public void setFlyMode(boolean fly) {
        this.flyMode = fly;
        if (fly) {
            // Keep controller registered; just neutralize controller forces in fly mode.
            charController.setWalkDirection(walkZero);
            setCharacterGravity(0f);
        } else {
            setCharacterGravity(PlayerConstants.CHAR_GRAVITY);
            smoothInit = false;
        }
    }

    // btKinematicCharacterController in this binding exposes a Vector3 gravity API,
    // so this wrapper keeps call-sites scalar-friendly and consistent.
    private void setCharacterGravity(float gravity) {
        gravityVec.set(0f, -gravity, 0f);
        charController.setGravity(gravityVec);
    }

    public boolean isFlyMode() {
        return flyMode;
    }

    /** Teleports the controller to a specific position. */
    public void warp(Vector3 position) {
        if (!isSanePosition(position)) {
            return;
        }
        charController.warp(position);
        lastValidFeetPos.set(position);
    }

    private boolean isSanePosition(Vector3 pos) {
        return Float.isFinite(pos.x)
                && Float.isFinite(pos.y)
                && Float.isFinite(pos.z)
                && Math.abs(pos.x) <= MAX_SANE_COORD
                && Math.abs(pos.y) <= MAX_SANE_COORD
                && Math.abs(pos.z) <= MAX_SANE_COORD;
    }

    public btPairCachingGhostObject getGhost() {
        return ghost;
    }

    public btKinematicCharacterController getCharController() {
        return charController;
    }

    public Vector3 getLinearVelocity() {
        Vector3 vel = charController.getLinearVelocity();
        if (!isFiniteVector(vel)) {
            // Expose a safe velocity to HUD/UI without mutating controller state.
            safeLinearVel.setZero();
            return safeLinearVel;
        }
        safeLinearVel.set(vel);
        return safeLinearVel;
    }

    private boolean isFiniteVector(Vector3 vel) {
        return vel != null
                && Float.isFinite(vel.x)
                && Float.isFinite(vel.y)
                && Float.isFinite(vel.z);
    }

    private boolean isSaneBodyVelocity(Vector3 vel) {
        return isFiniteVector(vel)
                && Math.abs(vel.x) <= MAX_SANE_SPEED
                && Math.abs(vel.y) <= MAX_SANE_SPEED
                && Math.abs(vel.z) <= MAX_SANE_SPEED;
    }

    public void dispose() {
        bulletWorld.removeAction(charController);
        bulletWorld.removeCollisionObject(ghost);
        charController.dispose();
        ghost.dispose();
        capsule.dispose();
        rideCallback.dispose();
    }
}
