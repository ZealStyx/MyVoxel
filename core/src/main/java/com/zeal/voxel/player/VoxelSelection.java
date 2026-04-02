package com.zeal.voxel.player;

import com.badlogic.gdx.math.Vector3;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable value object holding blocks and their absolute world coordinates. */
public class VoxelSelection {
    private final Map<Vector3, Integer> selectedBlocks;

    public VoxelSelection(Map<Vector3, Integer> selectedBlocks) {
        this.selectedBlocks = Collections.unmodifiableMap(new HashMap<>(selectedBlocks));
    }

    public Map<Vector3, Integer> getSelectedBlocks() {
        return selectedBlocks;
    }

    public boolean isEmpty() {
        return selectedBlocks.isEmpty();
    }

    public Vector3 getMinCorner() {
        if (selectedBlocks.isEmpty()) return new Vector3();
        Vector3 min = new Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        for (Vector3 v : selectedBlocks.keySet()) {
            min.x = Math.min(min.x, v.x);
            min.y = Math.min(min.y, v.y);
            min.z = Math.min(min.z, v.z);
        }
        return min;
    }

    public Vector3 getMaxCorner() {
        if (selectedBlocks.isEmpty()) return new Vector3();
        Vector3 max = new Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        for (Vector3 v : selectedBlocks.keySet()) {
            max.x = Math.max(max.x, v.x);
            max.y = Math.max(max.y, v.y);
            max.z = Math.max(max.z, v.z);
        }
        return max;
    }
}
