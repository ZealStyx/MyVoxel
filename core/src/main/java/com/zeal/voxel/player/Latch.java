package com.zeal.voxel.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.physics.BulletWorld;
import com.zeal.voxel.physics.CoordinateUtil;
import com.zeal.voxel.physics.PhysicsBody;
import com.zeal.voxel.physics.PhysicsBodyFactory;
import com.zeal.voxel.physics.PhysicsBodyManager;
import com.zeal.voxel.physics.RaycastResult;
import com.zeal.voxel.render.GhostRenderer;
import com.zeal.voxel.render.particle.BlockBreakEmitter;
import com.zeal.voxel.util.LatchConstants;
import com.zeal.voxel.world.WorldGrid;
import com.zeal.voxel.input.GameInputManager;

public class Latch {

    // State
    private LatchState state = LatchState.IDLE;
    private PlacementMode placementMode = PlacementMode.DROP;
    private PhysicsBody heldBody = null;
    private GrabConstraint constraint = null;
    private Vector3 localPivot = null;
    private float latchDistance = LatchConstants.DEFAULT_DIST;
    private RaycastResult lastHit = null;

    // Dependencies
    private final BulletWorld bulletWorld;
    private final WorldGrid worldGrid;
    private final PhysicsBodyFactory factory;
    private final PhysicsBodyManager bodyManager;
    private final FloodFillSelector filler;
    private final LatchAssembler assembler;
    private final LatchDisassembler disassembler;
    private GhostRenderer ghostRenderer;
    private final GameInputManager inputManager;
    private final CapsuleController playerController;

    public Latch(BulletWorld bulletWorld, WorldGrid worldGrid, PhysicsBodyFactory factory, 
                 PhysicsBodyManager bodyManager, FloodFillSelector filler,
                 BlockBreakEmitter emitter, GhostRenderer ghostRenderer,
                 GameInputManager inputManager, CapsuleController playerController) {
        this.bulletWorld = bulletWorld;
        this.worldGrid = worldGrid;
        this.factory = factory;
        this.bodyManager = bodyManager;
        this.filler = filler;
        this.ghostRenderer = ghostRenderer;
        this.inputManager = inputManager;
        this.playerController = playerController;

        this.assembler = new LatchAssembler();
        this.disassembler = new LatchDisassembler(emitter);
    }

    public void setGhostRenderer(GhostRenderer ghostRenderer) {
        this.ghostRenderer = ghostRenderer;
    }

    public void update(Camera camera, float delta) {
        updateRaycast(camera);
        updateState(camera, delta);
        updateHoldPhysics(camera, delta);
        updateRotation(camera, delta);
        
        if (state == LatchState.PLACING) {
            ghostRenderer.update(heldBody, placementMode, worldGrid);
        } else {
            ghostRenderer.clear();
        }
    }

    private void updateRaycast(Camera camera) {
        if (state == LatchState.IDLE || state == LatchState.TARGETING) {
            lastHit = bulletWorld.raycast(camera.position, camera.direction, PlayerConstants.REACH);
            if (lastHit != null) {
                state = LatchState.TARGETING;
            } else {
                state = LatchState.IDLE;
            }
        }
    }

    private void updateState(Camera camera, float delta) {
        if (state == LatchState.HOLDING || state == LatchState.PLACING) {
            // Toggle placement mode
            if (inputManager.isKeyJustPressed(Input.Keys.V)) {
                placementMode = (placementMode == PlacementMode.DROP) ? PlacementMode.ASSEMBLE : PlacementMode.DROP;
                state = LatchState.PLACING;
            }
            
            // Cancel / Drop
            if (inputManager.isKeyJustPressed(Input.Keys.ESCAPE)) {
                transitionToIdle(true);
            }
        }
    }

    private void updateHoldPhysics(Camera camera, float delta) {
        if (heldBody == null || constraint == null) return;

        // 3A. Target point computation
        Vector3 anchorPos = new Vector3(camera.position)
                .add(new Vector3(camera.direction).nor().scl(latchDistance));

        // 3B. Follow camera orientation each frame, then place at target anchor position.
        Matrix4 anchorTrans = new Matrix4(camera.view).inv();
        anchorTrans.setTranslation(anchorPos);
        constraint.updateAnchorTransform(anchorTrans);

        // Keep slight angular damping for stability during high-speed movement
        Vector3 angVel = heldBody.getAngularVelocity();
        angVel.scl(1f - LatchConstants.ANGULAR_DAMP * delta);
        heldBody.getRigidBody().setAngularVelocity(angVel);
    }

