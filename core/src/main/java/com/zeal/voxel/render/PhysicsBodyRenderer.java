package com.zeal.voxel.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.block.BlockRegistry;
import com.zeal.voxel.block.BlockTag;
import com.zeal.voxel.block.TextureRegionResolver;
import com.zeal.voxel.block.model.BlockMesherStrategy;
import com.zeal.voxel.block.model.BlockModelMesher;
import com.zeal.voxel.block.model.BlockModelRegistry;
import com.zeal.voxel.block.model.MeshBuilder;
import com.zeal.voxel.block.model.SimpleCubeMesher;
import com.zeal.voxel.physics.PhysicsBody;
import com.zeal.voxel.physics.PhysicsBodyManager;
import com.zeal.voxel.render.ao.FaceDirection;
import com.zeal.voxel.render.culling.FrustumCuller;
import com.zeal.voxel.render.pbr.PbrMaterial;
import com.zeal.voxel.render.pbr.PbrMaterialTable;
import com.zeal.voxel.render.shader.ShaderManager;
import com.zeal.voxel.render.shader.ShaderPrograms;
import com.zeal.voxel.render.shader.ShaderUniform;
import com.zeal.voxel.render.shadow.CascadedShadowMap;
import com.zeal.voxel.util.Constants;
import com.zeal.voxel.util.CsmConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PhysicsBodyRenderer {
    private static boolean USE_FRUSTUM_CULLING = false;
    // OPTIMIZED: Hoist immutable lighting vectors to avoid per-frame allocations.
    private static final Vector3 SUN_DIR = new Vector3(0.3f, 1.0f, 0.4f).nor();
    private static final Vector3 SUN_COLOR = new Vector3(1.0f, 0.98f, 0.9f);
    private static final Vector3 AMBIENT_COLOR = new Vector3(0.4f, 0.4f, 0.45f);
    private static final Vector3 RIM_COLOR = new Vector3(0.3f, 0.5f, 1.0f);

    private final PhysicsBodyManager manager;
    private final ShaderManager shaderManager;
    private final FrustumCuller frustumCuller;
    private final BlockRegistry blockRegistry;
    private final TextureRegionResolver textureRegionResolver;
    private final Map<PhysicsBody, Mesh> bodyMeshes = new HashMap<>();
    private final Map<PhysicsBody, Mesh> debugMeshes = new HashMap<>();

    private final Matrix4 tempMatrix = new Matrix4();
    private final Matrix3 tempNormalMat = new Matrix3();
    // OPTIMIZED: Reuse helper containers in render/depth/velocity loops.
    private final Matrix4 tempLightMvp = new Matrix4();
    private final Vector3 tempScreenVelColor = new Vector3();

    public PhysicsBodyRenderer(PhysicsBodyManager manager, ShaderManager shaderManager,
                               FrustumCuller frustumCuller, BlockRegistry blockRegistry,
                               TextureRegionResolver textureRegionResolver) {
        this.manager = manager;
        this.shaderManager = shaderManager;
        this.frustumCuller = frustumCuller;
        this.blockRegistry = blockRegistry;
        this.textureRegionResolver = textureRegionResolver;
    }

    /** Renders all physics bodies into the shadow depth FBO. */
    public void renderDepth(Camera sunCamera) {
        if (USE_FRUSTUM_CULLING) {
            frustumCuller.update(sunCamera);
        }

        ShaderProgram shader = shaderManager.get(ShaderPrograms.SHADOW_DEPTH);
        shader.bind();

        cleanupOldMeshes();

        for (PhysicsBody body : manager.getActiveBodies()) {
            if (USE_FRUSTUM_CULLING && !frustumCuller.isBodyVisible(body)) {
                continue;
            }

            Mesh mesh = getOrBuildMesh(body);
            tempMatrix.set(body.getTransform());
            // OPTIMIZED: Reuse temp matrix for light-space MVP composition.
            ShaderUniform.setMatrix4(shader, "u_lightSpaceMVP", tempLightMvp.set(sunCamera.combined).mul(tempMatrix));
            mesh.render(shader, GL20.GL_TRIANGLES);
        }
    }

    /** Full PBR render with CSM shadows and rim lighting. */
    public void render(Camera camera, CascadedShadowMap csm, PbrMaterialTable pbrTable) {
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        if (USE_FRUSTUM_CULLING) {
            frustumCuller.update(camera);
        }

        ShaderProgram shader = shaderManager.get(ShaderPrograms.PHYSICS_BODY_PBR);
        shader.bind();

        ShaderUniform.setMatrix4(shader, "u_projViewTrans", camera.combined);
        ShaderUniform.setMatrix4(shader, "u_viewTrans", camera.view);
        ShaderUniform.setInt(shader, "u_texture", 0);

        // Lighting
        // OPTIMIZED: Use cached immutable vectors for repeated lighting uniforms.
        ShaderUniform.setVector3(shader, "u_sunDir", SUN_DIR);
        ShaderUniform.setVector3(shader, "u_sunColor", SUN_COLOR);
        ShaderUniform.setVector3(shader, "u_ambientColor", AMBIENT_COLOR);
        ShaderUniform.setVector3(shader, "u_camPos", camera.position);

        // Rim lighting
        ShaderUniform.setVector3(shader, "u_rimColor", RIM_COLOR);
        ShaderUniform.setFloat(shader, "u_rimPower", 3.0f);

        // Fog
        ShaderUniform.set4f(shader, "u_fogColor", 0.2f, 0.4f, 0.6f, 1.0f);
        ShaderUniform.setFloat(shader, "u_fogStart", 20f);
        ShaderUniform.setFloat(shader, "u_fogEnd", 80f);

        // CSM shadow uniforms
        bindCsmUniforms(shader, csm);

        // Debug cascade visualization
        ShaderUniform.setInt(shader, "u_debugCascades", 0);

        TextureGenerator.getAtlasTexture().bind(0);

        cleanupOldMeshes();

        for (PhysicsBody body : manager.getActiveBodies()) {
            if (USE_FRUSTUM_CULLING && !frustumCuller.isBodyVisible(body)) {
                continue;
            }

            Mesh mesh = getOrBuildMesh(body);
            if (mesh != null) {
                tempMatrix.set(body.getTransform());
                ShaderUniform.setMatrix4(shader, "u_modelTrans", tempMatrix);

                // Compute normal matrix from model transform
                tempNormalMat.set(tempMatrix).inv().transpose();
                ShaderUniform.setMatrix3(shader, "u_normalMat", tempNormalMat);

                // PBR material (use dominant block type or default)
                int dominantBlockType = getDominantBlockType(body);
                PbrMaterial mat = pbrTable.get(dominantBlockType);
                ShaderUniform.setFloat(shader, "u_metallic", mat.metallic);
                ShaderUniform.setFloat(shader, "u_roughness", mat.roughness);
                ShaderUniform.setVector3(shader, "u_emission", mat.emission);
                ShaderUniform.setFloat(shader, "u_emissiveMask",
                    blockRegistry.hasTag(dominantBlockType, BlockTag.EMISSIVE) ? 1.0f : 0.0f);

                // Hit flash
                if (body.getHitTimer() > 0) {
                    float flashScale = body.getHitTimer() / 0.2f;
                    ShaderUniform.set4f(shader, "u_overrideColor", 1.0f, 0.5f, 0.5f, 0.6f * flashScale);
                } else {
                    ShaderUniform.set4f(shader, "u_overrideColor", 0f, 0f, 0f, 0f);
                }

                mesh.render(shader, GL20.GL_TRIANGLES);
            }
        }

        if (Constants.DEBUG) {
            ShaderProgram debugShader = shaderManager.get(ShaderPrograms.SELECTION);
            debugShader.bind();
            ShaderUniform.setMatrix4(debugShader, "u_projViewTrans", camera.combined);
            ShaderUniform.set4f(debugShader, "u_lineColor", 0f, 1f, 0f, 1f);

            for (PhysicsBody body : manager.getActiveBodies()) {
                Mesh dbgMesh = debugMeshes.get(body);
                if (dbgMesh != null) {
                    tempMatrix.set(body.getTransform());
                    ShaderUniform.setMatrix4(debugShader, "u_modelTrans", tempMatrix);
                    dbgMesh.render(debugShader, GL20.GL_LINES);
                }
            }
        }
    }

    /** Renders physics bodies into a velocity buffer for motion blur. */
    public void renderVelocity(Camera camera, com.badlogic.gdx.graphics.glutils.FrameBuffer velocityBuffer) {
        if (USE_FRUSTUM_CULLING) {
            frustumCuller.update(camera);
        }

        velocityBuffer.begin();
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        ShaderProgram shader = shaderManager.get(ShaderPrograms.PHYSICS_BODY);
        shader.bind();

        ShaderUniform.setMatrix4(shader, "u_projViewTrans", camera.combined);
        ShaderUniform.setInt(shader, "u_texture", 0);
        TextureGenerator.getAtlasTexture().bind(0);

        for (PhysicsBody body : manager.getActiveBodies()) {
            if (USE_FRUSTUM_CULLING && !frustumCuller.isBodyVisible(body)) {
                continue;
            }

            Vector3 vel = body.getLinearVelocity();
            if (vel.len() < Constants.BLUR_THRESHOLD)
                continue;

            Mesh mesh = getOrBuildMesh(body);

            ShaderUniform.setMatrix4(shader, "u_modelTrans", body.getTransform());

            Vector3 screenVel = body.getScreenVelocity(camera);
                // OPTIMIZED: Reuse velocity color vector instead of allocating per body.
                ShaderUniform.setVector3(shader, "u_ambientCol",
                    tempScreenVelColor.set(screenVel.x * 0.5f + 0.5f, screenVel.y * 0.5f + 0.5f, 0f));
            ShaderUniform.setVector3(shader, "u_sunColor", Vector3.Zero);

            mesh.render(shader, GL20.GL_TRIANGLES);
        }

        velocityBuffer.end();
    }

    /** Binds all CSM shadow map textures and light-space matrices to the shader. */
    private void bindCsmUniforms(ShaderProgram shader, CascadedShadowMap csm) {
        Matrix4[] lsm = csm.getLightSpaceMatrices();
        Texture[] shadowMaps = csm.getShadowMapTextures();
        float[] splits = csm.getCascadeSplits();

        for (int i = 0; i < CsmConstants.CASCADE_COUNT; i++) {
            int unit = i + 1;
            shadowMaps[i].bind(unit);
            ShaderUniform.setInt(shader, "u_shadowMap" + i, unit);
        }
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

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
    }

    /** Gets the most common block type in a body (for PBR material selection). */
    private int getDominantBlockType(PhysicsBody body) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int type : body.getVoxels().values()) {
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }
        int dominant = 1; // default stone
        int maxCount = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominant = entry.getKey();
            }
        }
        return dominant;
    }

    private Mesh getOrBuildMesh(PhysicsBody body) {
        Mesh mesh = bodyMeshes.get(body);
        if (mesh == null) {
            buildMesh(body);
            mesh = bodyMeshes.get(body);
        }
        return mesh;
    }

    private void cleanupOldMeshes() {
        List<PhysicsBody> active = manager.getActiveBodies();
        List<PhysicsBody> toRemove = new ArrayList<>();
        for (PhysicsBody b : bodyMeshes.keySet()) {
            if (!active.contains(b)) {
                toRemove.add(b);
            }
        }
        for (PhysicsBody b : toRemove) {
            bodyMeshes.remove(b).dispose();
            if (debugMeshes.containsKey(b))
                debugMeshes.remove(b).dispose();
        }
    }

    private void buildMesh(PhysicsBody body) {
        Map<Vector3, Integer> voxels = body.getVoxels();
        MeshBuilder builder = new MeshBuilder();

        BlockModelRegistry modelRegistry = BlockModelRegistry.getActive();
        SimpleCubeMesher cubeMesher = new SimpleCubeMesher(textureRegionResolver);
        BlockModelMesher modelMesher = modelRegistry == null ? null : new BlockModelMesher(textureRegionResolver, modelRegistry);

        for (Map.Entry<Vector3, Integer> entry : voxels.entrySet()) {
            Vector3 pos = entry.getKey();
            int x = (int) pos.x;
            int y = (int) pos.y;
            int z = (int) pos.z;
            int blockId = entry.getValue();

                BlockMesherStrategy strategy =
                    modelMesher != null && modelRegistry != null && modelRegistry.hasModel(blockId)
                        ? modelMesher : cubeMesher;

            strategy.emitBlock(
                    builder,
                    blockId,
                    x,
                    y,
                    z,
                    face -> hasSolidNeighbor(voxels, x, y, z, face));
        }

        if (builder.vertices.isEmpty())
            return;
        float[] vArray = new float[builder.vertices.size()];
        for (int i = 0; i < builder.vertices.size(); i++)
            vArray[i] = builder.vertices.get(i);
        short[] iArray = new short[builder.indices.size()];
        for (int i = 0; i < builder.indices.size(); i++)
            iArray[i] = builder.indices.get(i);

        Vector3 offset = body.getCenterOfMassOffset();
        for (int i = 0; i < vArray.length; i += 12) {
            vArray[i] -= offset.x;
            vArray[i + 1] -= offset.y;
            vArray[i + 2] -= offset.z;
        }

        Mesh mesh = new Mesh(true, vArray.length / 12, iArray.length,
                new VertexAttribute(Usage.Position, 3, "a_position"),
                new VertexAttribute(Usage.Normal, 3, "a_normal"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                new VertexAttribute(Usage.ColorUnpacked, 4, "a_color"));
        mesh.setVertices(vArray);
        mesh.setIndices(iArray);

        bodyMeshes.put(body, mesh);
    }

    private boolean hasSolidNeighbor(Map<Vector3, Integer> voxels, int x, int y, int z, FaceDirection face) {
        return switch (face) {
            case NORTH -> voxels.containsKey(new Vector3(x, y, z - 1));
            case SOUTH -> voxels.containsKey(new Vector3(x, y, z + 1));
            case EAST -> voxels.containsKey(new Vector3(x + 1, y, z));
            case WEST -> voxels.containsKey(new Vector3(x - 1, y, z));
            case TOP -> voxels.containsKey(new Vector3(x, y + 1, z));
            case BOTTOM -> voxels.containsKey(new Vector3(x, y - 1, z));
        };
    }

    public void dispose() {
        for (Mesh mesh : bodyMeshes.values())
            mesh.dispose();
        for (Mesh mesh : debugMeshes.values())
            mesh.dispose();
    }
}
