package com.zeal.voxel.render.post;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.zeal.voxel.render.shader.ShaderManager;
import com.zeal.voxel.render.shader.ShaderPrograms;
import com.zeal.voxel.render.shader.ShaderUniform;
import com.zeal.voxel.util.SsaoConstants;

import java.util.Random;

/**
 * Screen-Space Ambient Occlusion pass.
 * Generates a hemisphere sampling kernel and noise texture at construction time.
 * Renders AO in one pass, then applies a 4×4 box blur in a second pass.
 */
public class SsaoPass {

    private final ShaderManager shaderManager;
    private final Mesh fullscreenQuad;

    // Kernel samples (hemisphere, z > 0)
    private final float[] kernelSamples;

    // 4×4 noise texture
    private Texture noiseTexture;

    // FBOs
    private FrameBuffer ssaoBuffer;
    private FrameBuffer ssaoBlurBuffer;

    public SsaoPass(ShaderManager shaderManager, Mesh fullscreenQuad, int width, int height) {
        this.shaderManager = shaderManager;
        this.fullscreenQuad = fullscreenQuad;
        this.kernelSamples = generateKernel();
        this.noiseTexture = generateNoiseTexture();
        createBuffers(width, height);
    }

    private float[] generateKernel() {
        Random random = new Random(42); // deterministic for consistency
        float[] kernel = new float[SsaoConstants.SSAO_KERNEL_SIZE * 3];

        for (int i = 0; i < SsaoConstants.SSAO_KERNEL_SIZE; i++) {
            // Random direction in hemisphere (z > 0)
            float x = random.nextFloat() * 2f - 1f;
            float y = random.nextFloat() * 2f - 1f;
            float z = random.nextFloat(); // z always positive (hemisphere)

            // Normalize
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len > 0) { x /= len; y /= len; z /= len; }

            // Accelerate samples toward origin: scale by lerp(0.1, 1.0, (i/64)^2)
            float scale = (float) i / SsaoConstants.SSAO_KERNEL_SIZE;
            scale = 0.1f + scale * scale * (1.0f - 0.1f);
            x *= scale;
            y *= scale;
            z *= scale;

            kernel[i * 3] = x;
            kernel[i * 3 + 1] = y;
            kernel[i * 3 + 2] = z;
        }

        return kernel;
    }

    private Texture generateNoiseTexture() {
        Random random = new Random(123);
        int size = SsaoConstants.NOISE_TEXTURE_SIZE;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                // Random rotation vectors (xy), z=0, stored as color
                float rx = random.nextFloat() * 2f - 1f;
                float ry = random.nextFloat() * 2f - 1f;
                // Pack into [0,1] range for texture storage
                int r = (int) ((rx * 0.5f + 0.5f) * 255);
                int g = (int) ((ry * 0.5f + 0.5f) * 255);
                pixmap.drawPixel(x, y, (r << 24) | (g << 16) | (0 << 8) | 255);
            }
        }

        Texture tex = new Texture(pixmap);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        pixmap.dispose();
        return tex;
    }

    private void createBuffers(int width, int height) {
        if (width <= 0) width = 1;
        if (height <= 0) height = 1;
        ssaoBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        ssaoBlurBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
    }

    /**
     * Renders the SSAO pass using normal and depth buffers.
     * @param normalBuffer view-space normals (MRT attachment 1)
     * @param depthBuffer  linear depth (MRT attachment 2)
     * @param projection   camera projection matrix
     */
    public void render(MrtFrameBuffer sceneBuffer, Matrix4 projection) {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        // --- SSAO Pass ---
        ssaoBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        ShaderProgram ssaoShader = shaderManager.get(ShaderPrograms.POST_SSAO);
        ssaoShader.bind();

        // Bind normal buffer (MRT attachment 1)
        sceneBuffer.bindColorTexture(1, 0);
        ssaoShader.setUniformi("u_normalBuffer", 0);

        // Bind depth buffer (MRT attachment 2)
        sceneBuffer.bindColorTexture(2, 1);
        ssaoShader.setUniformi("u_depthBuffer", 1);

        // Bind noise texture
        noiseTexture.bind(2);
        ssaoShader.setUniformi("u_noise", 2);

        // Kernel samples
        for (int i = 0; i < SsaoConstants.SSAO_KERNEL_SIZE; i++) {
            String name = "u_samples[" + i + "]";
            if (ssaoShader.hasUniform(name)) {
                ssaoShader.setUniformf(name, kernelSamples[i * 3], kernelSamples[i * 3 + 1], kernelSamples[i * 3 + 2]);
            }
        }

        ShaderUniform.setMatrix4(ssaoShader, "u_projection", projection);
        Matrix4 invProj = new Matrix4(projection).inv();
        ShaderUniform.setMatrix4(ssaoShader, "u_invProjection", invProj);
        ssaoShader.setUniformf("u_screenSize", w, h);
        ShaderUniform.setFloat(ssaoShader, "u_radius", SsaoConstants.SSAO_RADIUS);
        ShaderUniform.setFloat(ssaoShader, "u_bias", SsaoConstants.SSAO_BIAS);

        fullscreenQuad.render(ssaoShader, GL20.GL_TRIANGLES);
        ssaoBuffer.end();

        // --- Blur Pass ---
        ssaoBlurBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        ShaderProgram blurShader = shaderManager.get(ShaderPrograms.POST_SSAO_BLUR);
        blurShader.bind();

        ssaoBuffer.getColorBufferTexture().bind(0);
        blurShader.setUniformi("u_ssaoInput", 0);
        blurShader.setUniformf("u_texelSize", 1f / w, 1f / h);

        fullscreenQuad.render(blurShader, GL20.GL_TRIANGLES);
        ssaoBlurBuffer.end();
    }

    /** Returns the blurred SSAO result texture. */
    public Texture getResult() {
        return ssaoBlurBuffer.getColorBufferTexture();
    }

    public void resize(int width, int height) {
        if (ssaoBuffer != null) ssaoBuffer.dispose();
        if (ssaoBlurBuffer != null) ssaoBlurBuffer.dispose();
        createBuffers(width, height);
    }

    public void dispose() {
        if (ssaoBuffer != null) ssaoBuffer.dispose();
        if (ssaoBlurBuffer != null) ssaoBlurBuffer.dispose();
        if (noiseTexture != null) noiseTexture.dispose();
    }
}