    private void updateRotation(Camera camera, float delta) {
        if (heldBody == null || constraint == null) return;

        // Use Arrow Keys for rotation. 
        // These don't require holding [R], allowing for easier simultaneous movement.
        Matrix4 anchorTrans = constraint.anchorGhost.getWorldTransform();

        Vector3 yawAxis = new Vector3(0, 1, 0);
        Vector3 pitchAxis = new Vector3(camera.direction).crs(camera.up).nor();
        Vector3 rollAxis = new Vector3(camera.direction).nor();

        float rotSpeed = LatchConstants.ROTATE_SPEED * 150f * delta;
        float pitch = 0, yaw = 0, roll = 0;

        // Arrow Key rotation (Yaw/Pitch)
        if (inputManager.isKeyPressed(Input.Keys.UP))    pitch = rotSpeed;
        if (inputManager.isKeyPressed(Input.Keys.DOWN))  pitch = -rotSpeed;
        if (inputManager.isKeyPressed(Input.Keys.LEFT))  yaw = rotSpeed;
        if (inputManager.isKeyPressed(Input.Keys.RIGHT)) yaw = -rotSpeed;

        // Roll remains on Q/E for consistency
        if (inputManager.isKeyPressed(Input.Keys.Q)) roll = -rotSpeed * 2f;
        if (inputManager.isKeyPressed(Input.Keys.E)) roll = rotSpeed * 2f;

        // Original [R] + Mouse support remains for precision
        if (inputManager.isKeyPressed(Input.Keys.R)) {
            pitch += -Gdx.input.getDeltaY() * LatchConstants.ROTATE_SPEED;
            yaw += -Gdx.input.getDeltaX() * LatchConstants.ROTATE_SPEED;
        }

        if (pitch != 0 || yaw != 0 || roll != 0) {
            anchorTrans.rotate(yawAxis, yaw);
            anchorTrans.rotate(pitchAxis, pitch);
            anchorTrans.rotate(rollAxis, roll);
            constraint.updateAnchorTransform(anchorTrans);
        }
    }

    public void onRightClick(Camera camera) {
        if (state == LatchState.TARGETING) {
            if (lastHit.body instanceof PhysicsBody) {
                // Mode 2: Latch directly
                transitionToHolding((PhysicsBody) lastHit.body, lastHit.pointWorld, camera);
            } else {
                // Mode 1: Assemble + Latch
                PhysicsBody newBody = assembler.assembleAndLatch(lastHit.pointWorld, worldGrid, factory, filler, bulletWorld);
                if (newBody != null) {
                    transitionToHolding(newBody, lastHit.pointWorld, camera);
                }
            }
        } else if (state == LatchState.HOLDING || state == LatchState.PLACING) {
            executePlacement();
        }
    }

    public void onLeftClick(Camera camera) {
        if (state == LatchState.HOLDING || state == LatchState.PLACING) {
            PhysicsBody body = heldBody;
            transitionToIdle(false);
            
            float mass = body.getRigidBody().getInvMass() > 0 ? 1.0f / body.getRigidBody().getInvMass() : 1.0f;
            Vector3 impulse = new Vector3(camera.direction).scl(LatchConstants.THROW_FORCE * mass);
            body.getRigidBody().applyCentralImpulse(impulse);
        }
    }

    public void onScroll(float amount) {
        if (state == LatchState.HOLDING || state == LatchState.PLACING) {
            latchDistance += amount * LatchConstants.SCROLL_SPEED;
            latchDistance = MathUtils.clamp(latchDistance, LatchConstants.MIN_DIST, LatchConstants.MAX_DIST);
        }
    }

    public void onCancel() {
        transitionToIdle(true);
    }

    private void transitionToHolding(PhysicsBody body, Vector3 hitWorld, Camera camera) {
        this.heldBody = body;
        this.localPivot = CoordinateUtil.worldToLocal(hitWorld, body);
        
        // Calculate distance from CAMERA (head) instead of controller (feet) to avoid offset glitches
        this.latchDistance = hitWorld.dst(camera.position);
        
        // Create 6DOF orientation-locked constraint
        this.constraint = GrabConstraint.create(body, hitWorld, bulletWorld);

        // Snap anchor rotation to body rotation initially
        Matrix4 startTrans = constraint.anchorGhost.getWorldTransform();
        startTrans.set(body.getTransform());
        startTrans.setTranslation(hitWorld);
        constraint.updateAnchorTransform(startTrans);
        
        this.state = LatchState.HOLDING;
        this.placementMode = PlacementMode.DROP;
    }

    private void transitionToIdle(boolean zeroVelocity) {
        if (constraint != null) {
            constraint.release(bulletWorld);
            constraint = null;
        }
        
        if (heldBody != null && zeroVelocity) {
            heldBody.getRigidBody().setLinearVelocity(Vector3.Zero);
            heldBody.getRigidBody().setAngularVelocity(Vector3.Zero);
        }
        
        heldBody = null;
        localPivot = null;
        state = LatchState.IDLE;
    }

    private void executePlacement() {
        if (placementMode == PlacementMode.DROP) {
            transitionToIdle(true);
        } else {
            disassembler.disassemble(heldBody, worldGrid, bodyManager, bulletWorld, playerController);
            // transition to IDLE is inside disassemble or we do it here
            heldBody = null;
            localPivot = null;
            if (constraint != null) {
                constraint.release(bulletWorld);
                constraint = null;
            }
            state = LatchState.IDLE;
        }
    }

    public boolean isRotating() {
        return (state == LatchState.HOLDING || state == LatchState.PLACING) && inputManager.isKeyPressed(Input.Keys.R);
    }

    public LatchState getState() { return state; }
    public PlacementMode getPlacementMode() { return placementMode; }
    public PhysicsBody getHeldBody() { return heldBody; }
    public Vector3 getLocalPivot() { return localPivot; }
    public RaycastResult getLastHit() { return lastHit; }
}
