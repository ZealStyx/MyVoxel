package com.zeal.voxel.block.model;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.block.TextureRegionResolver;
import com.zeal.voxel.render.ao.FaceDirection;

import java.util.Map;

public class BlockModelMesher implements BlockMesherStrategy {
    private static final float FULL_FACE_EPSILON = 0.001f;
    private static final float DEFAULT_AO = 1.0f;

    private final TextureRegionResolver textureResolver;
    private final BlockModelRegistry modelRegistry;

    public BlockModelMesher(TextureRegionResolver textureResolver, BlockModelRegistry modelRegistry) {
        this.textureResolver = textureResolver;
        this.modelRegistry = modelRegistry;
    }

    @Override
    public void emitBlock(MeshBuilder builder,
                          int blockId,
                          int worldX,
                          int worldY,
                          int worldZ,
                          NeighbourSolidChecker neighbours) {
        BlockModel model = modelRegistry.get(blockId);
        if (model == null) {
            return;
        }

        for (ModelElement element : model.elements) {
            for (Map.Entry<FaceDirection, ModelFace> entry : element.faces.entrySet()) {
                FaceDirection faceDir = entry.getKey();
                ModelFace face = entry.getValue();

                boolean fullUnitFace = element.isFullUnitFace(faceDir, FULL_FACE_EPSILON);
                if (fullUnitFace && neighbours.isSolid(faceDir)) {
                    continue;
                }

                String texturePath = model.resolveTexturePath(face.textureRef);
                TextureRegion region = textureResolver.resolvePath(texturePath);
                if (region == null) {
                    throw new ModelLoadException("Model file " + model.sourceFile
                            + " could not resolve atlas texture: " + texturePath);
                }

                Vector3[] corners = buildFaceCorners(element.from, element.to, faceDir);
                if (element.rotation != null) {
                    for (Vector3 c : corners) {
                        element.rotation.apply(c);
                    }
                }

                for (Vector3 c : corners) {
                    c.add(worldX, worldY, worldZ);
                }

                Vector3 normal = faceNormal(faceDir);
                if (element.rotation != null) {
                    element.rotation.rotateNormal(normal);
                    normal.nor();
                }

                float u0 = mapU(face.u1, model.textureWidth, region);
                float v0 = mapV(face.v1, model.textureHeight, region);
                float u1 = mapU(face.u2, model.textureWidth, region);
                float v1 = mapV(face.v2, model.textureHeight, region);

                builder.emitQuad(corners[0], corners[1], corners[2], corners[3], normal, u0, v0, u1, v1, DEFAULT_AO);
            }
        }
    }

    private Vector3[] buildFaceCorners(Vector3 from, Vector3 to, FaceDirection face) {
        return switch (face) {
            case NORTH -> new Vector3[]{
                    new Vector3(from.x, from.y, from.z),
                    new Vector3(from.x, to.y, from.z),
                    new Vector3(to.x, to.y, from.z),
                    new Vector3(to.x, from.y, from.z)
            };
            case SOUTH -> new Vector3[]{
                    new Vector3(to.x, from.y, to.z),
                    new Vector3(to.x, to.y, to.z),
                    new Vector3(from.x, to.y, to.z),
                    new Vector3(from.x, from.y, to.z)
            };
            case EAST -> new Vector3[]{
                    new Vector3(to.x, from.y, from.z),
                    new Vector3(to.x, to.y, from.z),
                    new Vector3(to.x, to.y, to.z),
                    new Vector3(to.x, from.y, to.z)
            };
            case WEST -> new Vector3[]{
                    new Vector3(from.x, from.y, to.z),
                    new Vector3(from.x, to.y, to.z),
                    new Vector3(from.x, to.y, from.z),
                    new Vector3(from.x, from.y, from.z)
            };
            case TOP -> new Vector3[]{
                    new Vector3(from.x, to.y, from.z),
                    new Vector3(from.x, to.y, to.z),
                    new Vector3(to.x, to.y, to.z),
                    new Vector3(to.x, to.y, from.z)
            };
            case BOTTOM -> new Vector3[]{
                    new Vector3(from.x, from.y, from.z),
                    new Vector3(to.x, from.y, from.z),
                    new Vector3(to.x, from.y, to.z),
                    new Vector3(from.x, from.y, to.z)
            };
        };
    }

    private Vector3 faceNormal(FaceDirection face) {
        int[] n = face.getNormal();
        return new Vector3(n[0], n[1], n[2]);
    }

    private float mapU(float pixelU, int texWidth, TextureRegion region) {
        float t = pixelU / texWidth;
        return region.getU() + t * (region.getU2() - region.getU());
    }

    private float mapV(float pixelV, int texHeight, TextureRegion region) {
        float t = pixelV / texHeight;
        return region.getV() + t * (region.getV2() - region.getV());
    }
}
