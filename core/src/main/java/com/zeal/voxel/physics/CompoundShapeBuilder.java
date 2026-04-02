package com.zeal.voxel.physics;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCompoundShape;
import com.zeal.voxel.render.ao.VoxelGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless greedy-merge compound shape builder.
 * Replaces naive one-btBoxShape-per-voxel approach with a minimal set
 * of non-overlapping axis-aligned boxes covering all solid voxels.
 *
 * Algorithm:
 *   1. For each Y layer, greedy-merge XZ slices into rectangles.
 *   2. Merge adjacent Y layers with identical XZ footprint into taller boxes.
 */
public final class CompoundShapeBuilder {

    /** An intermediate merged box before Y-merge. */
    private static final class MergedBox {
        int x0, y0, z0;
        int x1, y1, z1; // inclusive

        MergedBox(int x0, int y0, int z0, int x1, int y1, int z1) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0;
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
        }

        boolean canMergeY(MergedBox other) {
            return other.y0 == this.y1 + 1
                && other.x0 == this.x0 && other.x1 == this.x1
                && other.z0 == this.z0 && other.z1 == this.z1;
        }
    }

    private CompoundShapeBuilder() {}

    /**
     * Builds a compound shape from a voxel grid using greedy merge.
     * The grid is sampled from (0,0,0) to (sizeX-1, sizeY-1, sizeZ-1).
     *
     * @param grid    VoxelGrid to sample
     * @param sizeX   X dimension of the grid
     * @param sizeY   Y dimension of the grid
     * @param sizeZ   Z dimension of the grid
     * @param offset  offset to subtract from each box position (e.g. center of mass)
     * @return a NEW btCompoundShape — caller is responsible for disposing
     */
    public static btCompoundShape build(VoxelGrid grid, int sizeX, int sizeY, int sizeZ, Vector3 offset) {
        // Phase 1: greedy merge per Y layer
        List<MergedBox> boxes = new ArrayList<>();

        for (int y = 0; y < sizeY; y++) {
            boolean[][] visited = new boolean[sizeX][sizeZ];

            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    if (visited[x][z] || !grid.isSolid(x, y, z)) continue;

                    // Expand +X
                    int x1 = x;
                    while (x1 + 1 < sizeX && !visited[x1 + 1][z] && grid.isSolid(x1 + 1, y, z)) {
                        x1++;
                    }

                    // Expand +Z — entire row [x..x1] must be solid and unvisited
                    int z1 = z;
                    outer:
                    while (z1 + 1 < sizeZ) {
                        for (int xi = x; xi <= x1; xi++) {
                            if (visited[xi][z1 + 1] || !grid.isSolid(xi, y, z1 + 1)) {
                                break outer;
                            }
                        }
                        z1++;
                    }

                    // Mark visited
                    for (int xi = x; xi <= x1; xi++) {
                        for (int zi = z; zi <= z1; zi++) {
                            visited[xi][zi] = true;
                        }
                    }

                    boxes.add(new MergedBox(x, y, z, x1, y, z1));
                }
            }
        }

        // Phase 2: merge adjacent Y layers with identical XZ footprint
        boxes = mergeYLayers(boxes);

        // Phase 3: build btCompoundShape
        btCompoundShape compound = new btCompoundShape();
        Matrix4 childTransform = new Matrix4();

        for (MergedBox box : boxes) {
            float halfX = (box.x1 - box.x0 + 1) * 0.5f;
            float halfY = (box.y1 - box.y0 + 1) * 0.5f;
            float halfZ = (box.z1 - box.z0 + 1) * 0.5f;

            float cx = box.x0 + halfX - offset.x;
            float cy = box.y0 + halfY - offset.y;
            float cz = box.z0 + halfZ - offset.z;

            btBoxShape boxShape = new btBoxShape(new Vector3(halfX, halfY, halfZ));
            childTransform.setToTranslation(cx, cy, cz);
            compound.addChildShape(childTransform, boxShape);
        }

        return compound;
    }

    /**
     * Overload for physics bodies that use voxel cell centers offset by +0.5.
     * Center of mass offset is applied internally.
     */
    public static btCompoundShape buildForBody(VoxelGrid grid, int sizeX, int sizeY, int sizeZ, Vector3 centerOfMassOffset) {
        return build(grid, sizeX, sizeY, sizeZ, centerOfMassOffset);
    }

    private static List<MergedBox> mergeYLayers(List<MergedBox> input) {
        if (input.isEmpty()) return input;

        // Sort by x0, z0, y0 for deterministic merging
        input.sort((a, b) -> {
            if (a.x0 != b.x0) return Integer.compare(a.x0, b.x0);
            if (a.z0 != b.z0) return Integer.compare(a.z0, b.z0);
            return Integer.compare(a.y0, b.y0);
        });

        List<MergedBox> result = new ArrayList<>();
        boolean[] merged = new boolean[input.size()];

        for (int i = 0; i < input.size(); i++) {
            if (merged[i]) continue;

            MergedBox current = input.get(i);
            // Create a mutable copy
            MergedBox acc = new MergedBox(current.x0, current.y0, current.z0,
                                          current.x1, current.y1, current.z1);

            for (int j = i + 1; j < input.size(); j++) {
                if (merged[j]) continue;
                MergedBox candidate = input.get(j);
                if (acc.canMergeY(candidate)) {
                    acc.y1 = candidate.y1;
                    merged[j] = true;
                }
            }

            result.add(acc);
        }

        return result;
    }
}
