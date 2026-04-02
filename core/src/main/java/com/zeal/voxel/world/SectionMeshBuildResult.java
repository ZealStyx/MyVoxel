package com.zeal.voxel.world;

import java.util.HashMap;
import java.util.Map;

/**
 * Built mesh data split by render pass category.
 */
public class SectionMeshBuildResult {
    public final Map<Integer, MeshData> opaqueMeshes = new HashMap<>();
    public final Map<Integer, MeshData> transparentMeshes = new HashMap<>();

    public boolean isEmpty() {
        return opaqueMeshes.isEmpty() && transparentMeshes.isEmpty();
    }
}
