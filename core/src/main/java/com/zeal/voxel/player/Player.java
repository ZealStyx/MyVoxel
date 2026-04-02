package com.zeal.voxel.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.block.BlockType;
import com.zeal.voxel.physics.BulletWorld;
import com.zeal.voxel.physics.PhysicsBody;
import com.zeal.voxel.physics.PhysicsBodyFactory;
import com.zeal.voxel.physics.PhysicsBodyManager;
import com.zeal.voxel.physics.constraint.ConstraintFactory;
import com.zeal.voxel.physics.constraint.ConstraintManager;
import com.zeal.voxel.physics.constraint.PhysicsConstraint;
import com.zeal.voxel.input.GameInputManager;
import com.zeal.voxel.util.Constants;
import com.zeal.voxel.world.WorldGrid;

/**
 * Handles all player-side game logic: movement, camera, interaction, constraints.
 *
 * <p><b>INPUT CONTRACT — POLLING ONLY. DO NOT VIOLATE.</b><br>
 * All input in this class uses {@code Gdx.input.isKeyPressed()},
 * {@code isKeyJustPressed()}, {@code isButtonJustPressed()}, {@code getDeltaX()},
 * {@code getDeltaY()}. There are NO {@code InputProcessor} subclasses here.
 *
 * <p>Why: polling cannot be stolen by Bullet or any library.
 * Event-driven processors registered via {@code setInputProcessor()} can be
 * silently replaced, breaking all input with no error message.
 * {@link GameInputManager} is the single owner of the LibGDX processor — do not
 * call {@code setInputProcessor()} anywhere else in the codebase.
 *
 * <p>Quick reference:
 * <pre>
 *   Movement   isKeyPressed(W/A/S/D)
 *   Jump       isKeyJustPressed(SPACE)       ← Just, fires once
 *   Sprint     isKeyPressed(CONTROL_LEFT)    ← Held, every frame
 *   Grab       isButtonJustPressed(RIGHT)    ← Just, fires once
 *   Throw      isButtonJustPressed(LEFT)     ← Just
 *   Detach     isKeyJustPressed(G)
 *   Weld       isKeyJustPressed(C)
 *   Hinge      isKeyPressed(H) + isButtonJustPressed(LEFT)
 *   Remove C.  isKeyJustPressed(X)
 *   Mode       isKeyJustPressed(TAB)
 *   Fly        isKeyJustPressed(M)
 *   Escape     isKeyJustPressed(ESCAPE)
 *   Mouse      getDeltaX() / getDeltaY()
 * </pre>
 */
public class Player {
    public enum InteractionState { BUILDING, SELECTING }
    private InteractionState interactionState = InteractionState.BUILDING;
    
    private final Camera camera;
    private final WorldGrid world;
    private final SelectionTool selectionTool;
    private final PhysicsBodyFactory physicsBodyFactory;
    private final PhysicsBodyManager physicsBodyManager;
    private final ConstraintFactory constraintFactory;
    private final ConstraintManager constraintManager;
    
    // State
    private PhysicsBody focusedBody;
    private final MouseLook mouseLook;
    private final CapsuleController capsuleController;
    
    private int selectedBlockType = BlockType.STONE.getId();
    private boolean flyMode = false;

    // Interaction Tool
    private final Latch latch;
    private final GameInputManager inputManager;

    // Movement speed for simple flycam
    private float flySpeed = 10f;
    private float targetFov = Constants.CAMERA_FOV;
    private float currentFov = Constants.CAMERA_FOV;

    // Weld tool state
    private PhysicsBody weldFirstBody = null;
    private Vector3 weldFirstPoint = null;

    // Hinge tool state
    private boolean hingeMode = false;
    private PhysicsBody hingeBodyA = null;
    private Vector3 hingePivotA = null;
    private Vector3 hingeNormalA = null;

    // OPTIMIZED: Reused temp vectors to avoid per-frame allocations in update logic.
    private final Vector3 tmpForward = new Vector3();
    private final Vector3 tmpRight = new Vector3();
    private final Vector3 tmpWishDir = new Vector3();
    private final Vector3 tmpMove = new Vector3();
    private final Vector3 tmpDiff = new Vector3();
    private final Vector3 tmpDir = new Vector3();
    private final Vector3 tmpPivotWorld = new Vector3();
    private final Vector3 tmpFeetPos = new Vector3();

