package com.zeal.voxel.render.shadow;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.util.Constants;

/** Directional shadow map from the sun. Provides depth FBO, sun camera, and light-space matrix. */
public class ShadowMap {
    private final FrameBuffer depthFbo;
    private final OrthographicCamera sunCamera;
    private final Matrix4 lightSpaceMatrix = new Matrix4();
    private final Vector3 lastUpdatePos = new Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
    private final Vector3 sunDirection = new Vector3(0.3f, 1.0f, 0.4f).nor();

    public ShadowMap() {
        int size = Constants.SHADOW_MAP_SIZE;
        depthFbo = new FrameBuffer(Pixmap.Format.RGBA8888, size, size, true);
        depthFbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        sunCamera = new OrthographicCamera();
        float r = Constants.SHADOW_RADIUS;
        sunCamera.viewportWidth = r * 2;
        sunCamera.viewportHeight = r * 2;
        sunCamera.near = 0.1f;
        sunCamera.far = r * 4;
    }

    /** Repositions the sun camera to follow the player. Recomputes lightSpaceMatrix. */
    public void update(Vector3 playerPos) {
        if (lastUpdatePos.dst(playerPos) < Constants.SHADOW_UPDATE_THRESHOLD) {
            return;
        }
        lastUpdatePos.set(playerPos);

        float r = Constants.SHADOW_RADIUS;
        sunCamera.position.set(playerPos).add(sunDirection.x * r, sunDirection.y * r, sunDirection.z * r);
        sunCamera.lookAt(playerPos);
        sunCamera.up.set(0, 1, 0);
        sunCamera.update();

        lightSpaceMatrix.set(sunCamera.combined);
    }

    /** Bind the depth FBO for the shadow depth pass. */
    public void beginDepthPass() {
        depthFbo.begin();
        Gdx.gl.glClearColor(1, 1, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    /** Unbind the depth FBO. */
    public void endDepthPass() {
        depthFbo.end();
    }

    public OrthographicCamera getSunCamera() {
        return sunCamera;
    }

    public Matrix4 getLightSpaceMatrix() {
        return lightSpaceMatrix;
    }

    public Texture getDepthTexture() {
        return depthFbo.getColorBufferTexture();
    }

    public void dispose() {
        depthFbo.dispose();
    }
}
