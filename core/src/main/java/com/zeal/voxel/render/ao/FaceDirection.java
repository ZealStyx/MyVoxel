package com.zeal.voxel.render.ao;

/** Enumerates the six face directions of a voxel with their normals and corner neighbour offsets. */
public enum FaceDirection {
    TOP(new int[]{0, 1, 0},
        new int[][]{
            // For each of the 4 vertices: {side1, side2, corner} offsets relative to the face voxel
            // v0 = (x, y+1, z)     back-left
            {-1, 0, 0}, {0, 0, -1}, {-1, 0, -1},
            // v1 = (x, y+1, z+1)   front-left
            {-1, 0, 0}, {0, 0, 1},  {-1, 0, 1},
            // v2 = (x+1, y+1, z+1) front-right
            {1, 0, 0},  {0, 0, 1},  {1, 0, 1},
            // v3 = (x+1, y+1, z)   back-right
            {1, 0, 0},  {0, 0, -1}, {1, 0, -1}
        }),

    BOTTOM(new int[]{0, -1, 0},
        new int[][]{
            {-1, 0, 0}, {0, 0, 1},  {-1, 0, 1},
            {-1, 0, 0}, {0, 0, -1}, {-1, 0, -1},
            {1, 0, 0},  {0, 0, -1}, {1, 0, -1},
            {1, 0, 0},  {0, 0, 1},  {1, 0, 1}
        }),

    NORTH(new int[]{0, 0, -1},
        new int[][]{
            {1, 0, 0},  {0, 1, 0},  {1, 1, 0},
            {1, 0, 0},  {0, -1, 0}, {1, -1, 0},
            {-1, 0, 0}, {0, -1, 0}, {-1, -1, 0},
            {-1, 0, 0}, {0, 1, 0},  {-1, 1, 0}
        }),

    SOUTH(new int[]{0, 0, 1},
        new int[][]{
            {-1, 0, 0}, {0, 1, 0},  {-1, 1, 0},
            {-1, 0, 0}, {0, -1, 0}, {-1, -1, 0},
            {1, 0, 0},  {0, -1, 0}, {1, -1, 0},
            {1, 0, 0},  {0, 1, 0},  {1, 1, 0}
        }),

    EAST(new int[]{1, 0, 0},
        new int[][]{
            {0, 1, 0},  {0, 0, -1}, {0, 1, -1},
            {0, -1, 0}, {0, 0, -1}, {0, -1, -1},
            {0, -1, 0}, {0, 0, 1},  {0, -1, 1},
            {0, 1, 0},  {0, 0, 1},  {0, 1, 1}
        }),

    WEST(new int[]{-1, 0, 0},
        new int[][]{
            {0, 1, 0},  {0, 0, 1},  {0, 1, 1},
            {0, -1, 0}, {0, 0, 1},  {0, -1, 1},
            {0, -1, 0}, {0, 0, -1}, {0, -1, -1},
            {0, 1, 0},  {0, 0, -1}, {0, 1, -1}
        });

    private final int[] normal;
    private final int[][] cornerOffsets; // 4 vertices × 3 offsets (side1, side2, corner)

    FaceDirection(int[] normal, int[][] cornerOffsets) {
        this.normal = normal;
        this.cornerOffsets = cornerOffsets;
    }

    public int[] getNormal() {
        return normal;
    }

    /** Returns the 3 neighbour offsets for the given vertex index (0–3). */
    public int[] getSide1(int vertex) {
        return cornerOffsets[vertex * 3];
    }

    public int[] getSide2(int vertex) {
        return cornerOffsets[vertex * 3 + 1];
    }

    public int[] getCorner(int vertex) {
        return cornerOffsets[vertex * 3 + 2];
    }

    public String yamlKey() {
        return switch (this) {
            case TOP -> "top";
            case BOTTOM -> "bottom";
            case NORTH -> "north";
            case SOUTH -> "south";
            case EAST -> "east";
            case WEST -> "west";
        };
    }
}