    public Player(Camera camera, WorldGrid world, PhysicsBodyFactory factory,
                  PhysicsBodyManager manager, BulletWorld bulletWorld,
                  ConstraintFactory constraintFactory, ConstraintManager constraintManager,
                  GameInputManager inputManager) {
        this.camera = camera;
        this.world = world;
        this.selectionTool = new SelectionTool(camera, world, inputManager);
        this.physicsBodyFactory = factory;
        this.physicsBodyManager = manager;
        this.constraintFactory = constraintFactory;
        this.constraintManager = constraintManager;
        this.mouseLook = new MouseLook();
        this.inputManager = inputManager;
        
        this.capsuleController = new CapsuleController(bulletWorld, new Vector3(camera.position), manager);
        
        this.latch = new Latch(bulletWorld, world, factory, manager, 
                               selectionTool.getFloodFillSelector(), 
                               factory.getBlockBreakEmitter(), 
                               null, // GhostRenderer will be set from GameScreen
                               inputManager, capsuleController);
    }

    public void update(float delta) {
        if (inputManager.isKeyJustPressed(Input.Keys.M)) {
            Gdx.app.log("InputDebug", "M pressed! Current flyMode: " + flyMode);
            setFlyMode(!flyMode);
            Gdx.app.log("InputDebug", "New flyMode: " + flyMode);
        }
        
        if (inputManager.isKeyJustPressed(Input.Keys.TAB)) {
            interactionState = (interactionState == InteractionState.BUILDING) ? InteractionState.SELECTING : InteractionState.BUILDING;
        }

        if (camera instanceof com.badlogic.gdx.graphics.PerspectiveCamera) {
            com.badlogic.gdx.graphics.PerspectiveCamera pc = (com.badlogic.gdx.graphics.PerspectiveCamera) camera;
            mouseLook.update(pc, latch.isRotating());
            
            // Sprint FOV effect
            boolean sprinting = inputManager.isKeyPressed(Input.Keys.SHIFT_LEFT);
            targetFov = sprinting ? Constants.CAMERA_FOV + 8f : Constants.CAMERA_FOV;
            currentFov = MathUtils.lerp(currentFov, targetFov, delta * 8f);
            pc.fieldOfView = currentFov;
        }

        // Handle movement
        handleMovement(delta);

        selectionTool.handleInput(interactionState == InteractionState.SELECTING);
        
        // Let Latch handle its own update (physics/raycast)
        latch.update(camera, delta);
        
        handleInteraction();
        handleBlockSelection();
        handleConstraintTools();
    }



    public PhysicsBody getFocusedBody() {
        return focusedBody;
    }

    private void handleMovement(float delta) {
        boolean sprinting = inputManager.isKeyPressed(Input.Keys.SHIFT_LEFT);
        boolean jump = inputManager.isKeyJustPressed(Input.Keys.SPACE);
        float forwardInput = 0f;
        float strafeInput = 0f;

        if (inputManager.isKeyPressed(Input.Keys.W)) forwardInput += 1f;
        if (inputManager.isKeyPressed(Input.Keys.S)) forwardInput -= 1f;
        if (inputManager.isKeyPressed(Input.Keys.D)) strafeInput += 1f;
        if (inputManager.isKeyPressed(Input.Keys.A)) strafeInput -= 1f;

        // OPTIMIZED: Build movement basis vectors in reusable temporaries.
        tmpForward.set(camera.direction.x, 0f, camera.direction.z);
        if (tmpForward.len2() < 0.0001f) {
            tmpForward.set(0f, 0f, 1f);
        } else {
            tmpForward.nor();
        }
        tmpRight.set(tmpForward).crs(Vector3.Y).nor();
        tmpWishDir.set(tmpForward).scl(forwardInput).mulAdd(tmpRight, strafeInput);
        boolean moving = tmpWishDir.len2() > 0.0001f;
        if (moving) {
            tmpWishDir.nor();
        }
        
        if (flyMode) {
            float speed = flySpeed * delta;
            if (sprinting) speed *= 2f;
            if (moving) {
                // OPTIMIZED: Reuse movement temp for fly camera translation.
                camera.position.add(tmpMove.set(tmpWishDir).scl(speed));
            }
            if (inputManager.isKeyPressed(Input.Keys.SPACE)) camera.position.y += speed;
            if (inputManager.isKeyPressed(Input.Keys.CONTROL_LEFT)) camera.position.y -= speed;
        } else {
            // let CapsuleController handle speed/delta
            if (sprinting) tmpWishDir.scl(1.5f);

            if (jump) Gdx.app.log("InputDebug", "Space pressed (Jump)!");
            capsuleController.update(tmpWishDir, jump, delta);
        }
        
        camera.update();
    }

    private void handleBlockSelection() {
        if (inputManager.isKeyJustPressed(Input.Keys.NUM_1)) selectedBlockType = BlockType.GRASS.getId();
        if (inputManager.isKeyJustPressed(Input.Keys.NUM_2)) selectedBlockType = BlockType.DIRT.getId();
        if (inputManager.isKeyJustPressed(Input.Keys.NUM_3)) selectedBlockType = BlockType.STONE.getId();
    }

