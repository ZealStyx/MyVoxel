package com.zeal.voxel.block;

import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.render.ao.FaceDirection;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class BlockDefinition {
    public final String id;
    public final String name;
    public final int numericId;
    public final Map<FaceDirection, String> texturePaths;

    public final float mass;
    public final float restitution;
    public final float friction;

    public final float metallic;
    public final float roughness;
    public final Vector3 emission;

    public final Set<BlockTag> tags;

    public final String modelPath;
    public final String behaviourFile;
    public final Map<String, Object> behaviourParamOverrides;
    public final ResolvedBehaviour resolvedBehaviour;

    public final String itemTexture;

    public BlockDefinition(
            String id,
            String name,
            int numericId,
            Map<FaceDirection, String> texturePaths,
            float mass,
            float restitution,
            float friction,
            float metallic,
            float roughness,
            Vector3 emission,
            Set<BlockTag> tags,
            String modelPath,
            String behaviourFile,
            Map<String, Object> behaviourParamOverrides,
            ResolvedBehaviour resolvedBehaviour,
            String itemTexture) {
        this.id = id;
        this.name = name;
        this.numericId = numericId;
        this.texturePaths = Collections.unmodifiableMap(new EnumMap<>(texturePaths));
        this.mass = mass;
        this.restitution = restitution;
        this.friction = friction;
        this.metallic = metallic;
        this.roughness = roughness;
        this.emission = new Vector3(emission);
        this.tags = Collections.unmodifiableSet(tags);
        this.modelPath = modelPath;
        this.behaviourFile = behaviourFile;
        this.behaviourParamOverrides = behaviourParamOverrides == null
                ? null
                : Collections.unmodifiableMap(behaviourParamOverrides);
        this.resolvedBehaviour = resolvedBehaviour;
        this.itemTexture = itemTexture;
    }

    public BlockDefinition withResolvedBehaviour(ResolvedBehaviour resolved) {
        return new BlockDefinition(
                id,
                name,
                numericId,
                texturePaths,
                mass,
                restitution,
                friction,
                metallic,
                roughness,
                emission,
                tags,
                modelPath,
                behaviourFile,
                behaviourParamOverrides,
                resolved,
                itemTexture);
    }

    public boolean hasTag(BlockTag tag) {
        return tags.contains(tag);
    }

    public boolean hasBehaviour() {
        return resolvedBehaviour != null;
    }
}
