package com.zeal.voxel.player;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.zeal.voxel.world.WorldGrid;
import com.zeal.voxel.input.GameInputManager;

/**
 * Manages the active selection mode (AABB or FLOOD_FILL) and delegates input to the correct selector.
 */
public class SelectionTool {

    private SelectionMode mode = SelectionMode.AABB;
    private final GameInputManager inputManager;
    private final AabbSelector aabbSelector;
    private final FloodFillSelector floodFillSelector;
    
    public SelectionTool(Camera camera, WorldGrid world, GameInputManager inputManager) {
        this.inputManager = inputManager;
        this.aabbSelector = new AabbSelector(camera, world, inputManager);
        this.floodFillSelector = new FloodFillSelector(camera, world, inputManager);
    }

    public void handleInput(boolean active) {
        if (!active) return;
        
        if (inputManager.isKeyJustPressed(Input.Keys.K)) {
            mode = (mode == SelectionMode.AABB) ? SelectionMode.FLOOD_FILL : SelectionMode.AABB;
            activeSelector().clear();
        }
        activeSelector().handleInput();
    }

    public VoxelSelection getSelection() {
        VoxelSelection sel = activeSelector().getSelection();
        return sel != null ? sel : new VoxelSelection(java.util.Collections.emptyMap());
    }

    public void clearSelection() {
        activeSelector().clear();
    }

    public SelectionMode getMode() { return mode; }

    public AabbSelector getAabbSelector() { return aabbSelector; }
    public FloodFillSelector getFloodFillSelector() { return floodFillSelector; }

    private Selector activeSelector() {
        return mode == SelectionMode.AABB ? aabbSelector : floodFillSelector;
    }
}
