package com.zeal.voxel.block.model;

import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

public class MeshBuilder {
    public final List<Float> vertices = new ArrayList<>();
    public final List<Short> indices = new ArrayList<>();
    private short vertexIndex = 0;

    public void emitQuad(Vector3 v0,
                         Vector3 v1,
                         Vector3 v2,
                         Vector3 v3,
                         Vector3 normal,
                         float u0,
                         float v0t,
                         float u1,
                         float v1t,
                         float ao) {
        addVert(v0, normal, u0, v1t, ao);
        addVert(v1, normal, u0, v0t, ao);
        addVert(v2, normal, u1, v0t, ao);
        addVert(v3, normal, u1, v1t, ao);

        indices.add(vertexIndex);
        indices.add((short) (vertexIndex + 1));
        indices.add((short) (vertexIndex + 2));
        indices.add(vertexIndex);
        indices.add((short) (vertexIndex + 2));
        indices.add((short) (vertexIndex + 3));
        vertexIndex += 4;
    }

    private void addVert(Vector3 p, Vector3 n, float u, float v, float ao) {
        vertices.add(p.x);
        vertices.add(p.y);
        vertices.add(p.z);
        vertices.add(n.x);
        vertices.add(n.y);
        vertices.add(n.z);
        vertices.add(u);
        vertices.add(v);
        vertices.add(ao);
        vertices.add(1.0f);
        vertices.add(1.0f);
        vertices.add(1.0f);
    }
}
