package com.zeal.voxel.render.particle;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.util.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits block-break particle bursts. Maintains a ring buffer of particles.
 * Does NOT render — that is ParticleRenderer's responsibility.
 */
public class BlockBreakEmitter {
    private final List<BlockParticle> particles = new ArrayList<>();

    /** Spawns 6–12 particles at the given world position for the given block type. */
    public void emit(Vector3 worldPos, int blockType) {
        int count = MathUtils.random(6, 12);

        for (int i = 0; i < count; i++) {
            // Random outward direction
            float theta = MathUtils.random(0f, MathUtils.PI2);
            float phi = MathUtils.random(0f, MathUtils.PI * 0.5f);
            float speed = MathUtils.random(2f, 6f);

            Vector3 vel = new Vector3(
                MathUtils.cos(theta) * MathUtils.sin(phi) * speed,
                MathUtils.cos(phi) * speed,
                MathUtils.sin(theta) * MathUtils.sin(phi) * speed
            );

            float size = MathUtils.random(0.2f, 0.4f);
            float maxLife = MathUtils.random(0.4f, 0.8f);

            Quaternion rotation = new Quaternion().setEulerAngles(
                MathUtils.random(360f), MathUtils.random(360f), MathUtils.random(360f)
            );

            Vector3 angularVel = new Vector3(
                MathUtils.random(-180f, 180f),
                MathUtils.random(-180f, 180f),
                MathUtils.random(-180f, 180f)
            );

            BlockParticle p = new BlockParticle(worldPos, vel, size, maxLife, rotation, angularVel, blockType);
            particles.add(p);
        }

        // Ring buffer: discard oldest if over max
        while (particles.size() > Constants.MAX_PARTICLES) {
            particles.remove(0);
        }
    }

    /** Updates all live particles: gravity, damping, aging. Removes dead ones. */
    public void update(float delta) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            BlockParticle p = particles.get(i);

            p.position.add(p.velocity.x * delta, p.velocity.y * delta, p.velocity.z * delta);
            p.velocity.y += Constants.PARTICLE_GRAVITY * delta;

            // Angular damping
            p.angularVel.scl(1f - 5f * delta);

            // Shrink as it ages
            p.life += delta / p.maxLife;
            p.size *= (1f - p.life * delta);

            if (p.isDead()) {
                particles.remove(i);
            }
        }
    }

    public List<BlockParticle> getLiveParticles() {
        return particles;
    }
}
