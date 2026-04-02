package com.zeal.voxel.world;

/** Holds raw vertex and index data independent of rendering APIs. */
public class MeshData {
    public final float[] vertices;
    public final short[] indices;
    public final float[] debugVertices;

    public MeshData(float[] vertices, short[] indices) {
        this(vertices, indices, null);
    }

    public MeshData(float[] vertices, short[] indices, float[] debugVertices) {
        this.vertices = vertices;
        this.indices = indices;
        this.debugVertices = debugVertices;
    }
}
