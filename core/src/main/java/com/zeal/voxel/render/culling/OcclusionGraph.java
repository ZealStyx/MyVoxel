package com.zeal.voxel.render.culling;

import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.block.BlockRegistry;
import com.zeal.voxel.block.BlockTag;
import com.zeal.voxel.physics.CoordinateUtil;
import com.zeal.voxel.util.Constants;
import com.zeal.voxel.world.ColumnPosition;
import com.zeal.voxel.world.BlockColumn;
import com.zeal.voxel.world.Chunk;
import com.zeal.voxel.world.ChunkPosition;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OcclusionGraph {
    private static final int NORTH = 0;
    private static final int SOUTH = 1;
    private static final int EAST = 2;
    private static final int WEST = 3;
    private static final int UP = 4;
    private static final int DOWN = 5;

    private static final int[] DIR_X = {0, 0, 1, -1};
    private static final int[] DIR_Z = {-1, 1, 0, 0};
    private static final int[] DIR_FACE = {NORTH, SOUTH, EAST, WEST};
    private static final int[] OPP_FACE = {SOUTH, NORTH, WEST, EAST, DOWN, UP};

    private static class ChunkGraphEntry {
        volatile boolean[][] graph;
        volatile boolean rebuilding;
        volatile boolean dirty;
    }

    private final Map<ChunkPosition, ChunkGraphEntry> graphs = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "occlusion-graph-worker");
        t.setDaemon(true);
        return t;
    });

    private final BlockRegistry blockRegistry;
    private volatile ChunkPosition viewerChunk;
    private volatile Set<ChunkPosition> reachableChunks = Collections.emptySet();
    private volatile boolean graphsDirty = true;

    public OcclusionGraph(BlockRegistry blockRegistry) {
        this.blockRegistry = blockRegistry;
    }

    public void beginFrame(Vector3 viewerWorldPos, Map<ChunkPosition, Chunk> loadedChunks) {
        if (loadedChunks.isEmpty()) {
            reachableChunks = Collections.emptySet();
            return;
        }

        ChunkPosition currentViewer = CoordinateUtil.worldToChunk(viewerWorldPos);
        if (!currentViewer.equals(viewerChunk)
            || reachableChunks.isEmpty()
            || reachableChunks.size() != loadedChunks.size()) {
            viewerChunk = currentViewer;
            recomputeReachable(loadedChunks);
        }

        for (Map.Entry<ChunkPosition, Chunk> entry : loadedChunks.entrySet()) {
            ensureGraph(entry.getKey(), entry.getValue());
        }
    }

    public void beginFrameColumns(Vector3 viewerWorldPos, Map<Long, BlockColumn> loadedColumns) {
        if (loadedColumns.isEmpty()) {
            reachableChunks = Collections.emptySet();
            return;
        }

        ChunkPosition currentViewer = new ChunkPosition(
            (int) Math.floor(viewerWorldPos.x / Constants.COLUMN_SIZE),
            (int) Math.floor(viewerWorldPos.z / Constants.COLUMN_SIZE));

        boolean viewerMoved = !currentViewer.equals(viewerChunk);
        if (viewerMoved || graphsDirty || reachableChunks.size() != loadedColumns.size()) {
            viewerChunk = currentViewer;
            graphsDirty = false;
            recomputeReachableColumns(loadedColumns);
        }

        // Trigger graph rebuilds for columns that need it
        for (Map.Entry<Long, BlockColumn> entry : loadedColumns.entrySet()) {
            ChunkPosition cp = columnKeyToPosition(entry.getKey());
            if (cp != null) {
                rebuildColumnGraphAsync(entry.getValue(), cp);
            }
        }
    }

    public void beginFrame(Vector3 viewerWorldPos, Map<Long, BlockColumn> loadedColumns, int columnSize) {
        beginFrameColumns(viewerWorldPos, loadedColumns);
    }

    private void rebuildColumnGraphAsync(BlockColumn column, ChunkPosition cp) {
        ChunkGraphEntry entry = graphs.computeIfAbsent(cp, key -> {
            ChunkGraphEntry created = new ChunkGraphEntry();
            created.graph = fullReachability();
            created.rebuilding = false;
            created.dirty = true;
            return created;
        });

        if (entry.graph == null) {
            entry.graph = fullReachability();
            entry.dirty = true;
        }

        if (entry.rebuilding || !entry.dirty) {
            return;
        }

        entry.rebuilding = true;
        worker.submit(() -> {
            try {
                boolean[][] graph = computeColumnGraph(column);
                entry.graph = graph;
                entry.dirty = false;
                graphsDirty = true;
            } finally {
                entry.rebuilding = false;
            }
        });
    }

    public void rebuildAsync(Chunk chunk, ChunkPosition cp) {
        ChunkGraphEntry entry = graphs.computeIfAbsent(cp, key -> {
            ChunkGraphEntry created = new ChunkGraphEntry();
            created.graph = fullReachability();
            created.rebuilding = false;
            created.dirty = true;
            return created;
        });

        if (entry.graph == null) {
            entry.graph = fullReachability();
            entry.dirty = true;
        }

        if (entry.rebuilding || !entry.dirty) {
            return;
        }

        entry.rebuilding = true;
        worker.submit(() -> {
            boolean[][] graph = computeGraph(chunk);
            entry.graph = graph;
            entry.dirty = false;
            entry.rebuilding = false;
        });
    }

    public void invalidate(ChunkPosition cp) {
        graphs.remove(cp);
    }

    public boolean isSectionReachable(SubChunkSection section) {
        Set<ChunkPosition> snapshot = reachableChunks;
        if (snapshot.isEmpty() || viewerChunk == null) {
            return true;
        }
        return snapshot.contains(section.chunkPosition);
    }

    /**
     * Returns true if a column key is currently outside the occlusion-reachable set.
     */
    public boolean isColumnOccluded(long columnKey) {
        Set<ChunkPosition> snapshot = reachableChunks;
        if (snapshot.isEmpty() || viewerChunk == null) {
            return false;
        }

        int x = (int) (columnKey >> 32);
        int z = (int) columnKey;
        return !snapshot.contains(new ChunkPosition(x, z));
    }

    private ChunkPosition columnKeyToPosition(long columnKey) {
        int x = (int) (columnKey >> 32);
        int z = (int) columnKey;
        return new ChunkPosition(x, z);
    }

    private void recomputeReachableColumns(Map<Long, BlockColumn> loadedColumns) {
        if (viewerChunk == null) {
            reachableChunks = Collections.emptySet();
            return;
        }

        Set<ChunkPosition> visited = ConcurrentHashMap.newKeySet();
        ArrayDeque<ChunkPosition> queue = new ArrayDeque<>();

        if (!loadedColumns.containsKey(ColumnPosition.key(viewerChunk.x, viewerChunk.z))) {
            reachableChunks = Collections.emptySet();
            return;
        }

        visited.add(viewerChunk);
        queue.add(viewerChunk);

        while (!queue.isEmpty()) {
            ChunkPosition current = queue.poll();

            for (int i = 0; i < DIR_X.length; i++) {
                ChunkPosition next = new ChunkPosition(current.x + DIR_X[i], current.z + DIR_Z[i]);
                long nextKey = ColumnPosition.key(next.x, next.z);
                if (!loadedColumns.containsKey(nextKey) || visited.contains(next)) {
                    continue;
                }

                int faceOut = DIR_FACE[i];
                int faceIn = OPP_FACE[faceOut];

                if (!canTraverseFaceHorizontal(current, faceOut)
                        || !canTraverseFaceHorizontal(next, faceIn)) {
                    continue;
                }

                visited.add(next);
                queue.add(next);
            }
        }

        reachableChunks = visited;
    }

    private boolean canTraverseFaceHorizontal(ChunkPosition cp, int toFace) {
        ChunkGraphEntry entry = graphs.get(cp);
        if (entry == null || entry.rebuilding || entry.graph == null) {
            return true;
        }
        boolean[][] graph = entry.graph;
        for (int from = 0; from < 4; from++) {
            if (graph[from][toFace]) {
                return true;
            }
        }
        return false;
    }

    private void ensureGraph(ChunkPosition cp, Chunk chunk) {
        ChunkGraphEntry entry = graphs.computeIfAbsent(cp, key -> {
            ChunkGraphEntry created = new ChunkGraphEntry();
            created.graph = fullReachability();
            created.rebuilding = false;
            created.dirty = true;
            return created;
        });
        if (entry.graph == null) {
            entry.graph = fullReachability();
            entry.dirty = true;
        }
        if (entry.dirty && !entry.rebuilding) {
            rebuildAsync(chunk, cp);
        }
    }

    private boolean[][] computeColumnGraph(BlockColumn column) {
        boolean[][] reach = fullReachability(); // start with everything connected

        // For a full column, we only really care about horizontal traversal between columns.
        // Vertical traversal inside one column is almost always possible unless the whole column is solid.

        // Simple heuristic for now: if the column has any air path from top to bottom, allow UP/DOWN
        boolean hasVerticalPath = hasVerticalAirPath(column);
        if (!hasVerticalPath) {
            reach[UP][DOWN] = reach[DOWN][UP] = false;
        }

        return reach;
    }

    private boolean hasVerticalAirPath(BlockColumn column) {
        for (int lx = 0; lx < Constants.COLUMN_SIZE; lx++) {
            for (int lz = 0; lz < Constants.COLUMN_SIZE; lz++) {
                boolean connected = true;
                for (int y = 0; y < Constants.WORLD_HEIGHT - 1; y++) {
                    if (!isAirLike(column.getBlock(lx, y, lz)) && 
                        !isAirLike(column.getBlock(lx, y + 1, lz))) {
                        connected = false;
                        break;
                    }
                }
                if (connected) return true;
            }
        }
        return false;
    }

    private void recomputeReachable(Map<ChunkPosition, Chunk> loadedChunks) {
        if (viewerChunk == null) {
            reachableChunks = Collections.emptySet();
            return;
        }

        Set<ChunkPosition> visited = ConcurrentHashMap.newKeySet();
        ArrayDeque<ChunkPosition> queue = new ArrayDeque<>();

        if (!loadedChunks.containsKey(viewerChunk)) {
            reachableChunks = Collections.emptySet();
            return;
        }

        visited.add(viewerChunk);
        queue.add(viewerChunk);

        while (!queue.isEmpty()) {
            ChunkPosition current = queue.poll();

            for (int i = 0; i < DIR_X.length; i++) {
                ChunkPosition next = new ChunkPosition(current.x + DIR_X[i], current.z + DIR_Z[i]);
                if (!loadedChunks.containsKey(next) || visited.contains(next)) {
                    continue;
                }

                int faceOut = DIR_FACE[i];
                int faceIn = OPP_FACE[faceOut];

                if (!canTraverseFace(current, faceOut) || !canTraverseFace(next, faceIn)) {
                    continue;
                }

                visited.add(next);
                queue.add(next);
            }
        }

        reachableChunks = visited;
    }

    private boolean canTraverseFace(ChunkPosition cp, int toFace) {
        ChunkGraphEntry entry = graphs.get(cp);
        if (entry == null || entry.rebuilding || entry.graph == null) {
            return true;
        }
        boolean[][] graph = entry.graph;
        for (int from = 0; from < 6; from++) {
            if (graph[from][toFace]) {
                return true;
            }
        }
        return false;
    }

    private boolean[][] computeGraph(Chunk chunk) {
        boolean[][] reach = new boolean[6][6];
        for (int a = 0; a < 6; a++) {
            for (int b = 0; b < 6; b++) {
                if (a == b) {
                    reach[a][b] = true;
                } else {
                    reach[a][b] = floodFillFaces(chunk, a, b);
                }
            }
        }
        return reach;
    }

    private boolean floodFillFaces(Chunk chunk, int faceA, int faceB) {
        boolean[] visited = new boolean[Constants.CHUNK_SIZE_CUBED];
        ArrayDeque<int[]> queue = new ArrayDeque<>();

        enqueueFaceAirVoxels(chunk, faceA, visited, queue);

        while (!queue.isEmpty()) {
            int[] voxel = queue.poll();
            int x = voxel[0];
            int y = voxel[1];
            int z = voxel[2];

            if (isOnFace(x, y, z, faceB)) {
                return true;
            }

            expandNeighbors(chunk, x, y, z, visited, queue);
        }

        return false;
    }

    private void enqueueFaceAirVoxels(Chunk chunk, int face, boolean[] visited, ArrayDeque<int[]> queue) {
        for (int x = 0; x < Constants.CHUNK_SIZE; x++) {
            for (int y = 0; y < Constants.CHUNK_SIZE; y++) {
                for (int z = 0; z < Constants.CHUNK_SIZE; z++) {
                    if (!isOnFace(x, y, z, face)) {
                        continue;
                    }
                    if (!isAirLike(chunk.getBlock(x, y, z))) {
                        continue;
                    }
                    int idx = index(x, y, z);
                    if (!visited[idx]) {
                        visited[idx] = true;
                        queue.add(new int[]{x, y, z});
                    }
                }
            }
        }
    }

    private void expandNeighbors(Chunk chunk, int x, int y, int z, boolean[] visited, ArrayDeque<int[]> queue) {
        visitNeighbor(chunk, x + 1, y, z, visited, queue);
        visitNeighbor(chunk, x - 1, y, z, visited, queue);
        visitNeighbor(chunk, x, y + 1, z, visited, queue);
        visitNeighbor(chunk, x, y - 1, z, visited, queue);
        visitNeighbor(chunk, x, y, z + 1, visited, queue);
        visitNeighbor(chunk, x, y, z - 1, visited, queue);
    }

    private void visitNeighbor(Chunk chunk, int x, int y, int z, boolean[] visited, ArrayDeque<int[]> queue) {
        if (x < 0 || y < 0 || z < 0 || x >= Constants.CHUNK_SIZE || y >= Constants.CHUNK_SIZE || z >= Constants.CHUNK_SIZE) {
            return;
        }
        if (!isAirLike(chunk.getBlock(x, y, z))) {
            return;
        }
        int idx = index(x, y, z);
        if (!visited[idx]) {
            visited[idx] = true;
            queue.add(new int[]{x, y, z});
        }
    }

    private boolean isAirLike(int blockId) {
        if (blockId == 0) {
            return true;
        }
        if (blockRegistry == null) {
            return false;
        }
        boolean solid = blockRegistry.hasTag(blockId, BlockTag.SOLID);
        boolean transparent = blockRegistry.hasTag(blockId, BlockTag.TRANSPARENT);
        return !solid || transparent;
    }

    private boolean isOnFace(int x, int y, int z, int face) {
        int max = Constants.CHUNK_SIZE - 1;
        return switch (face) {
            case NORTH -> z == 0;
            case SOUTH -> z == max;
            case EAST -> x == max;
            case WEST -> x == 0;
            case UP -> y == max;
            case DOWN -> y == 0;
            default -> false;
        };
    }

    private int index(int x, int y, int z) {
        return x + y * Constants.CHUNK_SIZE + z * Constants.CHUNK_SIZE_SQUARED;
    }

    private boolean[][] fullReachability() {
        boolean[][] graph = new boolean[6][6];
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                graph[i][j] = true;
            }
        }
        return graph;
    }

    /**
     * Get count of reachable columns for debug/telemetry.
     */
    public int getReachableCount() {
        return reachableChunks.size();
    }

    public void dispose() {
        worker.shutdownNow();
    }
}
