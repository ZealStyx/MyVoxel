package com.zeal.voxel.world;

import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.render.ao.FaceDirection;

/**
 * Single source of truth for all face vertex positions and winding order.
 * Quads use triangle order (v0,v1,v2) and (v0,v2,v3).
 * CCW when viewed from outside.
 */
public class FaceGeometry {

    public static final float[][] TOP_VERTS = {
        {0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}
    };
    public static final float[][] BOTTOM_VERTS = {
        {0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}
    };
    public static final float[][] NORTH_VERTS = {
        {0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}
    };
    public static final float[][] SOUTH_VERTS = {
        {1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {0, 0, 1}
    };
    public static final float[][] EAST_VERTS = {
        {1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}
    };
    public static final float[][] WEST_VERTS = {
        {0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {0, 0, 0}
    };

    public static float[][] getVerts(FaceDirection fd) {
        switch (fd) {
            case TOP: return TOP_VERTS;
            case BOTTOM: return BOTTOM_VERTS;
            case NORTH: return NORTH_VERTS;
            case SOUTH: return SOUTH_VERTS;
            case EAST: return EAST_VERTS;
            case WEST: return WEST_VERTS;
            default: throw new IllegalArgumentException("Unknown face direction");
        }
    }

    public static float[] getNormal(FaceDirection fd) {
        int[] n = fd.getNormal();
        return new float[] { n[0], n[1], n[2] };
    }

    public static void assertWindingCorrect() {
        for (FaceDirection fd : FaceDirection.values()) {
            float[][] verts = getVerts(fd);
            Vector3 v0 = new Vector3(verts[0]);
            Vector3 v1 = new Vector3(verts[1]);
            Vector3 v2 = new Vector3(verts[2]);
            
            Vector3 edge1 = new Vector3(v1).sub(v0);
            Vector3 edge2 = new Vector3(v2).sub(v0);
            Vector3 cross = edge1.crs(edge2).nor();
            
            float[] expectedArr = getNormal(fd);
            Vector3 expected = new Vector3(expectedArr[0], expectedArr[1], expectedArr[2]);
            
            assert cross.dot(expected) > 0.99f : "Wrong winding on face " + fd;
        }
    }
}
