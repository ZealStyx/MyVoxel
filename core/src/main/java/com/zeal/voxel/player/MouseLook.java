package com.zeal.voxel.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.util.Constants;

public class MouseLook {
    private float yaw = 0f;
    private float pitch = 0f;
    private boolean initialized = false;

    private void initialize(PerspectiveCamera camera) {
        // Calculate initial yaw/pitch from current direction
        Vector3 dir = new Vector3(camera.direction).nor();
        pitch = MathUtils.asin(dir.y) * MathUtils.radiansToDegrees;
        
        // Yaw from XZ plane
        yaw = MathUtils.atan2(dir.x, dir.z) * MathUtils.radiansToDegrees;
        initialized = true;
    }

    public void update(PerspectiveCamera camera, boolean suppressRotation) {
        if (!initialized) initialize(camera);
        
        if (!Gdx.input.isCursorCatched()) {
            return;
        }

        if (!suppressRotation) {
            float dx = Gdx.input.getDeltaX();
            float dy = Gdx.input.getDeltaY();

            yaw -= dx * Constants.MOUSE_SENSITIVITY;
            pitch -= dy * Constants.MOUSE_SENSITIVITY;
        }

        // Clamp pitch so camera can't flip over
        pitch = MathUtils.clamp(pitch, Constants.MIN_PITCH, Constants.MAX_PITCH);

        // Rebuild camera direction
        float cosPitch = MathUtils.cosDeg(pitch);
        camera.direction.set(
            cosPitch * MathUtils.sinDeg(yaw),
            MathUtils.sinDeg(pitch),
            cosPitch * MathUtils.cosDeg(yaw)
        ).nor();
        
        // Strictly maintain vertical up to prevent roll
        camera.up.set(0, 1, 0);
        camera.update();
    }

    public void setSensitivity(float s) {
        // Handled via Constants currently, or could be dynamic
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
