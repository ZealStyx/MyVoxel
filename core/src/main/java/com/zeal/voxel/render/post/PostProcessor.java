package com.zeal.voxel.render.post;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.zeal.voxel.render.shader.ShaderManager;
import com.zeal.voxel.render.shader.ShaderPrograms;
import com.zeal.voxel.render.shader.ShaderUniform;
import com.zeal.voxel.util.Constants;

/**
 * Full-screen post-processing pipeline.
 * Pipeline: scene → SSAO → motion blur → bloom → composite.
 */
public class PostProcessor {
    private final ShaderManager shaderManager;
    private final Mesh fullscreenQuad;

    // MRT scene buffer (color + normals + depth)
    private MrtFrameBuffer sceneBuffer;

    // Standard FBOs for post-processing stages
    private FrameBuffer bloomBrightBuffer;
    private FrameBuffer bloomPingBuffer;
    private FrameBuffer motionBlurBuffer;

    // SSAO
    private SsaoPass ssaoPass;

    public PostProcessor(ShaderManager shaderManager) {
        this.shaderManager = shaderManager;
        this.fullscreenQuad = createFullscreenQuad();
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        createBuffers(w, h);
        ssaoPass = new SsaoPass(shaderManager, fullscreenQuad, w, h);
    }

    private Mesh createFullscreenQuad() {
        float[] vertices = {
            // x, y, z,  u, v
            -1, -1, 0,  0, 0,
             1, -1, 0,  1, 0,
             1,  1, 0,  1, 1,
            -1,  1, 0,  0, 1
        };
        short[] indices = { 0, 1, 2, 2, 3, 0 };

        Mesh mesh = new Mesh(true, 4, 6,
            new VertexAttribute(Usage.Position, 3, "a_position"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0")
        );
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        return mesh;
    }

    private void createBuffers(int width, int height) {
        if (width <= 0) width = 1;
        if (height <= 0) height = 1;
        // MRT buffer: 3 color attachments (scene color, view-space normals, linear depth)
        sceneBuffer = new MrtFrameBuffer(width, height, 3);
        bloomBrightBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        bloomPingBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        motionBlurBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
    }

    /** Begin rendering the 3D scene into the MRT offscreen buffer. */
    public void beginScene() {
        sceneBuffer.begin();
        Gdx.gl.glClearColor(0.2f, 0.4f, 0.6f, 0.0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    /** End rendering the 3D scene. */
    public void endScene() {
        sceneBuffer.end();
    }

    /** Returns the MRT scene buffer for rendering into. */
    public MrtFrameBuffer getSceneBuffer() {
        return sceneBuffer;
    }

    /**
     * Applies the full post-processing chain and renders to the default framebuffer.
     * Order: SSAO → motion blur → bloom → composite.
     */
    public void applyEffects(FrameBuffer velocityBuffer, Matrix4 projection) {
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        // --- SSAO Pass (uses MRT normal + depth buffers) ---
        ssaoPass.render(sceneBuffer, projection);

        // --- Motion Blur ---
        motionBlurBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        ShaderProgram mbShader = shaderManager.get(ShaderPrograms.POST_MOTIONBLUR);
        mbShader.bind();
        // Bind scene color (MRT attachment 0)
        sceneBuffer.bindColorTexture(0, 0);
        mbShader.setUniformi("u_scene", 0);
        velocityBuffer.getColorBufferTexture().bind(1);
        mbShader.setUniformi("u_velocity", 1);
        ShaderUniform.setFloat(mbShader, "u_shutterSpeed", Constants.SHUTTER_SPEED);
        fullscreenQuad.render(mbShader, GL20.GL_TRIANGLES);
        motionBlurBuffer.end();

        // --- Bloom: Bright-pass extraction ---
        bloomBrightBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        ShaderProgram bloomShader = shaderManager.get(ShaderPrograms.POST_BLOOM);
        bloomShader.bind();
        motionBlurBuffer.getColorBufferTexture().bind(0);
        bloomShader.setUniformi("u_scene", 0);
        bloomShader.setUniformf("u_texelSize", 1.0f / w, 1.0f / h);
        bloomShader.setUniformi("u_horizontal", 2); // bright-pass mode
        fullscreenQuad.render(bloomShader, GL20.GL_TRIANGLES);
        bloomBrightBuffer.end();

        // --- Bloom: Horizontal blur ---
        bloomPingBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        bloomShader.bind();
        bloomBrightBuffer.getColorBufferTexture().bind(0);
        bloomShader.setUniformi("u_scene", 0);
        bloomShader.setUniformf("u_texelSize", 1.0f / w, 1.0f / h);
        bloomShader.setUniformi("u_horizontal", 1);
        fullscreenQuad.render(bloomShader, GL20.GL_TRIANGLES);
        bloomPingBuffer.end();

        // --- Bloom: Vertical blur ---
        bloomBrightBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        bloomShader.bind();
        bloomPingBuffer.getColorBufferTexture().bind(0);
        bloomShader.setUniformi("u_scene", 0);
        bloomShader.setUniformf("u_texelSize", 1.0f / w, 1.0f / h);
        bloomShader.setUniformi("u_horizontal", 0);
        fullscreenQuad.render(bloomShader, GL20.GL_TRIANGLES);
        bloomBrightBuffer.end();

        // --- Composite: scene + bloom + SSAO → screen ---
        ShaderProgram compShader = shaderManager.get(ShaderPrograms.POST_COMPOSITE);
        compShader.bind();
        motionBlurBuffer.getColorBufferTexture().bind(0);
        compShader.setUniformi("u_scene", 0);
        bloomBrightBuffer.getColorBufferTexture().bind(1);
        compShader.setUniformi("u_bloom", 1);
        ssaoPass.getResult().bind(2);
        compShader.setUniformi("u_ssao", 2);
        ShaderUniform.setFloat(compShader, "u_bloomStrength", Constants.BLOOM_STRENGTH);
        fullscreenQuad.render(compShader, GL20.GL_TRIANGLES);
    }

    /** Recreates FBOs on viewport resize. */
    public void resize(int width, int height) {
        if (sceneBuffer != null) sceneBuffer.dispose();
        if (bloomBrightBuffer != null) bloomBrightBuffer.dispose();
        if (bloomPingBuffer != null) bloomPingBuffer.dispose();
        if (motionBlurBuffer != null) motionBlurBuffer.dispose();
        createBuffers(width, height);
        ssaoPass.resize(width, height);
    }

    public void dispose() {
        fullscreenQuad.dispose();
        if (sceneBuffer != null) sceneBuffer.dispose();
        if (bloomBrightBuffer != null) bloomBrightBuffer.dispose();
        if (bloomPingBuffer != null) bloomPingBuffer.dispose();
        if (motionBlurBuffer != null) motionBlurBuffer.dispose();
        if (ssaoPass != null) ssaoPass.dispose();
    }
}