    private void handleInteraction() {
        // Scroll wheel handling for Latch distance
        // LibGDX polling Gdx.input.getScrollAmount() requires an InputProcessor, 
        // which GameInputManager provides via its multiplexer interface.
        // We assume inputManager or another system captures scroll and provides it.
        // For now, let's use + / - or similar for polling-friendly scroll?
        // Actually, let's just use the scroll click + movement if needed, 
        // but for now we'll route the right-click button logic.

        if (inputManager.isButtonJustPressed(Input.Buttons.RIGHT)) {
            latch.onRightClick(camera);
        }

        if (inputManager.isButtonJustPressed(Input.Buttons.LEFT)) {
            if (latch.getState() == LatchState.HOLDING || latch.getState() == LatchState.PLACING) {
                latch.onLeftClick(camera);
            } else if (interactionState == InteractionState.BUILDING) {
                breakBlock();
            }
        }

        // BUILDING mode logic (only if latch is IDLE/TARGETING)
        if (interactionState == InteractionState.BUILDING) {
            if (latch.getState() == LatchState.IDLE || latch.getState() == LatchState.TARGETING) {
                // Building logic for static world already handled partially by Latch assembly,
                // but we might want manual block placement still.
                // However, the specification says: "Right-click on static blocks -> assemble + latch (MODE 1)"
                // This replaces the old "Right-click -> place block".
                
                // If we want both, we'd need a modifier key. 
                // As per spec: "Place -> isButtonJustPressed(BUTTON_RIGHT) // when NOT grabbing"
                // But wait, the spec ALSO says Right-click on static blocks -> assemble.
                // I will prioritize assembly as requested.
            }
        }

        // Action key (F) remains active in both modes
        boolean activateMechanisms = inputManager.isKeyPressed(Input.Keys.F);
        for (PhysicsBody body : physicsBodyManager.getActiveBodies()) {
             body.setActionActive(activateMechanisms);
        }

        // Body focus logic
        focusedBody = null;
        for (PhysicsBody body : physicsBodyManager.getActiveBodies()) {
            // OPTIMIZED: Use reusable vector while evaluating focused body candidates.
            tmpDiff.set(body.getPosition()).sub(camera.position);
            if (tmpDiff.len() < 15f && camera.direction.dot(tmpDiff.nor()) > 0.95f) {
                focusedBody = body;
                break;
            }
        }

        // Detach
        if (inputManager.isKeyJustPressed(Input.Keys.G) && !hingeMode) {
             VoxelSelection selection = selectionTool.getSelection();
             if (!selection.isEmpty()) {
                 physicsBodyFactory.create(selection, world);
                 selectionTool.clearSelection();
             }
        }

        // Reattach (Handled by Latch placement mode ASSEMBLE + Right-Click)
        // We can keep the [R] key as a shortcut or remove it.
        // The spec says: "Release -> isKeyJustPressed(ESCAPE)" and "Placement mode toggle -> [V]"
        // We'll remove the legacy [R] reattach to avoid confusion.
    }

