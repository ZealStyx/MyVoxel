package com.zeal.voxel.physics;

import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.block.BlockBehaviour;
import com.zeal.voxel.block.BlockRegistry;
import com.zeal.voxel.player.VoxelSelection;
import com.zeal.voxel.render.particle.BlockBreakEmitter;
import com.zeal.voxel.world.WorldGrid;

import java.util.HashMap;
import java.util.Map;

/** Converts selections between the static world grid and dynamic physics objects. */
public class PhysicsBodyFactory {
    
    private final PhysicsBodyManager physicsBodyManager;
    private final Map<Integer, BlockBehaviour> behaviours;
    private final BlockBreakEmitter emitter;

    public PhysicsBodyFactory(PhysicsBodyManager manager, Map<Integer, BlockBehaviour> behaviours, BlockBreakEmitter emitter) {
        this.physicsBodyManager = manager;
        this.behaviours = behaviours;
        this.emitter = emitter;
    }

    public BlockBreakEmitter getBlockBreakEmitter() {
        return emitter;
    }

    /** Extracts blocks from the world and spawns a new PhysicsBody at its center of mass. */
    public PhysicsBody create(VoxelSelection selection, WorldGrid world) {
        if (selection.isEmpty()) return null;

        // 1. Calculate Global Center of Mass (GCOM)
        Vector3 globalCoM = new Vector3();
        float totalMass = 0f;
        BlockRegistry registry = BlockRegistry.getActive();
        for (Map.Entry<Vector3, Integer> entry : selection.getSelectedBlocks().entrySet()) {
            float mass = registry != null ? registry.getMass(entry.getValue()) : com.zeal.voxel.block.BlockType.fromId(entry.getValue()).getMass();
            // Core Physics Fix: Must use center of the voxels ( +0.5 ) not just the integer coordinates
            globalCoM.add((entry.getKey().x + 0.5f) * mass, (entry.getKey().y + 0.5f) * mass, (entry.getKey().z + 0.5f) * mass);
            totalMass += mass;
        }
        if (totalMass > 0f) {
            globalCoM.scl(1f / totalMass);
        }

        // 2. Convert world coords to local coords relative to selection min (integers for storage)
        Vector3 origin = selection.getMinCorner();
        Map<Vector3, Integer> localVoxels = new HashMap<>();
        for (Map.Entry<Vector3, Integer> entry : selection.getSelectedBlocks().entrySet()) {
             Vector3 worldPos = entry.getKey();
             int type = entry.getValue();

             Vector3 localPos = new Vector3(worldPos).sub(origin);
             localVoxels.put(localPos, type);

             // 3. Remove from world and emit particles
             world.setBlock((int)worldPos.x, (int)worldPos.y, (int)worldPos.z, 0);
             emitter.emit(worldPos, type);
        }

        // 4. Create body centered at Global CoM
        PhysicsBodyImpl body = new PhysicsBodyImpl(globalCoM, localVoxels, behaviours);
        
        // 5. Add to manager
        physicsBodyManager.addBody(body);
        return body;
    }

    /** Freezes a physics body and reattaches its blocks into the static world grid. */
    public void mergeBodyIntoWorld(PhysicsBody body, WorldGrid world) {
        // centerOfMassOffset in body is relative to the internal 'origin'
        // But since we built the body at GlobalCoM, CoordinateUtil.localToWorld handles it.
        for (Map.Entry<Vector3, Integer> entry : body.getVoxels().entrySet()) {
             Vector3 localPos = entry.getKey(); // relative to Selection Min
             int type = entry.getValue();

             // Convert local to world based on the body's current transform
             Vector3 worldPos = CoordinateUtil.localToWorld(localPos, body);
             
             // Snap to integer grid
             int gx = Math.round(worldPos.x);
             int gy = Math.round(worldPos.y);
             int gz = Math.round(worldPos.z);
             
             world.setBlock(gx, gy, gz, type);
        }

        physicsBodyManager.destroyBody(body);
    }
}
