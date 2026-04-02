package com.zeal.voxel.render.post;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.IntBuffer;

/**
 * Custom framebuffer with multiple color attachments (MRT).
 * Requires OpenGL 3.0+ / GLES 3.0+.
 * 
 * Attachments:
 *   0: Scene color RGB + emissive (alpha)
 *   1: View-space normals (RGB)
 *   2: Linear depth (R, 32-bit float — uses RGBA8 as fallback)
 */
public class MrtFrameBuffer {

    private int framebufferHandle;
    private int depthRenderbufferHandle;
    private final int[] colorTextureHandles;
    private final int width;
    private final int height;
    private final int numColorAttachments;

    public MrtFrameBuffer(int width, int height, int numColorAttachments) {
        this.width = width;
        this.height = height;
        this.numColorAttachments = numColorAttachments;
        this.colorTextureHandles = new int[numColorAttachments];
        build();
    }

    private void build() {
        GL20 gl = Gdx.gl;

        // Create framebuffer
        IntBuffer buf = BufferUtils.newIntBuffer(1);
        gl.glGenFramebuffers(1, buf);
        framebufferHandle = buf.get(0);
        gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebufferHandle);

        // Create color textures
        IntBuffer texBuf = BufferUtils.newIntBuffer(numColorAttachments);
        gl.glGenTextures(numColorAttachments, texBuf);
        
        IntBuffer drawBuffers = BufferUtils.newIntBuffer(numColorAttachments);
        
        for (int i = 0; i < numColorAttachments; i++) {
            colorTextureHandles[i] = texBuf.get(i);
            gl.glBindTexture(GL20.GL_TEXTURE_2D, colorTextureHandles[i]);
            // Use RGBA8 for all attachments (ES 3.0 safe)
            gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGBA, width, height, 0,
                GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, null);
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_NEAREST);
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_NEAREST);
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);

            gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0 + i,
                GL20.GL_TEXTURE_2D, colorTextureHandles[i], 0);
            
            drawBuffers.put(i, GL20.GL_COLOR_ATTACHMENT0 + i);
        }

        // Set draw buffers (GL30)
        if (Gdx.gl30 != null) {
            Gdx.gl30.glDrawBuffers(numColorAttachments, drawBuffers);
        }

        // Create depth renderbuffer
        IntBuffer rbBuf = BufferUtils.newIntBuffer(1);
        gl.glGenRenderbuffers(1, rbBuf);
        depthRenderbufferHandle = rbBuf.get(0);
        gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, depthRenderbufferHandle);
        gl.glRenderbufferStorage(GL20.GL_RENDERBUFFER, GL20.GL_DEPTH_COMPONENT16, width, height);
        gl.glFramebufferRenderbuffer(GL20.GL_FRAMEBUFFER, GL20.GL_DEPTH_ATTACHMENT,
            GL20.GL_RENDERBUFFER, depthRenderbufferHandle);

        // Verify completeness
        int status = gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER);
        if (status != GL20.GL_FRAMEBUFFER_COMPLETE) {
            Gdx.app.error("MrtFrameBuffer", "Framebuffer not complete: " + status);
        }

        gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
    }

    public void begin() {
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebufferHandle);
        Gdx.gl.glViewport(0, 0, width, height);
    }

    public void end() {
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    }

    /** Bind a specific color attachment as a texture for reading. */
    public void bindColorTexture(int attachment, int unit) {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + unit);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, colorTextureHandles[attachment]);
    }

    /** Get the GL texture handle for a specific color attachment. */
    public int getColorTextureHandle(int attachment) {
        return colorTextureHandles[attachment];
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void dispose() {
        GL20 gl = Gdx.gl;
        IntBuffer buf = BufferUtils.newIntBuffer(1);

        for (int handle : colorTextureHandles) {
            buf.clear();
            buf.put(handle);
            buf.flip();
            gl.glDeleteTextures(1, buf);
        }

        buf.clear();
        buf.put(depthRenderbufferHandle);
        buf.flip();
        gl.glDeleteRenderbuffers(1, buf);

        buf.clear();
        buf.put(framebufferHandle);
        buf.flip();
        gl.glDeleteFramebuffers(1, buf);
    }
}
