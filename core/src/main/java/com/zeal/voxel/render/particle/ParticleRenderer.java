package com.zeal.voxel.render.particle;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.render.TextureGenerator;
import com.zeal.voxel.render.pbr.PbrMaterial;
import com.zeal.voxel.render.pbr.PbrMaterialTable;
import com.zeal.voxel.render.shader.ShaderManager;
import com.zeal.voxel.render.shader.ShaderPrograms;
import com.zeal.voxel.render.shader.ShaderUniform;
import com.zeal.voxel.render.shadow.CascadedShadowMap;
import com.zeal.voxel.util.Constants;
import com.zeal.voxel.util.CsmConstants;

import java.util.List;

/**
 * Batches all live particles into a single dynamic Mesh rebuilt each frame.
 * Each particle is a tiny axis-aligned cube (6 faces, 24 vertices, 36 indices).
 * Uses the VOXEL_PBR shader so particles share PBR lighting, CSM shadows, fog.
 * Does NOT emit particles — that is BlockBreakEmitter's responsibility.
 */
public class ParticleRenderer {
    private static final int VERTS_PER_PARTICLE = 24;
    private static final int INDICES_PER_PARTICLE = 36;
    private static final int FLOATS_PER_VERTEX = 12; // pos(3) + normal(3) + uv(2) + color(4)

    private final BlockBreakEmitter emitter;
    private final ShaderManager shaderManager;
    private Mesh mesh;
    // OPTIMIZED: Cache immutable uniforms and reusable frame buffers for particle batching.
    private final Matrix4 identityModel = new Matrix4();
    private final Vector3 sunDir = new Vector3(0.3f, 1.0f, 0.4f).nor();
    private final Vector3 sunColor = new Vector3(1.0f, 0.98f, 0.9f);
    private final Vector3 ambientColor = new Vector3(0.4f, 0.4f, 0.45f);
    private final float[] frameVertices;
    private final short[] frameIndices;

