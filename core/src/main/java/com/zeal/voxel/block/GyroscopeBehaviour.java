package com.zeal.voxel.block;

import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.physics.PhysicsBody;

public class GyroscopeBehaviour implements BlockBehaviour {
    private static final float DEFAULT_STABILIZATION_STRENGTH = 5.0f;

    @Override
    public void onTick(PhysicsBody body,
                       int localX,
                       int localY,
                       int localZ,
                       ResolvedBehaviour params,
                       float delta) {
        float stabilizationStrength = params.getFloat("stabilizationStrength", DEFAULT_STABILIZATION_STRENGTH);
        String axis = params.getString("axis", "y");

        Vector3 angularVel = body.getAngularVelocity();
        if (angularVel.len2() <= 0.001f) {
            return;
        }

        Vector3 counterTorque = switch (axis.toLowerCase()) {
            case "x" -> new Vector3(-angularVel.x, 0f, 0f);
            case "z" -> new Vector3(0f, 0f, -angularVel.z);
            default -> new Vector3(0f, -angularVel.y, 0f);
        };
        counterTorque.scl(stabilizationStrength * delta);
        body.applyTorque(counterTorque);
    }
}
