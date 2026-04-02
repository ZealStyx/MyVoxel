package com.zeal.voxel.render.culling;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.physics.PhysicsBody;
import com.zeal.voxel.util.Constants;

public class FrustumCuller {
    private final Frustum frustum = new Frustum();
    // OPTIMIZED: removed per-frame allocation — reusing cached vectors for AABB tests.
    private final Vector3 tmpAabbMin = new Vector3();
    private final Vector3 tmpAabbMax = new Vector3();
    private final Vector3 tmpCenter = new Vector3();
    private final Vector3 tmpHalf = new Vector3();

    public void update(Camera camera) {
        frustum.update(camera.combined);
    }

    public boolean isColumnVisible(int columnX, int columnZ) {
        float halfX = Constants.COLUMN_SIZE * 0.5f;
        float halfY = Constants.WORLD_HEIGHT * 0.5f;
        float halfZ = Constants.COLUMN_SIZE * 0.5f;
        float centerX = columnX * Constants.COLUMN_SIZE + halfX;
        float centerY = halfY;
        float centerZ = columnZ * Constants.COLUMN_SIZE + halfZ;
        return frustum.boundsInFrustum(centerX, centerY, centerZ, halfX, halfY, halfZ);
    }

    public boolean isSectionVisible(SubChunkSection section) {
        float half = SubChunkSection.SECTION_SIZE * 0.5f;
        return frustum.boundsInFrustum(section.centerX, section.centerY, section.centerZ, half, half, half);
    }

    public boolean isBodyVisible(PhysicsBody body) {
        body.getRigidBody().getAabb(tmpAabbMin, tmpAabbMax);
        tmpCenter.set(tmpAabbMin).add(tmpAabbMax).scl(0.5f);
        tmpHalf.set(tmpAabbMax).sub(tmpAabbMin).scl(0.5f);
        return frustum.boundsInFrustum(tmpCenter.x, tmpCenter.y, tmpCenter.z, tmpHalf.x, tmpHalf.y, tmpHalf.z);
    }
}
