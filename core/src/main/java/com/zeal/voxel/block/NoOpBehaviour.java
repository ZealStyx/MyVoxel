package com.zeal.voxel.block;

import com.zeal.voxel.physics.PhysicsBody;

public final class NoOpBehaviour implements BlockBehaviour {
    public static final NoOpBehaviour INSTANCE = new NoOpBehaviour();

    private NoOpBehaviour() {
    }

    @Override
    public void onTick(PhysicsBody body,
                       int localX,
                       int localY,
                       int localZ,
                       ResolvedBehaviour params,
                       float delta) {
        // No-op by design.
    }
}
