package com.zeal.voxel.world;

import com.badlogic.gdx.math.collision.BoundingBox;
import com.zeal.voxel.block.BlockRegistry;
import com.zeal.voxel.block.BlockTag;
import com.zeal.voxel.block.TextureRegionResolver;
import com.zeal.voxel.render.ao.FaceDirection;
import com.zeal.voxel.render.TextureGenerator;
import com.zeal.voxel.util.Constants;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.*;

/**
 * Generates meshes from a BlockColumn using flood-fill to identify connected components.
 * Produces one ground-connected mesh and additional floating cluster meshes.
 * Handles separate opaque and transparent face rendering.
 */
public class ColumnMesher {
    private static final float WHITE = 1f;
    private static final int MESH_BATCH_HEIGHT = 32;
    
    public static ColumnMeshResult meshColumn(
            BlockColumn column,
            WorldGrid world,
            BlockRegistry blockRegistry,
            TextureRegionResolver textureResolver) {
        
        if (column.isEmpty()) {
            return new ColumnMeshResult(new ArrayList<>(), new ArrayList<>());
        }

        // Step 1: Flood fill to find connected components
        Map<Integer, List<Integer>> componentToBlocks = identifyConnectedComponents(column, world, blockRegistry);
        
        // Step 2: Classify components (ground-connected vs floating)
        Set<Integer> groundConnectedComponents = new HashSet<>();
        classifyComponents(column, world, blockRegistry, componentToBlocks, groundConnectedComponents);
        
        // Step 3: Build meshes for each component
        List<ColumnMesh> opaqueMeshes = new ArrayList<>();
        List<ColumnMesh> transparentMeshes = new ArrayList<>();
        
        for (Map.Entry<Integer, List<Integer>> entry : componentToBlocks.entrySet()) {
            int componentId = entry.getKey();
            List<Integer> blockIndices = entry.getValue();
            
            if (blockIndices.isEmpty()) continue;
            
            boolean isFloating = !groundConnectedComponents.contains(componentId);
            
            Map<Integer, List<Integer>> bySlice = partitionByVerticalSlice(blockIndices);
            for (List<Integer> sliceBlocks : bySlice.values()) {
                ColumnMesh mesh = buildMeshForComponent(
                    column, world, blockRegistry, textureResolver,
                    sliceBlocks, isFloating
                );

                if (mesh != null) {
                    if (mesh.opaqueGeometry != null && mesh.opaqueGeometry.indices.length > 0) {
                        opaqueMeshes.add(mesh);
                    }
                    if (mesh.transparentGeometry != null && mesh.transparentGeometry.indices.length > 0) {
                        transparentMeshes.add(mesh);
                    }
                }
            }
        }
        
        return new ColumnMeshResult(opaqueMeshes, transparentMeshes);
    }
    
    /**
     * Use flood-fill to identify connected components of solid blocks.
     * Returns a map from componentId to list of voxel indices in that component.
     */
    private static Map<Integer, List<Integer>> identifyConnectedComponents(
            BlockColumn column,
            WorldGrid world,
            BlockRegistry blockRegistry) {
        
        BitSet visited = new BitSet(Constants.COLUMN_SIZE * Constants.COLUMN_SIZE * Constants.WORLD_HEIGHT);
        Map<Integer, List<Integer>> componentMap = new HashMap<>();
        int nextComponentId = 0;
        
        // Scan all blocks in column
        for (int lx = 0; lx < Constants.COLUMN_SIZE; lx++) {
            for (int lz = 0; lz < Constants.COLUMN_SIZE; lz++) {
                for (int y = 0; y < Constants.WORLD_HEIGHT; y++) {
                    int idx = getVoxelIndex(lx, y, lz);
                    
                    if (visited.get(idx)) continue;
                    
                    int blockId = column.getBlock(lx, y, lz);
                    if (blockId == 0) continue;  // Skip air
                    
                    if (!isSolid(blockRegistry, blockId)) continue;  // Skip non-solid blocks
                    
                    // Start new flood fill from this block
                    List<Integer> component = new ArrayList<>();
                    floodFill(column, world, blockRegistry, lx, y, lz, visited, component);
                    
                    if (!component.isEmpty()) {
                        componentMap.put(nextComponentId++, component);
                    }
                }
            }
        }
        
        return componentMap;
    }
    
