package com.zeal.voxel.block;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.physics.CoordinateUtil;
import com.zeal.voxel.physics.PhysicsBody;
import com.zeal.voxel.render.ao.FaceDirection;

public class ThrusterBehaviour implements BlockBehaviour {
    private static final float DEFAULT_FORCE = 50.0f;
    private static final String DEFAULT_EXHAUST_FACE = "south";
    private static final float DEFAULT_PARTICLE_SCALE = 1.0f;

    @Override
    public void onTick(PhysicsBody body,
                       int localX,
                       int localY,
                       int localZ,
                       ResolvedBehaviour params,
                       float delta) {
        if (!body.isActionActive()) {
            return;
        }

        float force = params.getFloat("force", DEFAULT_FORCE);
        String faceName = params.getString("exhaustFace", DEFAULT_EXHAUST_FACE);
        params.getFloat("particleScale", DEFAULT_PARTICLE_SCALE);

        FaceDirection face = parseFace(faceName);
        int[] normal = face.getNormal();

        Vector3 localDir = new Vector3(normal[0], normal[1], normal[2]);
        Quaternion rotation = body.getTransform().getRotation(new Quaternion());
        Vector3 worldDir = rotation.transform(localDir).nor();

        Vector3 forceVec = new Vector3(worldDir).scl(-force);

        Vector3 localPos = new Vector3(localX, localY, localZ);
        Vector3 worldPos = CoordinateUtil.localToWorld(localPos, body);
        Vector3 relativePos = new Vector3(worldPos).sub(body.getPosition());
        body.applyForce(forceVec, relativePos);
    }

    private FaceDirection parseFace(String value) {
        String normalized = value == null ? DEFAULT_EXHAUST_FACE : value.trim().toUpperCase();
        return switch (normalized) {
            case "TOP", "UP" -> FaceDirection.TOP;
            case "BOTTOM", "DOWN" -> FaceDirection.BOTTOM;
            case "NORTH" -> FaceDirection.NORTH;
            case "SOUTH" -> FaceDirection.SOUTH;
            case "EAST" -> FaceDirection.EAST;
            case "WEST" -> FaceDirection.WEST;
            default -> FaceDirection.SOUTH;
        };
    }
}
