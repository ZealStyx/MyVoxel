package com.zeal.voxel.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.GridPoint3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.player.AabbSelector;
import com.zeal.voxel.player.FloodFillSelector;
import com.zeal.voxel.player.SelectionMode;
import com.zeal.voxel.render.shader.ShaderManager;
import com.zeal.voxel.render.shader.ShaderPrograms;
import com.zeal.voxel.render.shader.ShaderUniform;

import java.util.Map;

public class SelectionRenderer {
    private final ShaderManager shaderManager;
    private final Mesh boxMesh;
    private final Mesh lineMesh;
    private final Matrix4 modelTrans = new Matrix4();
    private float time = 0f;

    public SelectionRenderer(ShaderManager shaderManager) {
        this.shaderManager = shaderManager;
        this.boxMesh = createWireframeBox();
        this.lineMesh = new Mesh(false, 2, 0, new VertexAttribute(Usage.Position, 3, "a_position"));
        this.lineMesh.setVertices(new float[] { 0,0,0, 1,1,1 });
    }

    private Mesh createWireframeBox() {
        float[] vertices = {
            0,0,0, 1,0,0,  1,0,0, 1,0,1,  1,0,1, 0,0,1,  0,0,1, 0,0,0,
            0,1,0, 1,1,0,  1,1,0, 1,1,1,  1,1,1, 0,1,1,  0,1,1, 0,1,0,
            0,0,0, 0,1,0,  1,0,0, 1,1,0,  1,0,1, 1,1,1,  0,0,1, 0,1,1
        };
        short[] indices = new short[24];
        for (short i = 0; i < 24; i++) indices[i] = i;
        Mesh mesh = new Mesh(true, 24, 24, new VertexAttribute(Usage.Position, 3, "a_position"));
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        return mesh;
    }

    public void render(Camera camera, com.zeal.voxel.player.Player player) {
        time += Gdx.graphics.getDeltaTime();

        ShaderProgram shader = shaderManager.get(ShaderPrograms.SELECTION);
        shader.bind();
        ShaderUniform.setMatrix4(shader, "u_projViewTrans", camera.combined);
        ShaderUniform.setFloat(shader, "u_time", time);

        // Selection Tool
        if (player.getInteractionState() == com.zeal.voxel.player.Player.InteractionState.SELECTING) {
            if (player.getSelectionMode() == SelectionMode.AABB) {
                renderAabb(shader, player.getSelectionTool().getAabbSelector());
            } else {
                renderFloodFill(shader, player.getSelectionTool().getFloodFillSelector());
            }
        }

        // Latch Tool
        com.zeal.voxel.player.Latch latch = player.getLatch();
        com.zeal.voxel.player.LatchState lstate = latch.getState();

        if (lstate == com.zeal.voxel.player.LatchState.TARGETING && latch.getLastHit() != null) {
            com.zeal.voxel.physics.RaycastResult hit = latch.getLastHit();
            boolean isBody = hit.body instanceof com.zeal.voxel.physics.PhysicsBody;
            
            // Highlight targeted voxel face or body
            if (isBody) {
                ShaderUniform.set4f(shader, "u_lineColor", 0.0f, 1.0f, 1.0f, 1.0f); // Teal
                // For simplicity, just draw a box around the body position or reuse body mesh?
                // Let's just draw an amber/teal highlight at the hit point
                renderTargetHighlight(shader, hit.pointWorld, true);
            } else {
                ShaderUniform.set4f(shader, "u_lineColor", 1.0f, 0.7f, 0.0f, 1.0f); // Amber
                renderTargetHighlight(shader, hit.pointWorld, false);
            }
        }

        if (latch.getHeldBody() != null) {
            com.zeal.voxel.physics.PhysicsBody body = latch.getHeldBody();
            Vector3 localPivot = latch.getLocalPivot();
            Vector3 worldPivot = com.zeal.voxel.physics.CoordinateUtil.localToWorld(localPivot, body);
            
            // Draw Teal Latch Line from camera to pivot
            ShaderUniform.set4f(shader, "u_lineColor", 0.0f, 1.0f, 1.0f, 0.8f); // Teal
            lineMesh.setVertices(new float[] { 
                camera.position.x, camera.position.y, camera.position.z, 
                worldPivot.x, worldPivot.y, worldPivot.z 
            });
            modelTrans.idt();
            ShaderUniform.setMatrix4(shader, "u_modelTrans", modelTrans);
            lineMesh.render(shader, GL20.GL_LINES);

            // Draw Glowing Teal Dot at pivot
            ShaderUniform.set4f(shader, "u_lineColor", 0.0f, 1.0f, 1.0f, 1.0f);
            float dotSize = 0.03f;
            renderStretchedBox(shader, 
                worldPivot.x - dotSize, worldPivot.y - dotSize, worldPivot.z - dotSize,
                worldPivot.x + dotSize, worldPivot.y + dotSize, worldPivot.z + dotSize);
            
            // Pulse outline for held body
            float pulse = 0.7f + 0.3f * com.badlogic.gdx.math.MathUtils.sin(time * 4f);
            if (latch.getPlacementMode() == com.zeal.voxel.player.PlacementMode.ASSEMBLE) {
                ShaderUniform.set4f(shader, "u_lineColor", 1.0f, 0.7f, 0.0f, pulse); // Amber pulse
            } else {
                ShaderUniform.set4f(shader, "u_lineColor", 0.0f, 1.0f, 1.0f, pulse); // Teal pulse
            }
            // (Outline rendering would ideally happen in PhysicsBodyRenderer, but we can draw a simple AABB here)
            // For now, the teal line and dot are sufficient as per spec.
        }
    }

