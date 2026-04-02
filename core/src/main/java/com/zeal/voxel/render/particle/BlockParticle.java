package com.zeal.voxel.render.particle;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/** Pure data class representing a single block-break particle fragment. */
public class BlockParticle {
    public final Vector3 position;
    public final Vector3 velocity;
    public float size;
    public float life;
    public final float maxLife;
    public final Quaternion rotation;
    public final Vector3 angularVel;
    public final int blockType;

    public BlockParticle(Vector3 position, Vector3 velocity, float size, float maxLife, 
                         Quaternion rotation, Vector3 angularVel, int blockType) {
        this.position = new Vector3(position);
        this.velocity = new Vector3(velocity);
        this.size = size;
        this.life = 0f;
        this.maxLife = maxLife;
        this.rotation = new Quaternion(rotation);
        this.angularVel = new Vector3(angularVel);
        this.blockType = blockType;
    }

    public boolean isDead() {
        return life >= 1.0f;
    }
}
