package com.zeal.voxel.world;

import java.util.List;

/**
 * Result of meshing a BlockColumn.
 * Contains all meshes produced: one ground-connected mesh plus any floating clusters.
 */
public class ColumnMeshResult {
    public final List<ColumnMesh> opaqueMeshes;
    public final List<ColumnMesh> transparentMeshes;
    
    public ColumnMeshResult(List<ColumnMesh> opaqueMeshes, List<ColumnMesh> transparentMeshes) {
        this.opaqueMeshes = opaqueMeshes;
        this.transparentMeshes = transparentMeshes;
    }
}