    public ParticleRenderer(BlockBreakEmitter emitter, ShaderManager shaderManager) {
        this.emitter = emitter;
        this.shaderManager = shaderManager;

        int maxVerts = Constants.MAX_PARTICLES * VERTS_PER_PARTICLE;
        int maxIndices = Constants.MAX_PARTICLES * INDICES_PER_PARTICLE;
        // OPTIMIZED: Allocate once and reuse each frame to avoid heap churn.
        frameVertices = new float[maxVerts * FLOATS_PER_VERTEX];
        frameIndices = new short[maxIndices];
        mesh = new Mesh(false, maxVerts, maxIndices,
            new VertexAttribute(Usage.Position, 3, "a_position"),
            new VertexAttribute(Usage.Normal, 3, "a_normal"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
            new VertexAttribute(Usage.ColorUnpacked, 4, "a_color")
        );
    }

    public void render(Camera camera, CascadedShadowMap csm, PbrMaterialTable pbrTable) {
        List<BlockParticle> particles = emitter.getLiveParticles();
        if (particles.isEmpty()) return;

        int count = Math.min(particles.size(), Constants.MAX_PARTICLES);
        // OPTIMIZED: Fill preallocated arrays instead of creating fresh arrays each frame.
        float[] vertices = frameVertices;
        short[] indices = frameIndices;

        int vi = 0;
        short baseVertex = 0;
        int ii = 0;

        for (int p = 0; p < count; p++) {
            BlockParticle part = particles.get(p);
            float s = part.size * 0.5f;
            float x = part.position.x;
            float y = part.position.y;
            float z = part.position.z;

            float texTypesCount = 6f;
            float u0 = (float) part.blockType / texTypesCount;
            float u1 = (float) (part.blockType + 1) / texTypesCount;

            // 6 faces, 4 vertices each, same winding as ChunkMesher
            // TOP
            vi = addCubeVert(vertices, vi, x-s, y+s, z-s, 0,1,0, u0, 0, 1);
            vi = addCubeVert(vertices, vi, x-s, y+s, z+s, 0,1,0, u0, 1, 1);
            vi = addCubeVert(vertices, vi, x+s, y+s, z+s, 0,1,0, u1, 1, 1);
            vi = addCubeVert(vertices, vi, x+s, y+s, z-s, 0,1,0, u1, 0, 1);
            ii = addFaceIndices(indices, ii, baseVertex); baseVertex += 4;

            // BOTTOM
            vi = addCubeVert(vertices, vi, x-s, y-s, z+s, 0,-1,0, u0, 0, 1);
            vi = addCubeVert(vertices, vi, x-s, y-s, z-s, 0,-1,0, u0, 1, 1);
            vi = addCubeVert(vertices, vi, x+s, y-s, z-s, 0,-1,0, u1, 1, 1);
            vi = addCubeVert(vertices, vi, x+s, y-s, z+s, 0,-1,0, u1, 0, 1);
            ii = addFaceIndices(indices, ii, baseVertex); baseVertex += 4;

            // NORTH (-Z)
            vi = addCubeVert(vertices, vi, x+s, y+s, z-s, 0,0,-1, u0, 0, 1);
            vi = addCubeVert(vertices, vi, x+s, y-s, z-s, 0,0,-1, u0, 1, 1);
            vi = addCubeVert(vertices, vi, x-s, y-s, z-s, 0,0,-1, u1, 1, 1);
            vi = addCubeVert(vertices, vi, x-s, y+s, z-s, 0,0,-1, u1, 0, 1);
            ii = addFaceIndices(indices, ii, baseVertex); baseVertex += 4;

            // SOUTH (+Z)
            vi = addCubeVert(vertices, vi, x-s, y+s, z+s, 0,0,1, u0, 0, 1);
            vi = addCubeVert(vertices, vi, x-s, y-s, z+s, 0,0,1, u0, 1, 1);
            vi = addCubeVert(vertices, vi, x+s, y-s, z+s, 0,0,1, u1, 1, 1);
            vi = addCubeVert(vertices, vi, x+s, y+s, z+s, 0,0,1, u1, 0, 1);
            ii = addFaceIndices(indices, ii, baseVertex); baseVertex += 4;

            // EAST (+X)
            vi = addCubeVert(vertices, vi, x+s, y+s, z-s, 1,0,0, u0, 0, 1);
            vi = addCubeVert(vertices, vi, x+s, y-s, z-s, 1,0,0, u0, 1, 1);
            vi = addCubeVert(vertices, vi, x+s, y-s, z+s, 1,0,0, u1, 1, 1);
            vi = addCubeVert(vertices, vi, x+s, y+s, z+s, 1,0,0, u1, 0, 1);
            ii = addFaceIndices(indices, ii, baseVertex); baseVertex += 4;

            // WEST (-X)
            vi = addCubeVert(vertices, vi, x-s, y+s, z+s, -1,0,0, u0, 0, 1);
            vi = addCubeVert(vertices, vi, x-s, y-s, z+s, -1,0,0, u0, 1, 1);
            vi = addCubeVert(vertices, vi, x-s, y-s, z-s, -1,0,0, u1, 1, 1);
            vi = addCubeVert(vertices, vi, x-s, y+s, z-s, -1,0,0, u1, 0, 1);
            ii = addFaceIndices(indices, ii, baseVertex); baseVertex += 4;
        }

        mesh.setVertices(vertices, 0, vi);
        mesh.setIndices(indices, 0, ii);

        ShaderProgram shader = shaderManager.get(ShaderPrograms.VOXEL_PBR);
        shader.bind();
        // OPTIMIZED: Reuse identity model transform for particle draw pass.
        ShaderUniform.setMatrix4(shader, "u_modelTrans", identityModel);
        ShaderUniform.setMatrix4(shader, "u_projViewTrans", camera.combined);
        ShaderUniform.setMatrix4(shader, "u_viewTrans", camera.view);
        ShaderUniform.setInt(shader, "u_texture", 0);
        ShaderUniform.setVector3(shader, "u_sunDir", sunDir);
        ShaderUniform.setVector3(shader, "u_sunColor", sunColor);
        ShaderUniform.setVector3(shader, "u_ambientColor", ambientColor);
        ShaderUniform.set4f(shader, "u_fogColor", 0.2f, 0.4f, 0.6f, 1.0f);
        ShaderUniform.setFloat(shader, "u_fogStart", 20f);
        ShaderUniform.setFloat(shader, "u_fogEnd", 80f);
        ShaderUniform.setVector3(shader, "u_camPos", camera.position);

        // CSM shadow uniforms
        Matrix4[] lsm = csm.getLightSpaceMatrices();
        Texture[] shadowMaps = csm.getShadowMapTextures();
        float[] splits = csm.getCascadeSplits();

        for (int i = 0; i < CsmConstants.CASCADE_COUNT; i++) {
            int unit = i + 1;
            shadowMaps[i].bind(unit);
            ShaderUniform.setInt(shader, "u_shadowMap" + i, unit);
        }

        ShaderUniform.setMatrix4(shader, "u_lightSpaceMatrix0", lsm[0]);
        ShaderUniform.setMatrix4(shader, "u_lightSpaceMatrix1", lsm[1]);
        ShaderUniform.setMatrix4(shader, "u_lightSpaceMatrix2", lsm[2]);
        ShaderUniform.setMatrix4(shader, "u_lightSpaceMatrix3", lsm[3]);

        ShaderUniform.setFloat(shader, "u_cascadeSplit0", splits[1]);
        ShaderUniform.setFloat(shader, "u_cascadeSplit1", splits[2]);
        ShaderUniform.setFloat(shader, "u_cascadeSplit2", splits[3]);
        ShaderUniform.setFloat(shader, "u_cascadeSplit3", splits[4]);

        ShaderUniform.setFloat(shader, "u_shadowBias0", CsmConstants.CASCADE_BIASES[0]);
        ShaderUniform.setFloat(shader, "u_shadowBias1", CsmConstants.CASCADE_BIASES[1]);
        ShaderUniform.setFloat(shader, "u_shadowBias2", CsmConstants.CASCADE_BIASES[2]);
        ShaderUniform.setFloat(shader, "u_shadowBias3", CsmConstants.CASCADE_BIASES[3]);

        // Default PBR material for particles (stone-like)
        PbrMaterial defaultMat = pbrTable.get(1);
        ShaderUniform.setFloat(shader, "u_metallic", defaultMat.metallic);
        ShaderUniform.setFloat(shader, "u_roughness", defaultMat.roughness);
        ShaderUniform.setVector3(shader, "u_emission", defaultMat.emission);
        ShaderUniform.setFloat(shader, "u_emissiveMask", 0f);

        ShaderUniform.setInt(shader, "u_debugCascades", 0);
        ShaderUniform.set4f(shader, "u_overrideColor", 0f, 0f, 0f, 0f);

        TextureGenerator.getAtlasTexture().bind(0);

        mesh.render(shader, GL20.GL_TRIANGLES, 0, ii);
    }

    private int addCubeVert(float[] v, int i, float x, float y, float z, float nx, float ny, float nz, float u, float uv, float ao) {
        v[i++] = x; v[i++] = y; v[i++] = z;
        v[i++] = nx; v[i++] = ny; v[i++] = nz;
        v[i++] = u; v[i++] = uv;
        v[i++] = ao; v[i++] = 1; v[i++] = 1; v[i++] = 1;
        return i;
    }

    private int addFaceIndices(short[] indices, int ii, short base) {
        indices[ii++] = base;
        indices[ii++] = (short)(base + 1);
        indices[ii++] = (short)(base + 2);
        indices[ii++] = (short)(base + 2);
        indices[ii++] = (short)(base + 3);
        indices[ii++] = base;
        return ii;
    }

    public void dispose() {
        if (mesh != null) mesh.dispose();
    }
}
