package com.zeal.voxel.block.model;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.block.TextureRegionResolver;
import com.zeal.voxel.render.ao.FaceDirection;
import com.zeal.voxel.world.FaceGeometry;

public class SimpleCubeMesher implements BlockMesherStrategy {
    private static final float DEFAULT_AO = 1.0f;

    private final TextureRegionResolver textureResolver;

    public SimpleCubeMesher(TextureRegionResolver textureResolver) {
        this.textureResolver = textureResolver;
    }

    @Override
    public void emitBlock(MeshBuilder builder,
                          int blockId,
                          int worldX,
                          int worldY,
                          int worldZ,
                          NeighbourSolidChecker neighbours) {
        for (FaceDirection face : FaceDirection.values()) {
            if (neighbours.isSolid(face)) {
                continue;
            }

            TextureRegion region = textureResolver.resolve(blockId, face);
            float[][] verts = FaceGeometry.getVerts(face);
            int[] n = face.getNormal();
            Vector3 normal = new Vector3(n[0], n[1], n[2]);

            Vector3 v0 = new Vector3(worldX + verts[0][0], worldY + verts[0][1], worldZ + verts[0][2]);
            Vector3 v1 = new Vector3(worldX + verts[1][0], worldY + verts[1][1], worldZ + verts[1][2]);
            Vector3 v2 = new Vector3(worldX + verts[2][0], worldY + verts[2][1], worldZ + verts[2][2]);
            Vector3 v3 = new Vector3(worldX + verts[3][0], worldY + verts[3][1], worldZ + verts[3][2]);

            builder.emitQuad(v0, v1, v2, v3, normal, region.getU(), region.getV(), region.getU2(), region.getV2(), DEFAULT_AO);
        }
    }
}
