package com.zeal.voxel.render.shadow;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.util.CsmConstants;

/**
 * 4-cascade directional shadow map.
 * Replaces the single ShadowMap with proper frustum-fitted cascades for high-quality shadows
 * at all distances.
 */
public class CascadedShadowMap {

    private final FrameBuffer[] depthFbos = new FrameBuffer[CsmConstants.CASCADE_COUNT];
    private final OrthographicCamera[] sunCameras = new OrthographicCamera[CsmConstants.CASCADE_COUNT];
    private final Matrix4[] lightSpaceMatrices = new Matrix4[CsmConstants.CASCADE_COUNT];
    private final float[] cascadeSplits = new float[CsmConstants.CASCADE_COUNT + 1];

    private final Vector3 sunDirection = new Vector3(0.3f, 1.0f, 0.4f).nor();
    private final Vector3 lastUpdatePos = new Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);

    // Temp vectors
    private final Vector3 tmpCenter = new Vector3();
    private final Vector3 tmpMin = new Vector3();
    private final Vector3 tmpMax = new Vector3();

    public CascadedShadowMap() {
        for (int i = 0; i < CsmConstants.CASCADE_COUNT; i++) {
            int res = CsmConstants.CASCADE_RESOLUTIONS[i];
            depthFbos[i] = new FrameBuffer(Pixmap.Format.RGBA8888, res, res, true);
            depthFbos[i].getColorBufferTexture().setFilter(
                Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

            sunCameras[i] = new OrthographicCamera();
            lightSpaceMatrices[i] = new Matrix4();
        }
    }

    /**
     * Recomputes cascade splits and fits sun cameras to the player camera frustum.
     * @param playerCamera the main perspective camera
     */
    public void update(PerspectiveCamera playerCamera) {
        // Skip update if player hasn't moved enough
        if (lastUpdatePos.dst(playerCamera.position) < 4f) {
            return;
        }
        lastUpdatePos.set(playerCamera.position);

        float near = playerCamera.near;
        float far = playerCamera.far;
        float lambda = CsmConstants.SPLIT_LAMBDA;

        // Compute cascade splits using practical logarithmic scheme
        for (int i = 0; i <= CsmConstants.CASCADE_COUNT; i++) {
            float p = (float) i / CsmConstants.CASCADE_COUNT;
            float log = near * (float) Math.pow(far / near, p);
            float linear = near + (far - near) * p;
            cascadeSplits[i] = lambda * log + (1.0f - lambda) * linear;
        }
        cascadeSplits[0] = near;
        cascadeSplits[CsmConstants.CASCADE_COUNT] = far;

        // For each cascade, fit an ortho camera around the sub-frustum
        for (int i = 0; i < CsmConstants.CASCADE_COUNT; i++) {
            fitSunCamera(i, playerCamera, cascadeSplits[i], cascadeSplits[i + 1]);
        }
    }

    private void fitSunCamera(int cascade, PerspectiveCamera cam, float splitNear, float splitFar) {
        // Build 8 frustum corners for the sub-frustum [splitNear, splitFar]
        Vector3[] corners = getFrustumCorners(cam, splitNear, splitFar);

        // Compute center of the frustum
        tmpCenter.setZero();
        for (Vector3 corner : corners) {
            tmpCenter.add(corner);
        }
        tmpCenter.scl(1f / corners.length);

        // Build a view matrix looking from the sun's direction
        OrthographicCamera sunCam = sunCameras[cascade];
        sunCam.position.set(tmpCenter).add(sunDirection.x * 100f, sunDirection.y * 100f, sunDirection.z * 100f);
        sunCam.lookAt(tmpCenter);
        sunCam.up.set(0, 1, 0);
        // Avoid gimbal lock if sun is nearly vertical
        if (Math.abs(sunDirection.dot(0, 1, 0)) > 0.99f) {
            sunCam.up.set(0, 0, 1);
        }
        sunCam.update();

        // Transform corners into sun-view space and compute AABB
        Matrix4 sunView = sunCam.view;
        tmpMin.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        tmpMax.set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);

        for (Vector3 corner : corners) {
            Vector3 transformed = new Vector3(corner).mul(sunView);
            tmpMin.x = Math.min(tmpMin.x, transformed.x);
            tmpMin.y = Math.min(tmpMin.y, transformed.y);
            tmpMin.z = Math.min(tmpMin.z, transformed.z);
            tmpMax.x = Math.max(tmpMax.x, transformed.x);
            tmpMax.y = Math.max(tmpMax.y, transformed.y);
            tmpMax.z = Math.max(tmpMax.z, transformed.z);
        }

        // Set ortho bounds
        float halfW = (tmpMax.x - tmpMin.x) * 0.5f;
        float halfH = (tmpMax.y - tmpMin.y) * 0.5f;
        sunCam.viewportWidth = halfW * 2f;
        sunCam.viewportHeight = halfH * 2f;
        sunCam.near = 0.1f;
        sunCam.far = (tmpMax.z - tmpMin.z) + 50f; // extra margin

        // Recenter the camera
        float centerX = (tmpMin.x + tmpMax.x) * 0.5f;
        float centerY = (tmpMin.y + tmpMax.y) * 0.5f;
        // Move camera to correct position in world space
        Matrix4 invSunView = new Matrix4(sunView).inv();
        Vector3 sunCenter = new Vector3(centerX, centerY, tmpMin.z - 25f).mul(invSunView);
        sunCam.position.set(sunCenter);
        sunCam.lookAt(sunCenter.x - sunDirection.x, sunCenter.y - sunDirection.y, sunCenter.z - sunDirection.z);
        if (Math.abs(sunDirection.dot(0, 1, 0)) > 0.99f) {
            sunCam.up.set(0, 0, 1);
        } else {
            sunCam.up.set(0, 1, 0);
        }
        sunCam.update();

        lightSpaceMatrices[cascade].set(sunCam.combined);
    }

    /** Computes the 8 corners of a perspective frustum between nearDist and farDist. */
    private Vector3[] getFrustumCorners(PerspectiveCamera cam, float nearDist, float farDist) {
        float aspect = cam.viewportWidth / cam.viewportHeight;
        float fovRad = (float) Math.toRadians(cam.fieldOfView);
        float tanHalfFov = (float) Math.tan(fovRad * 0.5f);

        float nearH = tanHalfFov * nearDist;
        float nearW = nearH * aspect;
        float farH = tanHalfFov * farDist;
        float farW = farH * aspect;

        Vector3 forward = new Vector3(cam.direction).nor();
        Vector3 right = new Vector3(cam.direction).crs(cam.up).nor();
        Vector3 up = new Vector3(right).crs(forward).nor();

        Vector3 nearCenter = new Vector3(cam.position).add(new Vector3(forward).scl(nearDist));
        Vector3 farCenter = new Vector3(cam.position).add(new Vector3(forward).scl(farDist));

        return new Vector3[] {
            // Near plane
            new Vector3(nearCenter).add(new Vector3(up).scl(nearH)).add(new Vector3(right).scl(-nearW)),
            new Vector3(nearCenter).add(new Vector3(up).scl(nearH)).add(new Vector3(right).scl(nearW)),
            new Vector3(nearCenter).add(new Vector3(up).scl(-nearH)).add(new Vector3(right).scl(nearW)),
            new Vector3(nearCenter).add(new Vector3(up).scl(-nearH)).add(new Vector3(right).scl(-nearW)),
            // Far plane
            new Vector3(farCenter).add(new Vector3(up).scl(farH)).add(new Vector3(right).scl(-farW)),
            new Vector3(farCenter).add(new Vector3(up).scl(farH)).add(new Vector3(right).scl(farW)),
            new Vector3(farCenter).add(new Vector3(up).scl(-farH)).add(new Vector3(right).scl(farW)),
            new Vector3(farCenter).add(new Vector3(up).scl(-farH)).add(new Vector3(right).scl(-farW)),
        };
    }

    /** Bind cascade depth FBO for rendering. */
    public void beginDepthPass(int cascade) {
        depthFbos[cascade].begin();
        Gdx.gl.glClearColor(1, 1, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    /** Unbind cascade depth FBO. */
    public void endDepthPass(int cascade) {
        depthFbos[cascade].end();
    }

    /** Get the sun camera for a specific cascade (used for depth rendering). */
    public OrthographicCamera getSunCamera(int cascade) {
        return sunCameras[cascade];
    }

    /** Get all light-space matrices (for shader uniforms). */
    public Matrix4[] getLightSpaceMatrices() {
        return lightSpaceMatrices;
    }

    /** Get all shadow map depth textures (for shader bind). */
    public Texture[] getShadowMapTextures() {
        Texture[] textures = new Texture[CsmConstants.CASCADE_COUNT];
        for (int i = 0; i < CsmConstants.CASCADE_COUNT; i++) {
            textures[i] = depthFbos[i].getColorBufferTexture();
        }
        return textures;
    }

    /** Get the cascade split distances. Returns array of size CASCADE_COUNT+1. */
    public float[] getCascadeSplits() {
        return cascadeSplits;
    }

    public void dispose() {
        for (FrameBuffer fbo : depthFbos) {
            if (fbo != null) fbo.dispose();
        }
    }
}
