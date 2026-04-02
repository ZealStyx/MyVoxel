package com.zeal.voxel.physics;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.PHY_ScalarType;
import com.badlogic.gdx.physics.bullet.collision.btBvhTriangleMeshShape;
import com.badlogic.gdx.physics.bullet.collision.btIndexedMesh;
import com.badlogic.gdx.physics.bullet.collision.btTriangleIndexVertexArray;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.zeal.voxel.world.Chunk;
import com.zeal.voxel.world.ChunkMesher;
import com.zeal.voxel.world.MeshData;
import com.zeal.voxel.world.WorldGrid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Wraps a static chunk collision body built from chunk mesh geometry.
 *
 * ── MEMORY SAFETY ───────────────────────────────────────────────────────────
 * btIndexedMesh / btTriangleIndexVertexArray hold raw C++ pointers directly
 * into vertexByteBuffer and indexByteBuffer. These buffers MUST remain
 * alive for the entire lifetime of the physics shape.
 *
 * We use ByteBuffer.allocateDirect() which allocates off-heap memory that
 * the JVM GC does NOT collect. The buffers are freed only when they become
 * unreachable AND the direct memory cleaner runs — well after dispose().
 *
 * ⚠ Do NOT use LibGDX Mesh or Model as the backing store. Their GC finalizers
 *   call Mesh.dispose() → frees the native OpenGL VBO buffers → Bullet holds
 *   dangling C++ pointer → EXCEPTION_ACCESS_VIOLATION in stepSimulation.
 * ────────────────────────────────────────────────────────────────────────────
 */
public class StaticChunkBody {

    // MeshData interleaved vertex layout: pos(3) + normal(3) + uv(2) + color(4) = 12 floats
    private static final int VERTEX_STRIDE_FLOATS   = 12;
    // Physics only needs XYZ positions per vertex — 3 floats = 12 bytes
    private static final int COLLISION_VERTEX_BYTES = 3 * Float.BYTES;
    // For int32 indices, triangle stride = 3 indices × 4 bytes
    private static final int TRIANGLE_INDEX_STRIDE  = 3 * Integer.BYTES;

    // ── STRONG REFERENCES ─────────────────────────────────────────────────────
    // Bullet's native layer holds raw C++ pointers into these. Fields prevent GC.
    @SuppressWarnings("unused")
    private final ByteBuffer vertexByteBuffer;
    @SuppressWarnings("unused")
    private final ByteBuffer indexByteBuffer;
    @SuppressWarnings("unused")
    private final btIndexedMesh indexedMesh;  // native struct must stay alive

    private final btTriangleIndexVertexArray triArray;
    private final btBvhTriangleMeshShape meshShape;
    public final btRigidBody rigidBody;

    private StaticChunkBody(ByteBuffer vertexByteBuffer,
                            ByteBuffer indexByteBuffer,
                            btIndexedMesh indexedMesh,
                            btTriangleIndexVertexArray triArray,
                            btBvhTriangleMeshShape meshShape,
                            btRigidBody rigidBody) {
        this.vertexByteBuffer = vertexByteBuffer;
        this.indexByteBuffer  = indexByteBuffer;
        this.indexedMesh      = indexedMesh;
        this.triArray         = triArray;
        this.meshShape        = meshShape;
        this.rigidBody        = rigidBody;
    }

    /**
     * Builds a static rigid body for the given chunk.
     * Returns null if the chunk generates no geometry (empty chunk).
     */
    public static StaticChunkBody build(Chunk chunk, WorldGrid world,
                                        int chunkWorldX, int chunkWorldY, int chunkWorldZ,
                                        Vector3 chunkWorldOffset) {

        MeshData data = ChunkMesher.buildMesh(chunk, world, chunkWorldX, chunkWorldY, chunkWorldZ);
        if (data.vertices.length == 0) return null;

        final int vertexCount = data.vertices.length / VERTEX_STRIDE_FLOATS;
        final int indexCount  = data.indices.length;
        final int triCount    = indexCount / 3;

        // ── 1. Pack XYZ positions only into a direct native-order ByteBuffer ────
        // We skip normals/UVs/color — Bullet only needs positions for collision.
        ByteBuffer vertBuf = ByteBuffer
                .allocateDirect(vertexCount * COLLISION_VERTEX_BYTES)
                .order(ByteOrder.nativeOrder());

        for (int i = 0; i < vertexCount; i++) {
            int base = i * VERTEX_STRIDE_FLOATS;
            vertBuf.putFloat(data.vertices[base]);       // X
            vertBuf.putFloat(data.vertices[base + 1]);   // Y
            vertBuf.putFloat(data.vertices[base + 2]);   // Z
        }
        vertBuf.position(0);

        // ── 2. Promote short[] indices to int32 in a direct native-order ByteBuffer
        // btIndexedMesh with PHY_INTEGER expects 32-bit unsigned triangle indices.
        ByteBuffer idxBuf = ByteBuffer
                .allocateDirect(indexCount * Integer.BYTES)
                .order(ByteOrder.nativeOrder());

        for (short s : data.indices) {
            idxBuf.putInt(s & 0xFFFF); // promote short → unsigned int
        }
        idxBuf.position(0);

        // ── 3. Build a btIndexedMesh describing the geometry ────────────────────
        btIndexedMesh im = new btIndexedMesh();
        im.setNumVertices(vertexCount);
        im.setVertexStride(COLLISION_VERTEX_BYTES);   // 12 bytes: XYZ floats
        im.setVertexBase(vertBuf);

        im.setNumTriangles(triCount);
        im.setTriangleIndexStride(TRIANGLE_INDEX_STRIDE); // 12 bytes: 3× int32
        im.setTriangleIndexBase(idxBuf);

        // ── 4. Build the triangle vertex array and BVH shape ────────────────────
        btTriangleIndexVertexArray triArray = new btTriangleIndexVertexArray();
        triArray.addIndexedMesh(im, PHY_ScalarType.PHY_INTEGER);

        btBvhTriangleMeshShape shape = new btBvhTriangleMeshShape(triArray, true);

        // ── 5. Static rigid body (mass = 0) ─────────────────────────────────────
        btRigidBody.btRigidBodyConstructionInfo info =
                new btRigidBody.btRigidBodyConstructionInfo(0f, null, shape, new Vector3());
        btRigidBody body = new btRigidBody(info);
        // CRITICAL BUG FIX (Global Clipping):
        // ChunkMesher already returns vertices in absolute WORLD coordinates.
        // If we also set a translation on the body, the collision geometry is shifted twice 
        // (moving it far away from the visual mesh). We must use Identity transform here.
        body.setWorldTransform(new Matrix4());
        info.dispose();

        return new StaticChunkBody(vertBuf, idxBuf, im, triArray, shape, body);
    }

    public void dispose() {
        rigidBody.dispose();
        meshShape.dispose();
        triArray.dispose();
        indexedMesh.dispose();
        // vertexByteBuffer / indexByteBuffer are direct — native memory cleaned up automatically
    }
}
