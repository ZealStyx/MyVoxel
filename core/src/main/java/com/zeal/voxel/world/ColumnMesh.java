package com.zeal.voxel.world;

import com.badlogic.gdx.math.collision.BoundingBox;

/**
 * A single mesh produced from a BlockColumn.
 * May represent ground-connected terrain or a floating cluster of blocks.
 * Always has separate opaque and transparent geometry paths.
 */
public class ColumnMesh {
    public static class MeshGeometry {
        public float[] vertices;
        public short[] indices;
        
        public MeshGeometry(float[] vertices, short[] indices) {
            this.vertices = vertices;
            this.indices = indices;
        }
    }
    
    /**
     * Opaque geometry - solid blocks rendered with full depth write.
     */
    public final MeshGeometry opaqueGeometry;
    
    /**
     * Transparent geometry - water, glass, etc. rendered separately with blending enabled.
     */
    public final MeshGeometry transparentGeometry;
    
    public final BoundingBox bounds;
    public final boolean isFloating;
    
    /**
     * Constructor for meshes with separate opaque and transparent geometry.
     */
    public ColumnMesh(MeshGeometry opaqueGeometry, MeshGeometry transparentGeometry, BoundingBox bounds, boolean isFloating) {
        this.opaqueGeometry = opaqueGeometry;
        this.transparentGeometry = transparentGeometry;
        this.bounds = bounds;
        this.isFloating = isFloating;
    }
}