    /** Handles weld tool [C], remove constraint [X], hinge tool [H]+click. */
    private void handleConstraintTools() {
        // --- WELD TOOL [C] ---
        if (inputManager.isKeyJustPressed(Input.Keys.C)) {
            if (focusedBody != null) {
                if (weldFirstBody == null) {
                    // First [C]: record first body
                    weldFirstBody = focusedBody;
                    weldFirstPoint = new Vector3(focusedBody.getPosition());
                } else {
                    // Second [C]: create WELD between the two bodies
                    Vector3 midpoint = new Vector3(weldFirstPoint).add(focusedBody.getPosition()).scl(0.5f);
                    PhysicsConstraint weld = constraintFactory.createWeld(weldFirstBody, focusedBody, midpoint);
                    constraintManager.add(weld);
                    weldFirstBody = null;
                    weldFirstPoint = null;
                }
            } else if (weldFirstBody != null) {
                // [C] on empty air with a first body selected → FIXED_TO_WORLD
                // OPTIMIZED: Reuse temp vector for world pivot computation.
                tmpPivotWorld.set(camera.position).mulAdd(camera.direction, 3f);
                // Convert to body-local space
                Matrix4 invTransform = new Matrix4(weldFirstBody.getTransform()).inv();
                Vector3 pivotLocal = new Vector3(tmpPivotWorld).mul(invTransform);
                PhysicsConstraint fixed = constraintFactory.createFixedToWorld(weldFirstBody, pivotLocal);
                constraintManager.add(fixed);
                weldFirstBody = null;
                weldFirstPoint = null;
            }
        }

        // --- REMOVE CONSTRAINT [X] ---
        if (inputManager.isKeyJustPressed(Input.Keys.X)) {
            if (focusedBody != null) {
                // Remove first constraint found involving the focused body
                for (PhysicsConstraint c : constraintManager.getConstraints()) {
                    if (c.involves(focusedBody)) {
                        constraintManager.remove(c);
                        break;
                    }
                }
            }
        }

        // --- HINGE TOOL [H] ---
        hingeMode = inputManager.isKeyPressed(Input.Keys.H);
        if (hingeMode) {
            if (inputManager.isButtonJustPressed(Input.Buttons.LEFT)) {
                if (focusedBody != null) {
                    if (hingeBodyA == null) {
                        hingeBodyA = focusedBody;
                        hingePivotA = new Vector3(focusedBody.getPosition());
                        hingeNormalA = new Vector3(camera.direction).scl(-1).nor();
                    } else {
                        // Second click: create hinge
                        Vector3 hingePivotB = new Vector3(focusedBody.getPosition());
                        Vector3 hingeNormalB = new Vector3(camera.direction).scl(-1).nor();

                        // Compute axis from cross product of face normals
                        Vector3 axis = new Vector3(hingeNormalA).crs(hingeNormalB).nor();
                        if (axis.len2() < 0.001f) {
                            axis.set(0, 1, 0); // fallback if normals are parallel
                        }

                        // Convert pivots to body-local space
                        Matrix4 invA = new Matrix4(hingeBodyA.getTransform()).inv();
                        Matrix4 invB = new Matrix4(focusedBody.getTransform()).inv();
                        Vector3 localPivotA = new Vector3(hingePivotA).mul(invA);
                        Vector3 localPivotB = new Vector3(hingePivotB).mul(invB);

                        PhysicsConstraint hinge = constraintFactory.createHinge(
                            hingeBodyA, focusedBody, localPivotA, localPivotB, axis, axis);
                        constraintManager.add(hinge);

                        hingeBodyA = null;
                        hingePivotA = null;
                        hingeNormalA = null;
                    }
                }
            }
        }

        // Cancel hinge with Escape
        if (inputManager.isKeyJustPressed(Input.Keys.ESCAPE)) {
            hingeBodyA = null;
            hingePivotA = null;
            hingeNormalA = null;
            weldFirstBody = null;
            weldFirstPoint = null;
        }
    }



    private void breakBlock() {
        // OPTIMIZED: Reuse normalized ray direction vector during voxel ray march.
        tmpDir.set(camera.direction).nor();
        for (float t = 0; t < 6f; t += 0.1f) {
            int x = (int) Math.floor(camera.position.x + tmpDir.x * t);
            int y = (int) Math.floor(camera.position.y + tmpDir.y * t);
            int z = (int) Math.floor(camera.position.z + tmpDir.z * t);
            if (world.getBlock(x, y, z) != 0) {
                world.setBlock(x, y, z, 0);
                return;
            }
        }
    }

    @SuppressWarnings("unused")
    private void placeBlock() {
        // OPTIMIZED: Reuse normalized ray direction vector during placement ray march.
        tmpDir.set(camera.direction).nor();
        Vector3 lastAir = null;
        for (float t = 0; t < 6f; t += 0.1f) {
            float px = camera.position.x + tmpDir.x * t;
            float py = camera.position.y + tmpDir.y * t;
            float pz = camera.position.z + tmpDir.z * t;
            int x = (int) Math.floor(px);
            int y = (int) Math.floor(py);
            int z = (int) Math.floor(pz);
            
            if (world.getBlock(x, y, z) != 0) {
                if (lastAir != null) {
                    world.setBlock((int)lastAir.x, (int)lastAir.y, (int)lastAir.z, selectedBlockType);
                }
                return;
            }
            lastAir = new Vector3(x, y, z);
        }
    }

    public void setFlyMode(boolean fly) {
        this.flyMode = fly;
        capsuleController.setFlyMode(fly);
        if (!fly) {
            // Capsule controller works in feet-space, camera is eye-space.
            // OPTIMIZED: Reuse temporary vector for feet-space warp conversion.
            capsuleController.warp(tmpFeetPos.set(camera.position).sub(0f, PlayerConstants.EYE_HEIGHT, 0f));
        }
    }

    public boolean isFlyMode() { return flyMode; }

    public int getSelectedBlockType() { return selectedBlockType; }

    public VoxelSelection getCurrentSelection() {
        return selectionTool.getSelection();
    }

    public SelectionMode getSelectionMode() {
        return selectionTool.getMode();
    }

    public SelectionTool getSelectionTool() {
        return selectionTool;
    }

    public InteractionState getInteractionState() { return interactionState; }
    
    public Latch getLatch() { return latch; }

    public CapsuleController getCapsuleController() {
        return capsuleController;
    }

    public void dispose() {
        capsuleController.dispose();
    }
}
