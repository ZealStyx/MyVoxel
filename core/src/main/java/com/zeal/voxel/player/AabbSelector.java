package com.zeal.voxel.player;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.GridPoint3;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.world.WorldGrid;
import com.zeal.voxel.input.GameInputManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Two-point AABB box selection.
 * Left-click = set corner A, Right-click = set corner B, G = confirm detach, Esc = clear.
 */
public class AabbSelector implements Selector {

    public enum State { IDLE, CORNER_A, CONFIRMED }

    private final Camera camera;
    private final WorldGrid world;
    private final GameInputManager inputManager;
    private GridPoint3 cornerA = null;
    private GridPoint3 cornerB = null;
    private State state = State.IDLE;
    
    public AabbSelector(Camera camera, WorldGrid world, GameInputManager inputManager) {
        this.camera = camera;
        this.world = world;
        this.inputManager = inputManager;
    }

    @Override
    public void handleInput() {
        if (inputManager.isKeyJustPressed(Input.Keys.ESCAPE)) {
            clear();
            return;
        }

        if (inputManager.isButtonJustPressed(Input.Buttons.LEFT)) {
            GridPoint3 hit = raycast();
            if (hit != null) {
                cornerA = hit;
                cornerB = null;
                state = State.CORNER_A;
            }
        }

        if (inputManager.isButtonJustPressed(Input.Buttons.RIGHT)) {
            if (state == State.CORNER_A) {
                GridPoint3 hit = raycast();
                if (hit != null) {
                    cornerB = hit;
                    state = State.CONFIRMED;
                }
            }
        }
    }

    /** Raycast from camera; returns the first solid voxel hit or null. */
    public GridPoint3 raycast() {
        Vector3 dir = new Vector3(camera.direction).nor();
        for (float t = 0; t < PlayerConstants.REACH; t += 0.05f) {
            int bx = (int) Math.floor(camera.position.x + dir.x * t);
            int by = (int) Math.floor(camera.position.y + dir.y * t);
            int bz = (int) Math.floor(camera.position.z + dir.z * t);
            if (world.getBlock(bx, by, bz) != 0) {
                return new GridPoint3(bx, by, bz);
            }
        }
        return null;
    }

    @Override
    public VoxelSelection getSelection() {
        if (state != State.CONFIRMED || cornerA == null || cornerB == null) return null;

        int minX = Math.min(cornerA.x, cornerB.x);
        int minY = Math.min(cornerA.y, cornerB.y);
        int minZ = Math.min(cornerA.z, cornerB.z);
        int maxX = Math.max(cornerA.x, cornerB.x);
        int maxY = Math.max(cornerA.y, cornerB.y);
        int maxZ = Math.max(cornerA.z, cornerB.z);

        Map<Vector3, Integer> blocks = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int id = world.getBlock(x, y, z);
                    if (id != 0) {
                        blocks.put(new Vector3(x, y, z), id);
                    }
                }
            }
        }
        return new VoxelSelection(blocks);
    }

    @Override
    public void clear() {
        cornerA = null;
        cornerB = null;
        state = State.IDLE;
    }

    // --- Accessors for selection renderer ---

    public GridPoint3 getCornerA() { return cornerA; }

    /** Live preview of B: the current raycast target when in CORNER_A state, else confirmed B. */
    public GridPoint3 getCornerBPreview() {
        if (state == State.CONFIRMED) return cornerB;
        if (state == State.CORNER_A) return raycast();
        return null;
    }

    public State getState() { return state; }
}
