package com.zeal.voxel.world;

import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.physics.BulletWorld;
import com.zeal.voxel.physics.PhysicsConstants;
import com.zeal.voxel.physics.StaticChunkBody;
import com.zeal.voxel.render.culling.OcclusionGraph;
import com.zeal.voxel.util.Constants;
import com.zeal.voxel.util.TerrainConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages streaming of BlockColumns around the player.
 * Columns are 2D (columnX, columnZ) and span the full world height.
 */
public class ColumnStreamer {
    private static final int MAX_COLUMN_LOADS_PER_FRAME = 8;
    private static final int STARTUP_COLUMN_LOAD_BURST = 24;
    private static final int MAX_COLUMN_UNLOADS_PER_FRAME = 4;
    private static final int PHYSICS_SLICE_COUNT = Constants.WORLD_HEIGHT / Constants.CHUNK_SIZE;

    private static final int MAX_COLUMN_CACHE_SIZE = 4096;

    private final Map<Long, BlockColumn> loadedColumns = new HashMap<>();
    private final Map<Long, BlockColumn> columnCache = Collections.synchronizedMap(
        new LinkedHashMap<Long, BlockColumn>(MAX_COLUMN_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, BlockColumn> eldest) {
                return size() > MAX_COLUMN_CACHE_SIZE;
            }
        }
    );
    private final Map<Long, BlockColumn> readyColumns = new ConcurrentHashMap<>();
    private final Set<Long> loadingColumns = ConcurrentHashMap.newKeySet();
    private final Map<Long, StaticChunkBody> staticColumnBodies = new HashMap<>();
    private final ColumnTerrainGenerator terrainGenerator;
    private final WorldGrid worldGrid;
    private final BulletWorld bulletWorld;
    private final OcclusionGraph occlusionGraph;
    private final ExecutorService columnLoader = Executors.newFixedThreadPool(2);
    private final List<ColumnPosition> missing = new ArrayList<>();
    private final List<ColumnPosition> toRemove = new ArrayList<>();

    public ColumnStreamer(ColumnTerrainGenerator terrainGenerator, WorldGrid worldGrid, 
                         BulletWorld bulletWorld, OcclusionGraph occlusionGraph) {
        this.terrainGenerator = terrainGenerator;
        this.worldGrid = worldGrid;
        this.bulletWorld = bulletWorld;
        this.occlusionGraph = occlusionGraph;
    }

    public void initialize() {
        // Reserved for startup ordering consistency.
    }

    private void applyReadyColumns() {
        if (readyColumns.isEmpty()) {
            return;
        }

        for (Map.Entry<Long, BlockColumn> entry : readyColumns.entrySet()) {
            long key = entry.getKey();
            if (loadedColumns.containsKey(key)) {
                readyColumns.remove(key);
                continue;
            }

            BlockColumn column = entry.getValue();
            ColumnPosition cp = new ColumnPosition(column.columnX, column.columnZ);
            worldGrid.putColumn(cp.x, cp.z, column);
            loadedColumns.put(key, column);
            buildColumnCollision(cp, column);
            readyColumns.remove(key);
        }
    }

    public void update(Vector3 playerWorldPos) {
        applyReadyColumns();

        // Convert player world position to column grid coordinates
        int playerColumnX = (int) Math.floor(playerWorldPos.x / Constants.COLUMN_SIZE);
        int playerColumnZ = (int) Math.floor(playerWorldPos.z / Constants.COLUMN_SIZE);
        
        // Gather missing columns within render distance
        missing.clear();
        for (int cx = playerColumnX - TerrainConstants.RENDER_DIST; 
             cx <= playerColumnX + TerrainConstants.RENDER_DIST; cx++) {
            for (int cz = playerColumnZ - TerrainConstants.RENDER_DIST; 
                 cz <= playerColumnZ + TerrainConstants.RENDER_DIST; cz++) {
                long key = ColumnPosition.key(cx, cz);
                if (!loadedColumns.containsKey(key)) {
                    missing.add(new ColumnPosition(cx, cz));
                }
            }
        }

        // Sort by distance to player
        missing.sort((a, b) -> {
            int dxA = Math.abs(a.x - playerColumnX);
            int dzA = Math.abs(a.z - playerColumnZ);
            int distA = Math.max(dxA, dzA);
            
            int dxB = Math.abs(b.x - playerColumnX);
            int dzB = Math.abs(b.z - playerColumnZ);
            int distB = Math.max(dxB, dzB);
            
            return Integer.compare(distA, distB);
        });

        // Load columns (async pipeline)
        int loadBudget = loadedColumns.size() < 64 ? STARTUP_COLUMN_LOAD_BURST : MAX_COLUMN_LOADS_PER_FRAME;
        int starts = 0;
        for (ColumnPosition cp : missing) {
            if (starts >= loadBudget) {
                break;
            }

            long key = ColumnPosition.key(cp.x, cp.z);
            if (loadedColumns.containsKey(key) || readyColumns.containsKey(key) || loadingColumns.contains(key)) {
                continue;
            }

            // Check cache first
            BlockColumn cached = columnCache.remove(key);
            if (cached != null) {
                loadedColumns.put(key, cached);
                worldGrid.putColumn(cp.x, cp.z, cached);
                buildColumnCollision(cp, cached);
                starts++;
                continue;
            }

            loadingColumns.add(key);
            columnLoader.submit(() -> {
                BlockColumn column = new BlockColumn(cp.x, cp.z);
                terrainGenerator.populate(column, cp.x, cp.z);
                readyColumns.put(key, column);
                loadingColumns.remove(key);
            });
            starts++;
        }

        // No direct insertion of loaded columns here; applyReadyColumns will process them.

        // Unload columns
        toRemove.clear();
        for (Map.Entry<Long, BlockColumn> entry : loadedColumns.entrySet()) {
            BlockColumn col = entry.getValue();
            ColumnPosition cp = new ColumnPosition(col.columnX, col.columnZ);
            
            int dx = Math.abs(cp.x - playerColumnX);
            int dz = Math.abs(cp.z - playerColumnZ);
            int dist = Math.max(dx, dz);
            
            if (dist > TerrainConstants.RENDER_DIST + 2) {
                toRemove.add(cp);
            }
        }

        int unloadedThisFrame = 0;
        for (ColumnPosition cp : toRemove) {
            if (unloadedThisFrame >= MAX_COLUMN_UNLOADS_PER_FRAME) {
                break;
            }

            long key = ColumnPosition.key(cp.x, cp.z);
            BlockColumn removed = loadedColumns.remove(key);            if (removed != null) {
                columnCache.put(key, removed);
            }            worldGrid.getColumns().remove(key);
            removeColumnCollision(cp);

            if (occlusionGraph != null) {
                // No-op: chunk-based occlusion is incompatible with full-height columns.
            }

            if (removed != null) {
                removed.setDirty(true);
            }

            unloadedThisFrame++;
        }
    }

    public Map<Long, BlockColumn> getLoadedColumns() {
        return loadedColumns;
    }

    public void dispose() {
        for (StaticChunkBody scb : staticColumnBodies.values()) {
            bulletWorld.removeRigidBody(scb.rigidBody);
            scb.dispose();
        }
        staticColumnBodies.clear();
        loadedColumns.clear();
        columnCache.clear();
        readyColumns.clear();
        loadingColumns.clear();
        columnLoader.shutdownNow();
    }

    private void buildColumnCollision(ColumnPosition cp, BlockColumn column) {
        for (int sliceIdx = 0; sliceIdx < PHYSICS_SLICE_COUNT; sliceIdx++) {
            int sliceY = sliceIdx * Constants.CHUNK_SIZE;
            Chunk slice = column.extractSlice(sliceY);
            if (isSliceEmpty(slice)) {
                continue;
            }

            Vector3 offset = new Vector3(
                cp.x * Constants.COLUMN_SIZE,
                sliceY,
                cp.z * Constants.COLUMN_SIZE
            );
            StaticChunkBody scb = StaticChunkBody.build(slice, worldGrid, cp.x, sliceIdx, cp.z, offset);
            if (scb == null) {
                continue;
            }

            bulletWorld.addRigidBody(scb.rigidBody, PhysicsConstants.GROUP_WORLD, PhysicsConstants.GROUP_ALL);
            bulletWorld.registerObject(scb.rigidBody, scb);
            staticColumnBodies.put(sliceKey(cp.x, sliceIdx, cp.z), scb);
        }
    }

    private void removeColumnCollision(ColumnPosition cp) {
        for (int sliceIdx = 0; sliceIdx < PHYSICS_SLICE_COUNT; sliceIdx++) {
            StaticChunkBody scb = staticColumnBodies.remove(sliceKey(cp.x, sliceIdx, cp.z));
            if (scb != null) {
                bulletWorld.removeRigidBody(scb.rigidBody);
                scb.dispose();
            }
        }
    }

    private boolean isSliceEmpty(Chunk chunk) {
        for (int x = 0; x < Constants.CHUNK_SIZE; x++) {
            for (int y = 0; y < Constants.CHUNK_SIZE; y++) {
                for (int z = 0; z < Constants.CHUNK_SIZE; z++) {
                    if (chunk.getBlock(x, y, z) != 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private long sliceKey(int cx, int sliceIdx, int cz) {
        return ((long) (cx & 0xFFFF) << 32)
            | ((long) (sliceIdx & 0xFF) << 24)
            | (cz & 0xFFFFL);
    }
}
