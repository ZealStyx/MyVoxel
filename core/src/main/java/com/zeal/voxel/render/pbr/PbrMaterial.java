package com.zeal.voxel.render.pbr;

import com.badlogic.gdx.math.Vector3;

/** Immutable PBR material properties for a single block type. */
public class PbrMaterial {
    public final float metallic;    // 0 = dielectric, 1 = metal
    public final float roughness;   // 0 = mirror, 1 = fully rough
    public final float ao;          // base ambient occlusion (baked AO adds on top)
    public final Vector3 emission;  // emissive color (black = no emission)

    public PbrMaterial(float metallic, float roughness, float ao, Vector3 emission) {
        this.metallic = metallic;
        this.roughness = roughness;
        this.ao = ao;
        this.emission = new Vector3(emission);
    }

    public PbrMaterial(float metallic, float roughness) {
        this(metallic, roughness, 1.0f, Vector3.Zero);
    }
}
