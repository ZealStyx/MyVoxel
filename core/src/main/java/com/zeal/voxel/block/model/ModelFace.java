package com.zeal.voxel.block.model;

public class ModelFace {
    public final float u1;
    public final float v1;
    public final float u2;
    public final float v2;
    public final String textureRef;

    public ModelFace(float u1, float v1, float u2, float v2, String textureRef) {
        this.u1 = u1;
        this.v1 = v1;
        this.u2 = u2;
        this.v2 = v2;
        this.textureRef = textureRef;
    }
}
