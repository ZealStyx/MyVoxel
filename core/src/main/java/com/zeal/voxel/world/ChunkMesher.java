package com.zeal.voxel.world;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.zeal.voxel.block.BlockRegistry;
import com.zeal.voxel.block.BlockTag;
import com.zeal.voxel.block.TextureRegionResolver;
import com.zeal.voxel.block.model.BlockMesherStrategy;
import com.zeal.voxel.block.model.BlockModelMesher;
import com.zeal.voxel.block.model.BlockModelRegistry;
import com.zeal.voxel.block.model.MeshBuilder;
import com.zeal.voxel.block.model.SimpleCubeMesher;
import com.zeal.voxel.render.TextureGenerator;
import com.zeal.voxel.render.ao.AmbientOcclusionCalculator;
import com.zeal.voxel.render.ao.FaceDirection;
import com.zeal.voxel.render.ao.VoxelGrid;
import com.zeal.voxel.render.culling.SubChunkSection;
import com.zeal.voxel.util.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Generates mesh data for a chunk using simple face culling and baked AO. */
public class ChunkMesher {
    public static final int LOD_NEAR_MAX_CHUNK_DISTANCE = 4;
    public static final int LOD_MID_MAX_CHUNK_DISTANCE = 8;

    public static MeshData buildMesh(Chunk chunk, WorldGrid world, int chunkWorldX, int chunkWorldY, int chunkWorldZ) {
        List<Float> vertices = new ArrayList<>();
        List<Short> indices = new ArrayList<>();
        short vertexIndex = 0;

        List<Float> debugVertices = new ArrayList<>();

        // Wrap WorldGrid as a VoxelGrid for AO lookups
        VoxelGrid aoGrid = (qx, qy, qz) -> world.getBlock(qx, qy, qz) != 0;

        for (int x = 0; x < Constants.CHUNK_SIZE; x++) {
            for (int y = 0; y < Constants.CHUNK_SIZE; y++) {
                for (int z = 0; z < Constants.CHUNK_SIZE; z++) {
                    int blockId = chunk.getBlock(x, y, z);
                    if (blockId == 0) continue;

                    int wx = chunkWorldX * Constants.CHUNK_SIZE + x;
                    int wy = chunkWorldY * Constants.CHUNK_SIZE + y;
                    int wz = chunkWorldZ * Constants.CHUNK_SIZE + z;

                    // Front (+Z) i.e. South
                    if (isTransparent(world, wx, wy, wz + 1)) {
                        float[] ao = AmbientOcclusionCalculator.calculateFaceAO(aoGrid, wx, wy, wz, FaceDirection.SOUTH);
                        TextureRegion r = TextureGenerator.getRegion(blockId, FaceDirection.SOUTH);
                        emitFace(vertices, wx, wy, wz, FaceDirection.SOUTH, r.getU(), r.getV(), r.getU2(), r.getV2(), ao);
                        vertexIndex = addIndices(indices, vertexIndex);
                        if (Constants.DEBUG) addDebugNormal(debugVertices, wx, wy, wz, FaceDirection.SOUTH);
                    }
                    // Back (-Z) i.e. North
                    if (isTransparent(world, wx, wy, wz - 1)) {
                        float[] ao = AmbientOcclusionCalculator.calculateFaceAO(aoGrid, wx, wy, wz, FaceDirection.NORTH);
                        TextureRegion r = TextureGenerator.getRegion(blockId, FaceDirection.NORTH);
                        emitFace(vertices, wx, wy, wz, FaceDirection.NORTH, r.getU(), r.getV(), r.getU2(), r.getV2(), ao);
                        vertexIndex = addIndices(indices, vertexIndex);
                        if (Constants.DEBUG) addDebugNormal(debugVertices, wx, wy, wz, FaceDirection.NORTH);
                    }
                    // Top (+Y)
                    if (isTransparent(world, wx, wy + 1, wz)) {
                        float[] ao = AmbientOcclusionCalculator.calculateFaceAO(aoGrid, wx, wy, wz, FaceDirection.TOP);
                        TextureRegion r = TextureGenerator.getRegion(blockId, FaceDirection.TOP);
                        emitFace(vertices, wx, wy, wz, FaceDirection.TOP, r.getU(), r.getV(), r.getU2(), r.getV2(), ao);
                        vertexIndex = addIndices(indices, vertexIndex);
                        if (Constants.DEBUG) addDebugNormal(debugVertices, wx, wy, wz, FaceDirection.TOP);
                    }
                    // Bottom (-Y)
                    if (isTransparent(world, wx, wy - 1, wz)) {
                        float[] ao = AmbientOcclusionCalculator.calculateFaceAO(aoGrid, wx, wy, wz, FaceDirection.BOTTOM);
                        TextureRegion r = TextureGenerator.getRegion(blockId, FaceDirection.BOTTOM);
                        emitFace(vertices, wx, wy, wz, FaceDirection.BOTTOM, r.getU(), r.getV(), r.getU2(), r.getV2(), ao);
                        vertexIndex = addIndices(indices, vertexIndex);
                        if (Constants.DEBUG) addDebugNormal(debugVertices, wx, wy, wz, FaceDirection.BOTTOM);
                    }
                    // Right (+X) i.e. East
                    if (isTransparent(world, wx + 1, wy, wz)) {
                        float[] ao = AmbientOcclusionCalculator.calculateFaceAO(aoGrid, wx, wy, wz, FaceDirection.EAST);
                        TextureRegion r = TextureGenerator.getRegion(blockId, FaceDirection.EAST);
                        emitFace(vertices, wx, wy, wz, FaceDirection.EAST, r.getU(), r.getV(), r.getU2(), r.getV2(), ao);
                        vertexIndex = addIndices(indices, vertexIndex);
                        if (Constants.DEBUG) addDebugNormal(debugVertices, wx, wy, wz, FaceDirection.EAST);
                    }
                    // Left (-X) i.e. West
                    if (isTransparent(world, wx - 1, wy, wz)) {
                        float[] ao = AmbientOcclusionCalculator.calculateFaceAO(aoGrid, wx, wy, wz, FaceDirection.WEST);
                        TextureRegion r = TextureGenerator.getRegion(blockId, FaceDirection.WEST);
                        emitFace(vertices, wx, wy, wz, FaceDirection.WEST, r.getU(), r.getV(), r.getU2(), r.getV2(), ao);
                        vertexIndex = addIndices(indices, vertexIndex);
                        if (Constants.DEBUG) addDebugNormal(debugVertices, wx, wy, wz, FaceDirection.WEST);
                    }
                }
            }
        }

        float[] vArray = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) vArray[i] = vertices.get(i);