    /**
     * Flood fill from starting position, marking all reachable solid blocks as part of same component.
     * Uses 6-connectivity (face-adjacent neighbors only).
     */
    private static void floodFill(
            BlockColumn column,
            WorldGrid world,
            BlockRegistry blockRegistry,
            int startLocalX,
            int startY,
            int startLocalZ,
            BitSet visited,
            List<Integer> component) {
        
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{startLocalX, startY, startLocalZ});
        
        int idx = getVoxelIndex(startLocalX, startY, startLocalZ);
        visited.set(idx);
        component.add(idx);
        
        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int lx = pos[0];
            int y = pos[1];
            int lz = pos[2];
            
            // Check all 6 face-adjacent neighbors
            int[][] neighbors = {
                {lx + 1, y, lz}, {lx - 1, y, lz},  // X
                {lx, y + 1, lz}, {lx, y - 1, lz},  // Y
                {lx, y, lz + 1}, {lx, y, lz - 1}   // Z
            };
            
            for (int[] npos : neighbors) {
                int nlx = npos[0];
                int ny = npos[1];
                int nlz = npos[2];
                
                // Check column bounds
                if (nlx < 0 || nlx >= Constants.COLUMN_SIZE ||
                    nlz < 0 || nlz >= Constants.COLUMN_SIZE ||
                    ny < 0 || ny >= Constants.WORLD_HEIGHT) {
                    continue;
                }
                
                int nidx = getVoxelIndex(nlx, ny, nlz);
                if (visited.get(nidx)) continue;
                
                int nblockId = column.getBlock(nlx, ny, nlz);
                if (nblockId == 0) continue;  // Skip air
                
                if (!isSolid(blockRegistry, nblockId)) continue;  // Skip non-solid
                
                visited.set(nidx);
                component.add(nidx);
                queue.add(npos);
            }
        }
    }
    
    /**
     * Classify which components are ground-connected.
     * A component is ground-connected if it can reach Y=0 or if it's adjacent to a
     * ground-connected component in a neighboring column.
     * 
     * For simplicity in this version, we'll mark components as ground-connected if any
     * block in them is within 2 blocks of Y=0.
     */
    private static void classifyComponents(
            BlockColumn column,
            WorldGrid world,
            BlockRegistry blockRegistry,
            Map<Integer, List<Integer>> componentMap,
            Set<Integer> groundConnected) {
        
        for (Map.Entry<Integer, List<Integer>> entry : componentMap.entrySet()) {
            int componentId = entry.getKey();
            List<Integer> blocks = entry.getValue();
            
            boolean isGroundConnected = false;
            
            // Check if any block in component is very close to Y=0
            for (int idx : blocks) {
                int[] pos = getVoxelPositionFromIndex(idx);
                int y = pos[1];
                
                if (y < 3) {
                    isGroundConnected = true;
                    break;
                }
            }
            
            // Also check if component has blocks adjacent to ground-connected blocks in neighboring columns
            if (!isGroundConnected) {
                for (int idx : blocks) {
                    int[] pos = getVoxelPositionFromIndex(idx);
                    int lx = pos[0];
                    int y = pos[1];
                    int lz = pos[2];
                    
                    int wx = column.worldX(lx);
                    int wz = column.worldZ(lz);
                    
                    // Check if any face-adjacent block in world coordinates is ground-close
                    int[][] worldNeighbors = {
                        {wx + 1, y, wz}, {wx - 1, y, wz},
                        {wx, y, wz + 1}, {wx, y, wz - 1}
                    };
                    
                    for (int[] wneighbor : worldNeighbors) {
                        int nblockId = world.getBlock(wneighbor[0], wneighbor[1], wneighbor[2]);
                        if (nblockId != 0 && isSolid(blockRegistry, nblockId) && wneighbor[1] < 3) {
                            isGroundConnected = true;
                            break;
                        }
                    }
                    
                    if (isGroundConnected) break;
                }
            }
            
            if (isGroundConnected) {
                groundConnected.add(componentId);
            }
        }
    }
    
    /**
     * Build mesh for a single connected component of blocks.
     */
    private static ColumnMesh buildMeshForComponent(
            BlockColumn column,
            WorldGrid world,
            BlockRegistry blockRegistry,
            TextureRegionResolver textureResolver,
            List<Integer> blockIndices,
            boolean isFloating) {

        List<Float> opaqueVertices = new ArrayList<>();
        List<Short> opaqueIndices = new ArrayList<>();
        List<Float> transparentVertices = new ArrayList<>();
        List<Short> transparentIndices = new ArrayList<>();
        
        BoundingBox bounds = new BoundingBox();
        boolean boundsInitialized = false;
        
        short opaqueVertexIndex = 0;
        short transparentVertexIndex = 0;
        
        // Emit faces for all blocks in component
        for (int idx : blockIndices) {
            int[] pos = getVoxelPositionFromIndex(idx);
            int lx = pos[0];
            int y = pos[1];
            int lz = pos[2];
            
            int blockId = column.getBlock(lx, y, lz);
            if (blockId == 0) continue;
            
            int wx = column.worldX(lx);
            int wz = column.worldZ(lz);
            
            boolean isTransparent = blockRegistry.hasTag(blockId, BlockTag.ALPHA_BLEND) ||
                                   blockRegistry.hasTag(blockId, BlockTag.ALPHA_CUTOUT);
            
            // For each face direction
            FaceDirection[] faces = {
                FaceDirection.SOUTH,   // +Z
                FaceDirection.NORTH,   // -Z
                FaceDirection.TOP,     // +Y
                FaceDirection.BOTTOM,  // -Y
                FaceDirection.EAST,    // +X
                FaceDirection.WEST     // -X
            };
            
            for (FaceDirection face : faces) {
                int neighborId = getNeighborBlock(column, world, wx, y, wz, face);
                
                if (!shouldCullFace(blockRegistry, blockId, neighborId)) {
                    // Emit face
                    List<Float> vertexList = isTransparent ? transparentVertices : opaqueVertices;
                    List<Short> indexList = isTransparent ? transparentIndices : opaqueIndices;
                    short[] vertexIdRef = isTransparent ? new short[]{transparentVertexIndex} : new short[]{opaqueVertexIndex};
                    
                    TextureRegion texRegion = resolveTexture(textureResolver, blockId, face);
                    emitFace(vertexList, wx, y, wz, face, texRegion, vertexIdRef, indexList);
                    
                    if (isTransparent) {
                        transparentVertexIndex = vertexIdRef[0];
                    } else {
                        opaqueVertexIndex = vertexIdRef[0];
                    }
                    
                    // Update bounds
                    if (!boundsInitialized) {
                        bounds.set(new com.badlogic.gdx.math.Vector3(wx, y, wz),
                                  new com.badlogic.gdx.math.Vector3(wx + 1, y + 1, wz + 1));
                        boundsInitialized = true;
                    } else {
                        bounds.ext(new com.badlogic.gdx.math.Vector3(wx, y, wz));
                        bounds.ext(new com.badlogic.gdx.math.Vector3(wx + 1, y + 1, wz + 1));
                    }
                }
            }
        }
        
        // Convert lists to arrays
        float[] opaqueVerts = floatListToArray(opaqueVertices);
        short[] opaqueInds = shortListToArray(opaqueIndices);
        float[] transparentVerts = floatListToArray(transparentVertices);
        short[] transparentInds = shortListToArray(transparentIndices);
        
        ColumnMesh.MeshGeometry opaqueGeometryObj = new ColumnMesh.MeshGeometry(opaqueVerts, opaqueInds);
        ColumnMesh.MeshGeometry transparentGeometryObj = new ColumnMesh.MeshGeometry(transparentVerts, transparentInds);
        
        if (!boundsInitialized) {
            bounds.set(new com.badlogic.gdx.math.Vector3(0, 0, 0),
                      new com.badlogic.gdx.math.Vector3(1, 1, 1));
        }
        
        return new ColumnMesh(opaqueGeometryObj, transparentGeometryObj, bounds, isFloating);
    }

    private static Map<Integer, List<Integer>> partitionByVerticalSlice(List<Integer> blockIndices) {
        Map<Integer, List<Integer>> bySlice = new HashMap<>();
        for (int idx : blockIndices) {
            int y = idx % Constants.WORLD_HEIGHT;
            int slice = y / MESH_BATCH_HEIGHT;
            bySlice.computeIfAbsent(slice, ignored -> new ArrayList<>()).add(idx);
        }
        return bySlice;
    }

    private static TextureRegion resolveTexture(TextureRegionResolver textureResolver, int blockId, FaceDirection face) {
        if (textureResolver != null) {
            try {
                return textureResolver.resolve(blockId, face);
            } catch (Exception ignored) {
                TextureRegion fallback = textureResolver.resolvePath("textures/blocks/stone.png");
                if (fallback != null) {
                    return fallback;
                }
            }
        }
        return TextureGenerator.getRegion(blockId, face);
    }
    
    /**
     * Get neighbor block at face-adjacent position.
     */
    private static int getNeighborBlock(BlockColumn column, WorldGrid world, int wx, int y, int wz, FaceDirection face) {
        int nwx = wx;
        int ny = y;
        int nwz = wz;
        
        switch (face) {
            case SOUTH:   nwz++; break;
            case NORTH:   nwz--; break;
            case TOP:     ny++; break;
            case BOTTOM:  ny--; break;
            case EAST:    nwx++; break;
            case WEST:    nwx--; break;
        }
        
        return world.getBlock(nwx, ny, nwz);
    }
    
    /**
     * Determine if face should be culled based on neighbor block.
     */
    private static boolean shouldCullFace(BlockRegistry blockRegistry, int blockId, int neighborId) {
        if (neighborId == 0) return false;  // Air — never cull
        
        boolean currentTransparent = blockRegistry.hasTag(blockId, BlockTag.ALPHA_BLEND) ||
                                    blockRegistry.hasTag(blockId, BlockTag.ALPHA_CUTOUT);
        boolean neighborTransparent = blockRegistry.hasTag(neighborId, BlockTag.ALPHA_BLEND) ||
                                     blockRegistry.hasTag(neighborId, BlockTag.ALPHA_CUTOUT);
        
        if (currentTransparent) {
            // Transparent blocks only cull against same type
            return neighborId == blockId;
        }
        
        // Opaque blocks cull against solid non-transparent neighbors
        boolean neighborSolid = blockRegistry.hasTag(neighborId, BlockTag.SOLID);
        return neighborSolid && !neighborTransparent;
    }
    
    /**
     * Check if a block is solid.
     */
    private static boolean isSolid(BlockRegistry blockRegistry, int blockId) {
        return blockRegistry.hasTag(blockId, BlockTag.SOLID);
    }
    
    /**
     * Emit a single face into the vertex/index lists.
     */
    private static void emitFace(List<Float> vertices, int wx, int y, int wz, FaceDirection face,
                               TextureRegion texRegion, short[] vertexIdRef, List<Short> indices) {
        short startIdx = vertexIdRef[0];
        
        float u = texRegion.getU();
        float v = texRegion.getV();
        float u2 = texRegion.getU2();
        float v2 = texRegion.getV2();
        float nx;
        float ny;
        float nz;
        
        // Emit 4 vertices per face (position + normal + uv + color)
        switch (face) {
            case SOUTH:  // +Z face
                nx = 0f;
                ny = 0f;
                nz = 1f;
                addVertex(vertices, wx, y, wz + 1, nx, ny, nz, u, v2);
                addVertex(vertices, wx + 1, y, wz + 1, nx, ny, nz, u2, v2);
                addVertex(vertices, wx + 1, y + 1, wz + 1, nx, ny, nz, u2, v);
                addVertex(vertices, wx, y + 1, wz + 1, nx, ny, nz, u, v);
                break;
            case NORTH:  // -Z face
                nx = 0f;
                ny = 0f;
                nz = -1f;
                addVertex(vertices, wx + 1, y, wz, nx, ny, nz, u, v2);
                addVertex(vertices, wx, y, wz, nx, ny, nz, u2, v2);
                addVertex(vertices, wx, y + 1, wz, nx, ny, nz, u2, v);
                addVertex(vertices, wx + 1, y + 1, wz, nx, ny, nz, u, v);
                break;
            case TOP:  // +Y face
                nx = 0f;
                ny = 1f;
                nz = 0f;
                addVertex(vertices, wx, y + 1, wz, nx, ny, nz, u, v2);
                addVertex(vertices, wx + 1, y + 1, wz, nx, ny, nz, u2, v2);
                addVertex(vertices, wx + 1, y + 1, wz + 1, nx, ny, nz, u2, v);
                addVertex(vertices, wx, y + 1, wz + 1, nx, ny, nz, u, v);
                break;
            case BOTTOM:  // -Y face
                nx = 0f;
                ny = -1f;
                nz = 0f;
                addVertex(vertices, wx, y, wz + 1, nx, ny, nz, u, v2);
                addVertex(vertices, wx + 1, y, wz + 1, nx, ny, nz, u2, v2);
                addVertex(vertices, wx + 1, y, wz, nx, ny, nz, u2, v);
                addVertex(vertices, wx, y, wz, nx, ny, nz, u, v);
                break;
            case EAST:  // +X face
                nx = 1f;
                ny = 0f;
                nz = 0f;
                addVertex(vertices, wx + 1, y, wz, nx, ny, nz, u, v2);
                addVertex(vertices, wx + 1, y, wz + 1, nx, ny, nz, u2, v2);
                addVertex(vertices, wx + 1, y + 1, wz + 1, nx, ny, nz, u2, v);
                addVertex(vertices, wx + 1, y + 1, wz, nx, ny, nz, u, v);
                break;
            case WEST:  // -X face
                nx = -1f;
                ny = 0f;
                nz = 0f;
                addVertex(vertices, wx, y, wz + 1, nx, ny, nz, u, v2);
                addVertex(vertices, wx, y, wz, nx, ny, nz, u2, v2);
                addVertex(vertices, wx, y + 1, wz, nx, ny, nz, u2, v);
                addVertex(vertices, wx, y + 1, wz + 1, nx, ny, nz, u, v);
                break;
        }
        
        // Add indices for quad (2 triangles)
        indices.add((short) (startIdx + 0));
        indices.add((short) (startIdx + 1));
        indices.add((short) (startIdx + 2));
        indices.add((short) (startIdx + 0));
        indices.add((short) (startIdx + 2));
        indices.add((short) (startIdx + 3));
        
        vertexIdRef[0] = (short) (startIdx + 4);
    }

    private static void addVertex(List<Float> vertices,
                                  float x,
                                  float y,
                                  float z,
                                  float nx,
                                  float ny,
                                  float nz,
                                  float u,
                                  float v) {
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);
        vertices.add(u);
        vertices.add(v);
        vertices.add(WHITE);
        vertices.add(WHITE);
        vertices.add(WHITE);
        vertices.add(WHITE);
    }
    
    private static int getVoxelIndex(int localX, int y, int localZ) {
        return localX * Constants.COLUMN_SIZE * Constants.WORLD_HEIGHT +
               localZ * Constants.WORLD_HEIGHT +
               y;
    }
    
    private static int[] getVoxelPositionFromIndex(int idx) {
        int y = idx % Constants.WORLD_HEIGHT;
        int lz = (idx / Constants.WORLD_HEIGHT) % Constants.COLUMN_SIZE;
        int lx = idx / (Constants.COLUMN_SIZE * Constants.WORLD_HEIGHT);
        return new int[]{lx, y, lz};
    }
    
    private static float[] floatListToArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
    
    private static short[] shortListToArray(List<Short> list) {
        short[] arr = new short[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
}
