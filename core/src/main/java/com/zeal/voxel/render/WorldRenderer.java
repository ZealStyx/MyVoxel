package com.zeal.voxel.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.zeal.voxel.block.BlockRegistry;
import com.zeal.voxel.block.TextureRegionResolver;
import com.zeal.voxel.render.culling.FrustumCuller;
import com.zeal.voxel.render.culling.OcclusionGraph;
import com.zeal.voxel.render.pbr.PbrMaterialTable;
import com.zeal.voxel.render.shader.ShaderManager;
import com.zeal.voxel.render.shader.ShaderPrograms;
import com.zeal.voxel.render.shader.ShaderUniform;
import com.zeal.voxel.render.shadow.CascadedShadowMap;
import com.zeal.voxel.world.BlockColumn;
import com.zeal.voxel.world.ColumnMesh;
import com.zeal.voxel.world.ColumnMeshResult;
import com.zeal.voxel.world.ColumnMesher;
import com.zeal.voxel.world.ColumnStreamer;
import com.zeal.voxel.world.WorldGrid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorldRenderer {
    private static final boolean USE_FRUSTUM_CULLING = true;
    private static final float TRANSPARENT_SORT_EPSILON = 0.0001f;
    private static final Vector3 SUN_DIR = new Vector3(0.3f, 1.0f, 0.4f).nor();
    private static final Vector3 SUN_COLOR = new Vector3(1.0f, 0.98f, 0.9f);
    private static final Vector3 AMBIENT_COLOR = new Vector3(0.6f, 0.6f, 0.65f);
    private static final float CULLING_LOG_INTERVAL_SECONDS = 5f;

    private final WorldGrid world;
    private final ColumnStreamer columnStreamer;
    private final ShaderManager shaderManager;
    private final BlockRegistry blockRegistry;
    private final TextureRegionResolver textureRegionResolver;
    private final FrustumCuller frustumCuller;
    private final OcclusionGraph occlusionGraph;

    private final Map<Long, ColumnRenderMeshes> columnMeshes = new HashMap<>();
    private final Set<Long> occludedColumnsThisFrame = new HashSet<>();
    private final Matrix4 identityModel = new Matrix4();
    private final List<TransparentBatch> transparentBatches = new ArrayList<>();
    private final Vector3 tmpBoundsCenter = new Vector3();
    private final Vector3 tmpBoundsDimensions = new Vector3();

    private int transparentBatchCount;
    private float cullingLogTimer;
    private int columnsRendered;
    private int columnsTotal;
    private int columnsOccluded;

    private static class MeshEntry {
        private final Mesh mesh;
        private final BoundingBox bounds;

        private MeshEntry(Mesh mesh, BoundingBox bounds) {
            this.mesh = mesh;
            this.bounds = new BoundingBox(bounds);
        }
    }

    private static class ColumnRenderMeshes {
        private final List<MeshEntry> opaque = new ArrayList<>();
        private final List<MeshEntry> transparent = new ArrayList<>();

        private boolean isEmpty() {
            return opaque.isEmpty() && transparent.isEmpty();
        }
    }

    private static class TransparentBatch {
        private MeshEntry meshEntry;
        private float sortKey;

        private void set(MeshEntry meshEntry, float sortKey) {
            this.meshEntry = meshEntry;
            this.sortKey = sortKey;
        }
    }

    public WorldRenderer(
            WorldGrid world,
            ColumnStreamer columnStreamer,
            ShaderManager shaderManager,
            BlockRegistry blockRegistry,
            TextureRegionResolver textureRegionResolver,
            FrustumCuller frustumCuller,
            OcclusionGraph occlusionGraph) {
        this.world = world;
        this.columnStreamer = columnStreamer;
        this.shaderManager = shaderManager;
        this.blockRegistry = blockRegistry;
        this.textureRegionResolver = textureRegionResolver;
        this.frustumCuller = frustumCuller;
        this.occlusionGraph = occlusionGraph;

        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    }

    public void renderDepth(Camera sunCamera) {
        rebuildDirtyMeshes();

        ShaderProgram shader = shaderManager.get(ShaderPrograms.SHADOW_DEPTH);
        shader.bind();
        ShaderUniform.setMatrix4(shader, "u_lightSpaceMVP", sunCamera.combined);

        for (ColumnRenderMeshes meshes : columnMeshes.values()) {
            for (MeshEntry meshEntry : meshes.opaque) {
                meshEntry.mesh.render(shader, GL20.GL_TRIANGLES);
            }
        }
    }

    public void render(Camera camera, CascadedShadowMap csm, PbrMaterialTable pbrTable) {
        rebuildDirtyMeshes();
        if (USE_FRUSTUM_CULLING) {
            frustumCuller.update(camera);
        }
        if (occlusionGraph != null) {
            occlusionGraph.beginFrame(camera.position, columnStreamer.getLoadedColumns(), com.zeal.voxel.util.Constants.COLUMN_SIZE);
        }

        ShaderProgram shader = shaderManager.get(ShaderPrograms.VOXEL_PBR);
        shader.bind();

        ShaderUniform.setMatrix4(shader, "u_projViewTrans", camera.combined);
        ShaderUniform.setMatrix4(shader, "u_modelTrans", identityModel);
        ShaderUniform.setMatrix4(shader, "u_viewTrans", camera.view);
        ShaderUniform.setInt(shader, "u_texture", 0);

        ShaderUniform.setVector3(shader, "u_sunDir", SUN_DIR);
        ShaderUniform.setVector3(shader, "u_sunColor", SUN_COLOR);
        ShaderUniform.setVector3(shader, "u_ambientColor", AMBIENT_COLOR);
        ShaderUniform.setVector3(shader, "u_camPos", camera.position);

        ShaderUniform.set4f(shader, "u_fogColor", 0.2f, 0.4f, 0.6f, 1.0f);
        ShaderUniform.setFloat(shader, "u_fogStart", 20f);
        ShaderUniform.setFloat(shader, "u_fogEnd", 80f);
        ShaderUniform.set4f(shader, "u_overrideColor", 0f, 0f, 0f, 0f);

        TextureGenerator.getAtlasTexture().bind(0);

        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDepthMask(true);
        columnsRendered = 0;
        columnsTotal = 0;
        columnsOccluded = 0;
        occludedColumnsThisFrame.clear();
        renderWorldPass(shader, false, camera);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDepthMask(false);
        renderWorldPass(shader, true, camera);

        cullingLogTimer += Gdx.graphics.getDeltaTime();
        if (cullingLogTimer >= CULLING_LOG_INTERVAL_SECONDS) {
            cullingLogTimer = 0f;
            Gdx.app.log("WorldRenderer", "columnsRendered=" + columnsRendered + "/" + columnsTotal
                    + " (occluded: " + columnsOccluded + ")");
        }

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderWorldPass(ShaderProgram shader, boolean transparentPass, Camera camera) {
        if (transparentPass) {
            transparentBatchCount = 0;
        }

        for (Map.Entry<Long, BlockColumn> entry : columnStreamer.getLoadedColumns().entrySet()) {
            long key = entry.getKey();
            if (!transparentPass) {
                columnsTotal++;
            }

            ColumnRenderMeshes meshes = columnMeshes.get(key);
            if (meshes == null) {
                continue;
            }

            if (!transparentPass && occlusionGraph != null && occlusionGraph.isColumnOccluded(key)) {
                columnsOccluded++;
                occludedColumnsThisFrame.add(key);
                continue;
            }

            if (transparentPass && occludedColumnsThisFrame.contains(key)) {
                continue;
            }

            boolean columnRenderedInOpaquePass = false;

            List<MeshEntry> active = transparentPass ? meshes.transparent : meshes.opaque;
            for (MeshEntry meshEntry : active) {
                if (USE_FRUSTUM_CULLING && !isBoundsVisible(camera, meshEntry.bounds)) {
                    continue;
                }

                if (transparentPass) {
                    meshEntry.bounds.getCenter(tmpBoundsCenter);
                    float dx = tmpBoundsCenter.x - camera.position.x;
                    float dy = tmpBoundsCenter.y - camera.position.y;
                    float dz = tmpBoundsCenter.z - camera.position.z;
                    float sortKey = dx * camera.direction.x + dy * camera.direction.y + dz * camera.direction.z;

                    TransparentBatch batch;
                    if (transparentBatchCount < transparentBatches.size()) {
                        batch = transparentBatches.get(transparentBatchCount);
                    } else {
                        batch = new TransparentBatch();
                        transparentBatches.add(batch);
                    }
                    batch.set(meshEntry, sortKey);
                    transparentBatchCount++;
                    continue;
                }

                if (!columnRenderedInOpaquePass) {
                    columnsRendered++;
                    columnRenderedInOpaquePass = true;
                }

                applyDefaultMaterial(shader);
                meshEntry.mesh.render(shader, GL20.GL_TRIANGLES);
            }
        }

        if (transparentPass && transparentBatchCount > 0) {
            List<TransparentBatch> activeBatches = transparentBatches.subList(0, transparentBatchCount);
            activeBatches.sort(Comparator.comparingDouble((TransparentBatch b) -> b.sortKey).reversed());

            float previousKey = Float.NaN;
            for (TransparentBatch batch : activeBatches) {
                if (!Float.isNaN(previousKey) && Math.abs(previousKey - batch.sortKey) < TRANSPARENT_SORT_EPSILON) {
                    // Keep stable order when two batches project to nearly equal depth.
                }
                previousKey = batch.sortKey;
                applyDefaultMaterial(shader);
                batch.meshEntry.mesh.render(shader, GL20.GL_TRIANGLES);
            }
        }
    }

    private void applyDefaultMaterial(ShaderProgram shader) {
        ShaderUniform.setFloat(shader, "u_metallic", 0.05f);
        ShaderUniform.setFloat(shader, "u_roughness", 0.9f);
        ShaderUniform.setVector3(shader, "u_emission", Vector3.Zero);
        ShaderUniform.setFloat(shader, "u_emissiveMask", 0.0f);
    }

    private boolean isBoundsVisible(Camera camera, BoundingBox bounds) {
        bounds.getCenter(tmpBoundsCenter);
        bounds.getDimensions(tmpBoundsDimensions);
        float halfX = tmpBoundsDimensions.x * 0.5f;
        float halfY = tmpBoundsDimensions.y * 0.5f;
        float halfZ = tmpBoundsDimensions.z * 0.5f;
        return camera.frustum.boundsInFrustum(tmpBoundsCenter.x, tmpBoundsCenter.y, tmpBoundsCenter.z, halfX, halfY, halfZ);
    }

    private void rebuildDirtyMeshes() {
        columnMeshes.entrySet().removeIf(entry -> {
            if (columnStreamer.getLoadedColumns().containsKey(entry.getKey())) {
                return false;
            }
            disposeColumnMeshes(entry.getValue());
            return true;
        });

        for (Map.Entry<Long, BlockColumn> entry : columnStreamer.getLoadedColumns().entrySet()) {
            long key = entry.getKey();
            BlockColumn column = entry.getValue();
            if (!column.isDirty() && columnMeshes.containsKey(key)) {
                continue;
            }

            ColumnRenderMeshes old = columnMeshes.remove(key);
            if (old != null) {
                disposeColumnMeshes(old);
            }

            ColumnMeshResult built = ColumnMesher.meshColumn(column, world, blockRegistry, textureRegionResolver);
            ColumnRenderMeshes renderMeshes = new ColumnRenderMeshes();

            for (ColumnMesh mesh : built.opaqueMeshes) {
                if (mesh.opaqueGeometry == null || mesh.opaqueGeometry.indices.length == 0) {
                    continue;
                }
                renderMeshes.opaque.add(new MeshEntry(createMesh(mesh.opaqueGeometry), mesh.bounds));
            }

            for (ColumnMesh mesh : built.transparentMeshes) {
                if (mesh.transparentGeometry == null || mesh.transparentGeometry.indices.length == 0) {
                    continue;
                }
                renderMeshes.transparent.add(new MeshEntry(createMesh(mesh.transparentGeometry), mesh.bounds));
            }

            if (!renderMeshes.isEmpty()) {
                columnMeshes.put(key, renderMeshes);
            }
            column.setDirty(false);
        }
    }

    private Mesh createMesh(ColumnMesh.MeshGeometry geometry) {
        Mesh mesh = new Mesh(true, geometry.vertices.length / 12, geometry.indices.length,
                new VertexAttribute(Usage.Position, 3, "a_position"),
                new VertexAttribute(Usage.Normal, 3, "a_normal"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                new VertexAttribute(Usage.ColorUnpacked, 4, "a_color"));
        mesh.setVertices(geometry.vertices);
        mesh.setIndices(geometry.indices);
        return mesh;
    }

    private void disposeColumnMeshes(ColumnRenderMeshes meshes) {
        for (MeshEntry entry : meshes.opaque) {
            entry.mesh.dispose();
        }
        for (MeshEntry entry : meshes.transparent) {
            entry.mesh.dispose();
        }
        meshes.opaque.clear();
        meshes.transparent.clear();
    }

    public void dispose() {
        for (ColumnRenderMeshes meshes : columnMeshes.values()) {
            disposeColumnMeshes(meshes);
        }
        columnMeshes.clear();
    }
}
