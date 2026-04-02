package com.zeal.voxel.render.ao;

/**
 * Stateless ambient occlusion calculator.
 * Computes per-vertex AO for a quad face using the smooth vertex AO algorithm.
 * All methods are pure functions of the voxel grid input.
 */
public final class AmbientOcclusionCalculator {

    private AmbientOcclusionCalculator() {}

    /**
     * Calculates AO values for the 4 vertices of a face quad.
     *
     * @param grid  the voxel grid to sample neighbours from
     * @param x     voxel x position
     * @param y     voxel y position
     * @param z     voxel z position
     * @param face  which face direction to calculate AO for
     * @return float[4] with AO values per vertex (0.0 = darkest, 1.0 = fully lit)
     */
    public static float[] calculateFaceAO(VoxelGrid grid, int x, int y, int z, FaceDirection face) {
        float[] ao = new float[4];

        int[] n = face.getNormal();
        // The face is on the voxel adjacent to (x,y,z) in the normal direction
        int fx = x + n[0];
        int fy = y + n[1];
        int fz = z + n[2];

        for (int vertex = 0; vertex < 4; vertex++) {
            int[] s1off = face.getSide1(vertex);
            int[] s2off = face.getSide2(vertex);
            int[] coff  = face.getCorner(vertex);

            int s1 = grid.isSolid(fx + s1off[0], fy + s1off[1], fz + s1off[2]) ? 1 : 0;
            int s2 = grid.isSolid(fx + s2off[0], fy + s2off[1], fz + s2off[2]) ? 1 : 0;
            int c  = grid.isSolid(fx + coff[0],  fy + coff[1],  fz + coff[2])  ? 1 : 0;

            if (s1 == 1 && s2 == 1) {
                ao[vertex] = 0.0f;
            } else {
                ao[vertex] = 1.0f - (s1 + s2 + c) / 3.0f;
            }

            // Clamp
            ao[vertex] = Math.max(0.0f, Math.min(1.0f, ao[vertex]));
        }

        return ao;
    }
}
