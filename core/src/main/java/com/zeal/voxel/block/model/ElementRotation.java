package com.zeal.voxel.block.model;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public class ElementRotation {
    public final float angleDeg;
    public final String axis;
    public final Vector3 origin;
    public final boolean rescale;

    public ElementRotation(float angleDeg, String axis, Vector3 origin, boolean rescale) {
        this.angleDeg = angleDeg;
        this.axis = axis;
        this.origin = new Vector3(origin);
        this.rescale = rescale;
    }

    public Matrix4 toMatrix4() {
        Vector3 axisVec = axisVector();
        return new Matrix4()
                .translate(origin)
                .rotate(axisVec, angleDeg)
                .translate(-origin.x, -origin.y, -origin.z);
    }

    public void apply(Vector3 vec) {
        vec.mul(toMatrix4());
    }

    public void rotateNormal(Vector3 normal) {
        normal.rotate(axisVector(), angleDeg);
    }

    private Vector3 axisVector() {
        return switch (axis.toLowerCase()) {
            case "x" -> new Vector3(1f, 0f, 0f);
            case "z" -> new Vector3(0f, 0f, 1f);
            default -> new Vector3(0f, 1f, 0f);
        };
    }
}