    private void renderTargetHighlight(ShaderProgram shader, Vector3 point, boolean isBody) {
        float x = (float)Math.floor(point.x);
        float y = (float)Math.floor(point.y);
        float z = (float)Math.floor(point.z);
        renderStretchedBox(shader, x, y, z, x + 1, y + 1, z + 1);
    }

    private void renderStretchedBox(ShaderProgram shader, float x0, float y0, float z0, float x1, float y1, float z1) {
        float sx = x1 - x0;
        float sy = y1 - y0;
        float sz = z1 - z0;
        modelTrans.idt().translate(x0, y0, z0).scale(sx, sy, sz);
        ShaderUniform.setMatrix4(shader, "u_modelTrans", modelTrans);
        boxMesh.render(shader, GL20.GL_LINES);
    }

    private void renderAabb(ShaderProgram shader, AabbSelector aabb) {
        GridPoint3 a = aabb.getCornerA();
        GridPoint3 bPrev = aabb.getCornerBPreview();

        // Corner A — green
        if (a != null) {
            ShaderUniform.set4f(shader, "u_lineColor", 0.2f, 1.0f, 0.3f, 1.0f);
            modelTrans.setToTranslation(a.x, a.y, a.z);
            ShaderUniform.setMatrix4(shader, "u_modelTrans", modelTrans);
            boxMesh.render(shader, GL20.GL_LINES);
        }

        // Corner B preview — yellow
        if (bPrev != null) {
            ShaderUniform.set4f(shader, "u_lineColor", 1.0f, 1.0f, 0.2f, 1.0f);
            modelTrans.setToTranslation(bPrev.x, bPrev.y, bPrev.z);
            ShaderUniform.setMatrix4(shader, "u_modelTrans", modelTrans);
            boxMesh.render(shader, GL20.GL_LINES);
        }

        // AABB preview box — translucent white spanning the full selection
        if (a != null && bPrev != null) {
            int minX = Math.min(a.x, bPrev.x);
            int minY = Math.min(a.y, bPrev.y);
            int minZ = Math.min(a.z, bPrev.z);
            int maxX = Math.max(a.x, bPrev.x) + 1;
            int maxY = Math.max(a.y, bPrev.y) + 1;
            int maxZ = Math.max(a.z, bPrev.z) + 1;

            ShaderUniform.set4f(shader, "u_lineColor", 1.0f, 1.0f, 1.0f, 0.5f);
            renderStretchedBox(shader, minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private void renderFloodFill(ShaderProgram shader, FloodFillSelector fill) {
        Map<Vector3, ?> blocks = fill.getSelectedBlocks();
        if (blocks.isEmpty()) return;

        ShaderUniform.set4f(shader, "u_lineColor", 0.3f, 0.6f, 1.0f, 1.0f);
        for (Vector3 pos : blocks.keySet()) {
            modelTrans.setToTranslation(pos);
            ShaderUniform.setMatrix4(shader, "u_modelTrans", modelTrans);
            boxMesh.render(shader, GL20.GL_LINES);
        }
    }



    public void dispose() {
        boxMesh.dispose();
        lineMesh.dispose();
    }
}
