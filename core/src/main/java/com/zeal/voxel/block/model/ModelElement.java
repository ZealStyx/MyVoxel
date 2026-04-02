package com.zeal.voxel.block.model;

import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.render.ao.FaceDirection;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class ModelElement {
    public final String name;
    public final Vector3 from;
    public final Vector3 to;
    public final ElementRotation rotation;
    public final Map<FaceDirection, ModelFace> faces;

    public ModelElement(String name,
                        Vector3 from,
                        Vector3 to,
                        ElementRotation rotation,
                        Map<FaceDirection, ModelFace> faces) {
        this.name = name;
        this.from = new Vector3(from);
        this.to = new Vector3(to);
        this.rotation = rotation;
        this.faces = Collections.unmodifiableMap(new EnumMap<>(faces));
    }

    public boolean isFullUnitFace(FaceDirection face, float eps) {
        return switch (face) {
            case NORTH -> near(from.z, 0f, eps) && spansUnit(from.x, to.x, eps) && spansUnit(from.y, to.y, eps);
            case SOUTH -> near(to.z, 1f, eps) && spansUnit(from.x, to.x, eps) && spansUnit(from.y, to.y, eps);
            case EAST -> near(to.x, 1f, eps) && spansUnit(from.z, to.z, eps) && spansUnit(from.y, to.y, eps);
            case WEST -> near(from.x, 0f, eps) && spansUnit(from.z, to.z, eps) && spansUnit(from.y, to.y, eps);
            case TOP -> near(to.y, 1f, eps) && spansUnit(from.x, to.x, eps) && spansUnit(from.z, to.z, eps);
            case BOTTOM -> near(from.y, 0f, eps) && spansUnit(from.x, to.x, eps) && spansUnit(from.z, to.z, eps);
        };
    }

    private boolean spansUnit(float min, float max, float eps) {
        return near(min, 0f, eps) && near(max, 1f, eps);
    }

    private boolean near(float a, float b, float eps) {
        return Math.abs(a - b) <= eps;
    }
}
