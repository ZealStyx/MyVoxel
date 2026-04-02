package com.zeal.voxel.block;

import com.zeal.voxel.physics.PhysicsBody;

public interface BlockBehaviour {
    void onTick(PhysicsBody body,
                int localX,
                int localY,
                int localZ,
                ResolvedBehaviour params,
                float delta);

    default void onAttach(PhysicsBody body,
                          int localX,
                          int localY,
                          int localZ,
                          ResolvedBehaviour params) {
    }

    default void onDetach(PhysicsBody body,
                          int localX,
                          int localY,
                          int localZ,
                          ResolvedBehaviour params) {
    }
}