        short[] iArray = new short[indices.size()];
        for (int i = 0; i < indices.size(); i++) iArray[i] = indices.get(i);
        
        float[] dbgArray = null;
        if (Constants.DEBUG) {
            dbgArray = new float[debugVertices.size()];
            for (int i = 0; i < debugVertices.size(); i++) dbgArray[i] = debugVertices.get(i);
        }

        return new MeshData(vArray, iArray, dbgArray);
    }

    public static SectionMeshBuildResult buildSectionMeshes(
            Chunk chunk,
            SubChunkSection section,
            WorldGrid world,
            int chunkWorldX,
            int chunkWorldY,
            int chunkWorldZ,
            int lodLevel,
            TextureRegionResolver textureResolver,
            BlockRegistry blockRegistry) {
        if (lodLevel == Chunk.LOD_FAR) {
            return buildFarLodSectionMesh(chunk, section);
        }

        Map<Integer, MeshBuilder> opaqueBuilders = new HashMap<>();
        Map<Integer, MeshBuilder> transparentBuilders = new HashMap<>();
        BlockModelRegistry modelRegistry = BlockModelRegistry.getActive();
        SimpleCubeMesher cubeMesher = new SimpleCubeMesher(textureResolver);
        BlockModelMesher modelMesher = modelRegistry == null ? null : new BlockModelMesher(textureResolver, modelRegistry);

        int startX = section.localX;
        int startY = section.localY;
        int startZ = section.localZ;
        int endX = startX + SubChunkSection.SECTION_SIZE;
        int endY = startY + SubChunkSection.SECTION_SIZE;
        int endZ = startZ + SubChunkSection.SECTION_SIZE;

        for (int x = startX; x < endX; x++) {
            for (int y = startY; y < endY; y++) {
                for (int z = startZ; z < endZ; z++) {
                    int blockId = chunk.getBlock(x, y, z);
                    if (blockId == 0) {
                        continue;
                    }

                    int wx = chunkWorldX * Constants.CHUNK_SIZE + x;
                    int wy = chunkWorldY * Constants.CHUNK_SIZE + y;
                    int wz = chunkWorldZ * Constants.CHUNK_SIZE + z;

                        boolean transparentBlend = isTransparentBlendBlock(blockRegistry, blockId);
                        MeshBuilder builder = transparentBlend
                            ? transparentBuilders.computeIfAbsent(blockId, ignored -> new MeshBuilder())
                            : opaqueBuilders.computeIfAbsent(blockId, ignored -> new MeshBuilder());

                        BlockMesherStrategy strategy =
                            modelMesher != null && modelRegistry != null && modelRegistry.hasModel(blockId)
                                ? modelMesher : cubeMesher;

                    boolean isLiquidBlock = blockRegistry != null && blockRegistry.hasTag(blockId, BlockTag.LIQUID);

                    strategy.emitBlock(
                            builder,
                            blockId,
                            wx,
                            wy,
                            wz,
                            face -> {
                                // OPTIMIZED: Mid LOD drops vertical faces to reduce distant geometry.
                                if (lodLevel == Chunk.LOD_MID
                                        && (face == FaceDirection.TOP || face == FaceDirection.BOTTOM)) {
                                    return true;
                                }
                                if (isLiquidBlock) {
                                    // Water/hot-spring water surface meshing:
                                    // - never render side or bottom faces
                                    // - only render top when the block above is not liquid
                                    if (face != FaceDirection.TOP) {
                                        return true;
                                    }
                                    if (isNeighborLiquid(world, blockRegistry, wx, wy + 1, wz)) {
                                        return true;
                                    }
                                }
                                return isNeighborSolid(world, blockRegistry, blockId, wx, wy, wz, face);
                            });
                }
            }
        }

        SectionMeshBuildResult out = new SectionMeshBuildResult();

        for (Map.Entry<Integer, MeshBuilder> entry : opaqueBuilders.entrySet()) {
            MeshBuilder builder = entry.getValue();
            if (builder.vertices.isEmpty()) {
                continue;
            }
            out.opaqueMeshes.put(entry.getKey(), new MeshData(
                    toFloatArray(builder.vertices),
                    toShortArray(builder.indices),
                    null));
        }

        for (Map.Entry<Integer, MeshBuilder> entry : transparentBuilders.entrySet()) {
            MeshBuilder builder = entry.getValue();
            if (builder.vertices.isEmpty()) {
                continue;
            }
            out.transparentMeshes.put(entry.getKey(), new MeshData(
                    toFloatArray(builder.vertices),
                    toShortArray(builder.indices),
                    null));
        }

        section.isEmpty = out.isEmpty();
        section.isDirty = false;
        return out;
    }

    private static SectionMeshBuildResult buildFarLodSectionMesh(Chunk chunk, SubChunkSection section) {
        // OPTIMIZED: Far LOD uses a single solid-color billboarded top quad per section.
        boolean hasSolid = false;
        int startX = section.localX;
        int startY = section.localY;
        int startZ = section.localZ;
        int endX = startX + SubChunkSection.SECTION_SIZE;
        int endY = startY + SubChunkSection.SECTION_SIZE;
        int endZ = startZ + SubChunkSection.SECTION_SIZE;

        for (int x = startX; x < endX && !hasSolid; x++) {
            for (int y = startY; y < endY && !hasSolid; y++) {
                for (int z = startZ; z < endZ; z++) {
                    if (chunk.getBlock(x, y, z) != 0) {
                        hasSolid = true;
                        break;
                    }
                }
            }
        }

        SectionMeshBuildResult out = new SectionMeshBuildResult();
        if (!hasSolid) {
            section.isEmpty = true;
            section.isDirty = false;
            return out;
        }

        float minX = section.chunkPosition.x * Constants.CHUNK_SIZE + section.localX;
        float maxX = minX + SubChunkSection.SECTION_SIZE;
        float minZ = section.chunkPosition.z * Constants.CHUNK_SIZE + section.localZ;
        float maxZ = minZ + SubChunkSection.SECTION_SIZE;
        float y = section.chunkPosition.y * Constants.CHUNK_SIZE + section.localY + SubChunkSection.SECTION_SIZE;

        TextureRegion tex = TextureGenerator.getRegion(1, FaceDirection.TOP);
        float u0 = tex.getU();
        float v0 = tex.getV();
        float u1 = tex.getU2();
        float v1 = tex.getV2();

        float[] vertices = new float[] {
                minX, y, minZ, 0f, 1f, 0f, u0, v1, 1f, 1f, 1f, 1f,
                maxX, y, minZ, 0f, 1f, 0f, u1, v1, 1f, 1f, 1f, 1f,
                maxX, y, maxZ, 0f, 1f, 0f, u1, v0, 1f, 1f, 1f, 1f,
                minX, y, maxZ, 0f, 1f, 0f, u0, v0, 1f, 1f, 1f, 1f
        };
        short[] indices = new short[] {0, 1, 2, 0, 2, 3};

        out.opaqueMeshes.put(1, new MeshData(vertices, indices, null));
        section.isEmpty = false;
        section.isDirty = false;
        return out;
    }

    private static boolean isTransparent(WorldGrid world, int wx, int wy, int wz) {
        return world.getBlock(wx, wy, wz) == 0;
    }

    private static boolean isFaceOpen(WorldGrid world, BlockRegistry blockRegistry, int currentBlockId, int wx, int wy, int wz) {
        int neighborId = world.getBlock(wx, wy, wz);
        if (neighborId == 0) {
            // Treat missing-chunk neighbors as open so boundary faces (especially top faces)
            // are rendered correctly in a single-layer vertical world.
            return true;
        }
        if (blockRegistry == null) {
            return false;
        }

        boolean currentTransparent = blockRegistry.hasTag(currentBlockId, BlockTag.TRANSPARENT);
        boolean sameTypeCull = blockRegistry.hasTag(currentBlockId, BlockTag.SAME_TYPE_CULL);
        if (currentTransparent && sameTypeCull) {
            // Transparent same-type culling: only cull against the same block id.
            return neighborId != currentBlockId;
        }

        boolean solid = blockRegistry.hasTag(neighborId, BlockTag.SOLID);
        boolean transparent = blockRegistry.hasTag(neighborId, BlockTag.TRANSPARENT);
        return !solid || transparent;
    }

    private static boolean isNeighborSolid(WorldGrid world,
                                           BlockRegistry blockRegistry,
                                           int currentBlockId,
                                           int wx,
                                           int wy,
                                           int wz,
                                           FaceDirection face) {
        int offX = 0;
        int offY = 0;
        int offZ = 0;
        switch (face) {
            case NORTH -> offZ = -1;
            case SOUTH -> offZ = 1;
            case EAST -> offX = 1;
            case WEST -> offX = -1;
            case TOP -> offY = 1;
            case BOTTOM -> offY = -1;
        }
        return !isFaceOpen(world, blockRegistry, currentBlockId, wx + offX, wy + offY, wz + offZ);
    }

    private static boolean isTransparentBlendBlock(BlockRegistry blockRegistry, int blockId) {
        if (blockRegistry == null) {
            return false;
        }
        boolean alphaBlend = blockRegistry.hasTag(blockId, BlockTag.ALPHA_BLEND);
        boolean transparentCompat = blockRegistry.hasTag(blockId, BlockTag.TRANSPARENT)
                && !blockRegistry.hasTag(blockId, BlockTag.ALPHA_CUTOUT);
        return alphaBlend || transparentCompat;
    }

    private static boolean isNeighborLiquid(WorldGrid world,
                                            BlockRegistry blockRegistry,
                                            int wx,
                                            int wy,
                                            int wz) {
        int neighborId = world.getBlock(wx, wy, wz);
        if (neighborId == 0 || blockRegistry == null) {
            return false;
        }
        return blockRegistry.hasTag(neighborId, BlockTag.LIQUID);
    }

    private static short addIndices(List<Short> indices, short vIdx) {
        indices.add(vIdx); indices.add((short)(vIdx + 1)); indices.add((short)(vIdx + 2));
        indices.add(vIdx); indices.add((short)(vIdx + 2)); indices.add((short)(vIdx + 3));
        return (short) (vIdx + 4);
    }

    private static void emitFace(List<Float> v, float x, float y, float z, FaceDirection dir, float u0, float v0, float u1, float v1, float[] ao) {
        float[][] verts = FaceGeometry.getVerts(dir);
        float[] normal = FaceGeometry.getNormal(dir);
        addVert(v, x + verts[0][0], y + verts[0][1], z + verts[0][2], normal[0], normal[1], normal[2], u0, v1, ao[0]);
        addVert(v, x + verts[1][0], y + verts[1][1], z + verts[1][2], normal[0], normal[1], normal[2], u0, v0, ao[1]);
        addVert(v, x + verts[2][0], y + verts[2][1], z + verts[2][2], normal[0], normal[1], normal[2], u1, v0, ao[2]);
        addVert(v, x + verts[3][0], y + verts[3][1], z + verts[3][2], normal[0], normal[1], normal[2], u1, v1, ao[3]);
    }

    private static void addVert(List<Float> v, float x, float y, float z, float nx, float ny, float nz, float u, float val, float ao) {
        v.add(x); v.add(y); v.add(z);
        v.add(nx); v.add(ny); v.add(nz);
        v.add(u); v.add(val);
        // a_color: R=AO, G=1, B=1, A=1
        v.add(ao); v.add(1.0f); v.add(1.0f); v.add(1.0f);
    }

    private static void addDebugNormal(List<Float> dbg, float x, float y, float z, FaceDirection dir) {
        float[] n = FaceGeometry.getNormal(dir);
        float cx = x + 0.5f + n[0] * 0.51f;
        float cy = y + 0.5f + n[1] * 0.51f;
        float cz = z + 0.5f + n[2] * 0.51f;
        
        float tx = cx + n[0] * 0.3f;
        float ty = cy + n[1] * 0.3f;
        float tz = cz + n[2] * 0.3f;
        
        dbg.add(cx); dbg.add(cy); dbg.add(cz);
        dbg.add(tx); dbg.add(ty); dbg.add(tz);
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static short[] toShortArray(List<Short> list) {
        short[] out = new short[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }
        return out;
    }

}
