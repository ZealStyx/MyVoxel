package com.zeal.voxel.player;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.GridPoint3;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.util.SelectionConstants;
import com.zeal.voxel.world.WorldGrid;
import com.zeal.voxel.input.GameInputManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Flood-fill (connected) voxel selector.
 * Left-click = flood fill from targeted voxel, G = confirm detach, Esc = clear.
 */
public class FloodFillSelector implements Selector {

    private final Camera camera;
    private final WorldGrid world;
    private final GameInputManager inputManager;
    private final Map<Vector3, Integer> selectedBlocks = new HashMap<>();
    private boolean capped = false;

    public FloodFillSelector(Camera camera, WorldGrid world, GameInputManager inputManager) {
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
            GridPoint3 target = raycast();
            if (target != null) {
                runFloodFill(target);
            }
        }
    }

    private GridPoint3 raycast() {
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

    public void runFloodFill(GridPoint3 start) {
        selectedBlocks.clear();
        capped = false;

        Set<GridPoint3> visited = new HashSet<>();
        Queue<GridPoint3> frontier = new LinkedList<>();
        frontier.add(start);

        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

        while (!frontier.isEmpty() && selectedBlocks.size() < SelectionConstants.FLOOD_FILL_CAP) {
            GridPoint3 cur = frontier.poll();
            if (visited.contains(cur)) continue;
            visited.add(cur);

            int id = world.getBlock(cur.x, cur.y, cur.z);
            if (id == 0) continue;

            selectedBlocks.put(new Vector3(cur.x, cur.y, cur.z), id);

            for (int[] d : dirs) {
                GridPoint3 nb = new GridPoint3(cur.x + d[0], cur.y + d[1], cur.z + d[2]);
                if (!visited.contains(nb)) frontier.add(nb);
            }
        }

        if (selectedBlocks.size() >= SelectionConstants.FLOOD_FILL_CAP) {
            capped = true;
        }
    }

    @Override
    public VoxelSelection getSelection() {
        if (selectedBlocks.isEmpty()) return null;
        return new VoxelSelection(selectedBlocks);
    }

    @Override
    public void clear() {
        selectedBlocks.clear();
        capped = false;
    }

    public boolean isCapped() { return capped; }
    public Map<Vector3, Integer> getSelectedBlocks() { return selectedBlocks; }
}
