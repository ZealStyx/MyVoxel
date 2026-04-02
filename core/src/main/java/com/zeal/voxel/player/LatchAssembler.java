package com.zeal.voxel.player;

import com.badlogic.gdx.math.GridPoint3;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.physics.BulletWorld;
import com.zeal.voxel.physics.PhysicsBody;
import com.zeal.voxel.physics.PhysicsBodyFactory;
import com.zeal.voxel.world.WorldGrid;

public class LatchAssembler {

    public PhysicsBody assembleAndLatch(Vector3 hitPointWorld,
                                       WorldGrid world,
                                       PhysicsBodyFactory factory,
                                       FloodFillSelector filler,
                                       BulletWorld bw) {
        
        // 1. Raycast hit a static block at worldPos (already passed as hitPointWorld)
        GridPoint3 start = new GridPoint3(
            (int) Math.floor(hitPointWorld.x),
            (int) Math.floor(hitPointWorld.y),
            (int) Math.floor(hitPointWorld.z)
        );

        // 2. FloodFillSelector floods from that block
        filler.runFloodFill(start);
        
        // 3. Check for fill cap
        if (filler.isCapped()) {
            // HUD will show warning based on filler.isCapped()
            // We return null to indicate failure
            return null;
        }

        VoxelSelection selection = filler.getSelection();
        if (selection == null || selection.isEmpty()) return null;

        // 4. PhysicsBodyFactory.create(selection, world)
        // This removes voxels from WorldGrid and builds PhysicsBody
        PhysicsBody body = factory.create(selection, world);
        
        // 5. Clear selection after assembly
        filler.clear();

        return body;
    }
}
