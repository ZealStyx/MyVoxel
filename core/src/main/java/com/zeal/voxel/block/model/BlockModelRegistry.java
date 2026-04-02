package com.zeal.voxel.block.model;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.zeal.voxel.block.BlockDefinition;
import com.zeal.voxel.block.BlockRegistry;
import com.zeal.voxel.block.TextureRegionResolver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.io.File;

public class BlockModelRegistry {
    private static BlockModelRegistry active;

    private final Map<Integer, BlockModel> byBlockId = new HashMap<>();
    private final BlockModelLoader loader = new BlockModelLoader();
    private final TextureRegionResolver textureResolver;

    public BlockModelRegistry(TextureRegionResolver textureResolver) {
        this.textureResolver = textureResolver;
    }

    public static Set<String> collectTexturePaths(BlockRegistry registry) {
        Set<String> paths = new HashSet<>();
        BlockModelLoader loader = new BlockModelLoader();

        for (BlockDefinition def : registry.getAll()) {
            if (def.modelPath == null || def.modelPath.isBlank()) {
                continue;
            }

            FileHandle modelFile = resolveModelFile(def.modelPath);
            if (!modelFile.exists()) {
                throw new ModelLoadException("Model file missing for block '" + def.id + "': " + def.modelPath);
            }
            BlockModel model = loader.load(modelFile);
            for (String value : model.textures.values()) {
                String normalized = value.replace('\\', '/');
                if (!normalized.endsWith(".png")) {
                    normalized += ".png";
                }
                paths.add(normalized);
            }
        }

        return paths;
    }

    public void loadAll(BlockRegistry registry) {
        byBlockId.clear();

        for (BlockDefinition def : registry.getAll()) {
            if (def.modelPath == null || def.modelPath.isBlank()) {
                continue;
            }

            FileHandle modelFile = resolveModelFile(def.modelPath);
            if (!modelFile.exists()) {
                throw new ModelLoadException("Model file missing for block '" + def.id + "': " + def.modelPath);
            }

            BlockModel model = loader.load(modelFile);

            for (String variablePath : model.textures.values()) {
                String fullPath = variablePath.replace('\\', '/');
                if (!fullPath.endsWith(".png")) {
                    fullPath += ".png";
                }
                if (textureResolver.resolvePath(fullPath) == null) {
                    throw new ModelLoadException("Model file " + model.sourceFile
                            + " references missing texture path: " + fullPath);
                }
            }

            byBlockId.put(def.numericId, model);
        }

        active = this;
    }

    public boolean hasModel(int blockId) {
        return byBlockId.containsKey(blockId);
    }

    public BlockModel get(int blockId) {
        return byBlockId.get(blockId);
    }

    public static BlockModelRegistry getActive() {
        return active;
    }

    private static FileHandle resolveModelFile(String modelPath) {
        FileHandle direct = Gdx.files.internal(modelPath);
        if (direct.exists()) {
            return direct;
        }

        if (!modelPath.startsWith("assets/")) {
            FileHandle prefixed = Gdx.files.internal("assets/" + modelPath);
            if (prefixed.exists()) {
                return prefixed;
            }
        }

        File directFile = new File(modelPath);
        if (directFile.exists()) {
            return Gdx.files.absolute(directFile.getAbsolutePath());
        }

        if (!modelPath.startsWith("assets/")) {
            File prefixedFile = new File("assets/" + modelPath);
            if (prefixedFile.exists()) {
                return Gdx.files.absolute(prefixedFile.getAbsolutePath());
            }
        }

        return direct;
    }
}
