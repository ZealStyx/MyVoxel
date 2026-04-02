package com.zeal.voxel.player;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.physics.BulletWorld;
import com.zeal.voxel.physics.PhysicsBody;
import com.zeal.voxel.physics.PhysicsBodyManager;
import com.zeal.voxel.render.particle.BlockBreakEmitter;
import com.zeal.voxel.world.ChunkPosition;
import com.zeal.voxel.world.WorldGrid;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LatchDisassembler {

    private final BlockBreakEmitter emitter;

    public LatchDisassembler(BlockBreakEmitter emitter) {
        this.emitter = emitter;
    }

    public void disassemble(PhysicsBody body,
                            WorldGrid world,
                            PhysicsBodyManager mgr,
                            BulletWorld bw,
                            CapsuleController playerController) {
        
        Map<Vector3, Integer> voxels = body.getVoxels();
        Set<ChunkPosition> affectedChunks = new HashSet<>();

        // Get player AABB for intersection check
        Vector3 playerPos = playerController.getFeetPosition();
        float pRadius = PlayerConstants.CAPSULE_RADIUS + 0.1f; // padding
        float pHeight = PlayerConstants.CAPSULE_HEIGHT + 0.1f;

        for (Map.Entry<Vector3, Integer> entry : voxels.entrySet()) {
            Vector3 localPos = entry.getKey();
            int blockId = entry.getValue();

            // Local center of the voxel
            Vector3 localCenter = new Vector3(localPos.x + 0.5f, localPos.y + 0.5f, localPos.z + 0.5f);
            
            // Transform to world space using body transform
            Vector3 worldCenter = new Vector3(localCenter).mul(body.getTransform());

            // Round to nearest grid cell
            int wx = MathUtils.floor(worldCenter.x);
            int wy = MathUtils.floor(worldCenter.y);
            int wz = MathUtils.floor(worldCenter.z);

            // 1. Collision with player check
            if (wx >= playerPos.x - pRadius && wx <= playerPos.x + pRadius &&
                wz >= playerPos.z - pRadius && wz <= playerPos.z + pRadius &&
                wy >= playerPos.y && wy <= playerPos.y + pHeight) {
                
                // Skip this voxel to avoid trapping the player
                emitter.emit(worldCenter, blockId);
                continue;
            }

            // 2. Only place if target cell is currently air
            if (world.getBlock(wx, wy, wz) == 0) {
                world.setBlock(wx, wy, wz, blockId);
                affectedChunks.add(new ChunkPosition(wx >> 4, wz >> 4));
            } else {
                // Collision with existing world block → burst
                emitter.emit(worldCenter, blockId);
            }
        }

        // Clean up
        mgr.destroyBody(body);
        
        // Note: Chunk rebuilding is handled by GameScreen observing worldGrid.getModifiedChunks()
    }
}
