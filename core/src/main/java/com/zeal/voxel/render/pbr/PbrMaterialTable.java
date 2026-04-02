package com.zeal.voxel.render.pbr;

import com.badlogic.gdx.math.Vector3;
import com.zeal.voxel.block.BlockDefinition;
import com.zeal.voxel.block.BlockRegistry;

import java.util.HashMap;
import java.util.Map;

/** Registry of PBR materials indexed by block ID. */
public class PbrMaterialTable {

    private static final PbrMaterial DEFAULT = new PbrMaterial(0.0f, 0.9f, 1.0f, Vector3.Zero);

    private final Map<Integer, PbrMaterial> materials = new HashMap<>();

    public void buildFromRegistry(BlockRegistry registry) {
        materials.clear();
        for (BlockDefinition def : registry.getAll()) {
            register(def.numericId, new PbrMaterial(def.metallic, def.roughness, def.friction, def.emission));
        }
    }

    public void register(int blockId, PbrMaterial material) {
        materials.put(blockId, material);
    }

    public PbrMaterial get(int blockId) {
        PbrMaterial m = materials.get(blockId);
        return m != null ? m : DEFAULT;
    }
}
