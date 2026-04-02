package com.zeal.voxel.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.zeal.voxel.physics.PhysicsBody;
import com.zeal.voxel.player.PlacementMode;
import com.zeal.voxel.util.LatchConstants;
import com.zeal.voxel.world.WorldGrid;
import java.util.Map;

public class GhostRenderer implements Disposable {

    private final ShaderProgram shader;
    private Mesh mesh;
    private final Matrix4 lastTransform = new Matrix4();
    private boolean needsRebuild = true;

    // Cube vertices offset for batching
    private static final float[] CUBE_VERTS = {
        // Front
        0,0,1, 1,0,1, 1,1,1, 0,1,1,
        // Back
        0,0,0, 0,1,0, 1,1,0, 1,0,0,
        // Top
        0,1,0, 0,1,1, 1,1,1, 1,1,0,
        // Bottom
        0,0,0, 1,0,0, 1,0,1, 0,0,1,
        // Left
        0,0,0, 0,0,1, 0,1,1, 0,1,0,
        // Right
        1,0,0, 1,1,0, 1,1,1, 1,0,1
    };

    private static final short[] CUBE_INDICES = {
        0,1,2, 2,3,0,
        4,5,6, 6,7,4,
        8,9,10, 10,11,8,
        12,13,14, 14,15,12,
        16,17,18, 18,19,16,
        20,21,22, 22,23,20
    };

    public GhostRenderer(ShaderProgram shader) {
        this.shader = shader;
    }

    public void update(PhysicsBody body, PlacementMode mode, WorldGrid world) {
        if (body == null || mode != PlacementMode.ASSEMBLE) {
            clear();
            return;
        }

        Matrix4 currentTransform = body.getTransform();
        if (needsRebuild || !lastTransform.equals(currentTransform)) {
            rebuildMesh(body, world);
            lastTransform.set(currentTransform);
            needsRebuild = false;
        }
    }

    private void rebuildMesh(PhysicsBody body, WorldGrid world) {
        Map<Vector3, Integer> voxels = body.getVoxels();
        int count = voxels.size();
        
        // 4 vertices per face * 6 faces = 24 vertices per cube
        // But we can just do a very simple cube mesh per voxel
        int maxVerts = count * 24;
        int maxIndices = count * 36;

        if (mesh == null || mesh.getMaxVertices() < maxVerts) {
            if (mesh != null) mesh.dispose();
            mesh = new Mesh(true, maxVerts, maxIndices, 
                new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE));
        }

        float[] vertices = new float[maxVerts * 4]; // x,y,z,color
        short[] indices = new short[maxIndices];
        
        int vOffset = 0;
        int iOffset = 0;
        short baseIdx = 0;

        for (Map.Entry<Vector3, Integer> entry : voxels.entrySet()) {
            Vector3 localPos = entry.getKey();
            Vector3 localCenter = new Vector3(localPos.x + 0.5f, localPos.y + 0.5f, localPos.z + 0.5f);
            Vector3 worldCenter = new Vector3(localCenter).mul(body.getTransform());

            int wx = MathUtils.floor(worldCenter.x);
            int wy = MathUtils.floor(worldCenter.y);
            int wz = MathUtils.floor(worldCenter.z);

            boolean blocked = world.getBlock(wx, wy, wz) != 0;
            float color = blocked ? Color.RED.toFloatBits() : Color.WHITE.toFloatBits();

            for (int i = 0; i < CUBE_VERTS.length; i += 3) {
                vertices[vOffset++] = wx + CUBE_VERTS[i];
                vertices[vOffset++] = wy + CUBE_VERTS[i+1];
                vertices[vOffset++] = wz + CUBE_VERTS[i+2];
                vertices[vOffset++] = color;
            }

            for (short idx : CUBE_INDICES) {
                indices[iOffset++] = (short)(baseIdx + idx);
            }
            baseIdx += 24;
        }

        mesh.setVertices(vertices, 0, vOffset);
        mesh.setIndices(indices, 0, iOffset);
    }

    public void render(Camera camera) {
        if (mesh == null || mesh.getNumVertices() == 0) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDepthMask(false);

        shader.bind();
        shader.setUniformMatrix("u_projTrans", camera.combined);
        shader.setUniformMatrix("u_worldTrans", new Matrix4()); // vertices already in world space
        shader.setUniformf("u_opacity", LatchConstants.GHOST_OPACITY);

        mesh.render(shader, GL20.GL_TRIANGLES);

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public void clear() {
        if (mesh != null) {
            // effectively "empty" the mesh but keep the buffer
            mesh.setVertices(new float[0]);
        }
        needsRebuild = true;
    }

    @Override
    public void dispose() {
        if (mesh != null) mesh.dispose();
    }
}
