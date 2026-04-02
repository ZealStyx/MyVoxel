package com.zeal.voxel.world;

import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.block.BlockRegistry;
import com.zeal.voxel.block.BlockTag;
import com.zeal.voxel.physics.BulletWorld;
import com.zeal.voxel.physics.PhysicsConstants;
import com.zeal.voxel.physics.StaticChunkBody;
import com.zeal.voxel.render.culling.OcclusionGraph;
import com.zeal.voxel.util.Constants;
import com.zeal.voxel.util.TerrainConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChunkStreamer {
    private static final int MAX_CHUNK_LOADS_PER_FRAME = 8;
    private static final int STARTUP_CHUNK_LOAD_BURST = 24;
    private static final int MAX_CHUNK_UNLOADS_PER_FRAME = 4;
    private static final int VERTICAL_LOAD_RADIUS = 1;

    private final Map<ChunkPosition, Chunk> loadedChunks = new HashMap<>();
    private final Map<ChunkPosition, StaticChunkBody> staticChunkBodies = new HashMap<>();
    private final TerrainGenerator terrainGenerator;
    private final WorldGrid worldGrid;
    private final BulletWorld bulletWorld;
    private final OcclusionGraph occlusionGraph;
    private final BlockRegistry blockRegistry;
    // OPTIMIZED: Reuse frame-temporary containers and offsets to reduce GC churn.
    private final List<ChunkPosition> missing = new ArrayList<>();
    private final List<ChunkPosition> toRemove = new ArrayList<>();
    private final Vector3 tmpChunkWorldOffset = new Vector3();

    public ChunkStreamer(TerrainGenerator terrainGenerator, WorldGrid worldGrid, BulletWorld bulletWorld,
                         OcclusionGraph occlusionGraph, BlockRegistry blockRegistry) {
        this.terrainGenerator = terrainGenerator;
        this.worldGrid = worldGrid;
        this.bulletWorld = bulletWorld;
        this.occlusionGraph = occlusionGraph;
        this.blockRegistry = blockRegistry;
    }

    public void initialize() {
        // Reserved for startup ordering consistency.
    }

    public void update(Vector3 playerWorldPos) {
        ChunkPosition playerChunk = com.zeal.voxel.physics.CoordinateUtil.worldToChunk(playerWorldPos);
        // OPTIMIZED: Reuse missing list instead of allocating every update.
        missing.clear();
        for (int cx = playerChunk.x - TerrainConstants.RENDER_DIST; cx <= playerChunk.x + TerrainConstants.RENDER_DIST; cx++) {
            for (int cy = playerChunk.y - VERTICAL_LOAD_RADIUS; cy <= playerChunk.y + VERTICAL_LOAD_RADIUS; cy++) {
                for (int cz = playerChunk.z - TerrainConstants.RENDER_DIST; cz <= playerChunk.z + TerrainConstants.RENDER_DIST; cz++) {
                    ChunkPosition cp = new ChunkPosition(cx, cy, cz);
                    if (!loadedChunks.containsKey(cp)) {
                        missing.add(cp);
                    }
                }
            }
        }

        missing.sort((a, b) -> Integer.compare(a.chebyshevDist(playerChunk), b.chebyshevDist(playerChunk)));

        int loadBudget = loadedChunks.size() < 64 ? STARTUP_CHUNK_LOAD_BURST : MAX_CHUNK_LOADS_PER_FRAME;
        int loads = Math.min(loadBudget, missing.size());
        for (int i = 0; i < loads; i++) {
            ChunkPosition cp = missing.get(i);
            Chunk chunk = new Chunk();
            terrainGenerator.populate(chunk, cp);
            chunk.initializeSections(cp);
            loadedChunks.put(cp, chunk);
            markOcclusionDirtyAround(cp);

            // OPTIMIZED: Reuse chunk world offset vector while creating static chunk physics.
            tmpChunkWorldOffset.set(cp.x * Constants.CHUNK_SIZE, cp.y * Constants.CHUNK_SIZE, cp.z * Constants.CHUNK_SIZE);
            StaticChunkBody scb = StaticChunkBody.build(chunk, worldGrid, cp.x, cp.y, cp.z, tmpChunkWorldOffset);
            if (scb != null) {
                bulletWorld.addRigidBody(scb.rigidBody, PhysicsConstants.GROUP_WORLD, PhysicsConstants.GROUP_ALL);
                bulletWorld.registerObject(scb.rigidBody, scb);
                staticChunkBodies.put(cp, scb);
            }

            if (occlusionGraph != null) {
                occlusionGraph.rebuildAsync(chunk, cp);
            }
        }

        // OPTIMIZED: Reuse removal list instead of allocating every update.
        toRemove.clear();
        for (ChunkPosition cp : loadedChunks.keySet()) {
            if (cp.chebyshevDist(playerChunk) > TerrainConstants.RENDER_DIST + 2) {
                toRemove.add(cp);
            }
        }

        int unloadedThisFrame = 0;
        for (ChunkPosition cp : toRemove) {
            if (unloadedThisFrame >= MAX_CHUNK_UNLOADS_PER_FRAME) {
                break;
            }

            Chunk removed = loadedChunks.remove(cp);
            // OPTIMIZED: Remove from shared chunk map to keep visibility queries accurate.
            // worldGrid.getChunks().remove(new GridPoint3(cp.x, cp.y, cp.z));

            StaticChunkBody scb = staticChunkBodies.remove(cp);
            if (scb != null) {
                bulletWorld.removeRigidBody(scb.rigidBody);
                scb.dispose();
            }

            if (occlusionGraph != null) {
                occlusionGraph.invalidate(cp);
            }

            if (removed != null) {
                removed.setDirty(true);
            }
            markOcclusionDirtyAround(cp);

            unloadedThisFrame++;
        }

        recomputeDirtyOcclusion();
    }

    public void updateChunkPhysics(ChunkPosition cp, Chunk chunk) {
        chunk.initializeSections(cp);

        StaticChunkBody oldScb = staticChunkBodies.remove(cp);
        if (oldScb != null) {
            bulletWorld.removeRigidBody(oldScb.rigidBody);
            oldScb.dispose();
        }

        // OPTIMIZED: Reuse chunk world offset vector for chunk physics rebuilds.
        tmpChunkWorldOffset.set(cp.x * Constants.CHUNK_SIZE, cp.y * Constants.CHUNK_SIZE, cp.z * Constants.CHUNK_SIZE);
        StaticChunkBody scb = StaticChunkBody.build(chunk, worldGrid, cp.x, cp.y, cp.z, tmpChunkWorldOffset);
        if (scb != null) {
            bulletWorld.addRigidBody(scb.rigidBody, PhysicsConstants.GROUP_WORLD, PhysicsConstants.GROUP_ALL);
            bulletWorld.registerObject(scb.rigidBody, scb);
            staticChunkBodies.put(cp, scb);
        }

        if (occlusionGraph != null) {
            occlusionGraph.invalidate(cp);
            occlusionGraph.rebuildAsync(chunk, cp);
        }

        markOcclusionDirtyAround(cp);
    }

    public Map<ChunkPosition, Chunk> getLoadedChunks() {
        return loadedChunks;
    }

    public void dispose() {
        // OPTIMIZED: Deterministically release all static chunk physics bodies on shutdown.
        for (StaticChunkBody scb : staticChunkBodies.values()) {
            bulletWorld.removeRigidBody(scb.rigidBody);
            scb.dispose();
        }
        staticChunkBodies.clear();
        loadedChunks.clear();
    }

    private void recomputeDirtyOcclusion() {
        // OPTIMIZED: occlusion culling — recompute only when chunks/neighbors are marked dirty.
        for (Map.Entry<ChunkPosition, Chunk> entry : loadedChunks.entrySet()) {
            ChunkPosition cp = entry.getKey();
            Chunk chunk = entry.getValue();
            if (!chunk.isOcclusionDirty()) {
                continue;
            }
            chunk.setOccluded(isChunkFullyEnclosed(cp, chunk));
        }
    }

    private boolean isChunkFullyEnclosed(ChunkPosition cp, Chunk chunk) {
        ChunkPosition east = new ChunkPosition(cp.x + 1, cp.y, cp.z);
        ChunkPosition west = new ChunkPosition(cp.x - 1, cp.y, cp.z);
        ChunkPosition up = new ChunkPosition(cp.x, cp.y + 1, cp.z);
        ChunkPosition down = new ChunkPosition(cp.x, cp.y - 1, cp.z);
        ChunkPosition south = new ChunkPosition(cp.x, cp.y, cp.z + 1);
        ChunkPosition north = new ChunkPosition(cp.x, cp.y, cp.z - 1);

        Chunk eastChunk = loadedChunks.get(east);
        Chunk westChunk = loadedChunks.get(west);
        Chunk upChunk = loadedChunks.get(up);
        Chunk downChunk = loadedChunks.get(down);
        Chunk southChunk = loadedChunks.get(south);
        Chunk northChunk = loadedChunks.get(north);

        if (eastChunk == null || westChunk == null || upChunk == null
                || downChunk == null || southChunk == null || northChunk == null) {
            return false;
        }

        return isSharedFaceSolid(chunk, eastChunk, 1, 0, 0)
                && isSharedFaceSolid(chunk, westChunk, -1, 0, 0)
                && isSharedFaceSolid(chunk, upChunk, 0, 1, 0)
                && isSharedFaceSolid(chunk, downChunk, 0, -1, 0)
                && isSharedFaceSolid(chunk, southChunk, 0, 0, 1)
                && isSharedFaceSolid(chunk, northChunk, 0, 0, -1);
    }

    private boolean isSharedFaceSolid(Chunk self, Chunk neighbor, int dx, int dy, int dz) {
        int max = Constants.CHUNK_SIZE - 1;

        if (dx != 0) {
            int sx = dx > 0 ? max : 0;
            int nx = dx > 0 ? 0 : max;
            for (int y = 0; y < Constants.CHUNK_SIZE; y++) {
                for (int z = 0; z < Constants.CHUNK_SIZE; z++) {
                    if (!isSolidForOcclusion(self.getBlock(sx, y, z))
                            || !isSolidForOcclusion(neighbor.getBlock(nx, y, z))) {
                        return false;
                    }
                }
            }
            return true;
        }

        if (dy != 0) {
            int sy = dy > 0 ? max : 0;
            int ny = dy > 0 ? 0 : max;
            for (int x = 0; x < Constants.CHUNK_SIZE; x++) {
                for (int z = 0; z < Constants.CHUNK_SIZE; z++) {
                    if (!isSolidForOcclusion(self.getBlock(x, sy, z))
                            || !isSolidForOcclusion(neighbor.getBlock(x, ny, z))) {
                        return false;
                    }
                }
            }
            return true;
        }

        int sz = dz > 0 ? max : 0;
        int nz = dz > 0 ? 0 : max;
        for (int x = 0; x < Constants.CHUNK_SIZE; x++) {
            for (int y = 0; y < Constants.CHUNK_SIZE; y++) {
                if (!isSolidForOcclusion(self.getBlock(x, y, sz))
                        || !isSolidForOcclusion(neighbor.getBlock(x, y, nz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isSolidForOcclusion(int blockId) {
        if (blockId == 0) {
            return false;
        }
        if (blockRegistry == null) {
            return true;
        }
        return blockRegistry.hasTag(blockId, BlockTag.SOLID)
                && !blockRegistry.hasTag(blockId, BlockTag.TRANSPARENT)
                && !blockRegistry.hasTag(blockId, BlockTag.ALPHA_BLEND)
                && !blockRegistry.hasTag(blockId, BlockTag.ALPHA_CUTOUT);
    }

    private void markOcclusionDirtyAround(ChunkPosition cp) {
        markOcclusionDirty(cp.x, cp.y, cp.z);
        markOcclusionDirty(cp.x + 1, cp.y, cp.z);
        markOcclusionDirty(cp.x - 1, cp.y, cp.z);
        markOcclusionDirty(cp.x, cp.y + 1, cp.z);
        markOcclusionDirty(cp.x, cp.y - 1, cp.z);
        markOcclusionDirty(cp.x, cp.y, cp.z + 1);
        markOcclusionDirty(cp.x, cp.y, cp.z - 1);
    }

    private void markOcclusionDirty(int x, int y, int z) {
        Chunk neighbor = loadedChunks.get(new ChunkPosition(x, y, z));
        if (neighbor != null) {
            neighbor.markOcclusionDirty();
        }
    }
}
