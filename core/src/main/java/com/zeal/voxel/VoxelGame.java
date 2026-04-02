package com.zeal.voxel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.zeal.voxel.block.BehaviourLoader;
import com.zeal.voxel.block.BlockBehaviourRegistry;
import com.zeal.voxel.block.BlockLoader;
import com.zeal.voxel.block.BlockRegistry;
import com.zeal.voxel.block.TextureRegionResolver;
import com.zeal.voxel.block.model.BlockModelRegistry;
import com.zeal.voxel.render.TextureGenerator;
import com.zeal.voxel.render.pbr.PbrMaterialTable;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class VoxelGame extends Game {
    @Override
    public void create() {
        // 1. Register all Java behaviour types FIRST — before any JSON is loaded.
        BlockBehaviourRegistry behaviourRegistry = new BlockBehaviourRegistry();
        behaviourRegistry.register("thruster", new com.zeal.voxel.block.ThrusterBehaviour());
        behaviourRegistry.register("gyroscope", new com.zeal.voxel.block.GyroscopeBehaviour());

        // 2. Load and validate all JSON block/behaviour definitions.
        TextureRegionResolver textureResolver = new TextureRegionResolver();
        BlockRegistry blockRegistry = new BlockRegistry(
            new BlockLoader(),
            new BehaviourLoader(),
            behaviourRegistry);
        FileHandle blocksDir = resolveAssetsPath("blocks");
        FileHandle behavioursDir = resolveAssetsPath("behaviours/blocks");
        blockRegistry.loadAll(
            blocksDir,
            behavioursDir);

        // 3. Build texture atlas from block and model texture references.
        Set<String> allTexturePaths = new HashSet<>(blockRegistry.collectTexturePaths());
        allTexturePaths.addAll(BlockModelRegistry.collectTexturePaths(blockRegistry));
        TextureGenerator.generateTextures(allTexturePaths);

        // 4. Build derived lookup tables from the registry.
        textureResolver.buildFromRegistry(blockRegistry);

        PbrMaterialTable pbrTable = new PbrMaterialTable();
        pbrTable.buildFromRegistry(blockRegistry);

        // 5. Load Blockbench models and validate texture paths against atlas.
        BlockModelRegistry modelRegistry = new BlockModelRegistry(textureResolver);
        modelRegistry.loadAll(blockRegistry);

        // 6. Start runtime systems.
        Bullet.init();
        
        if (com.zeal.voxel.util.Constants.DEBUG) {
            com.zeal.voxel.world.FaceGeometry.assertWindingCorrect();
        }

        setScreen(new GameScreen(blockRegistry, textureResolver, behaviourRegistry, pbrTable));
    }

    @Override
    public void dispose() {
        super.dispose();
        TextureGenerator.dispose();
    }

    private FileHandle resolveAssetsPath(String relativePath) {
        FileHandle direct = Gdx.files.internal(relativePath);
        if (direct.exists() && direct.isDirectory()) {
            return direct;
        }

        FileHandle prefixed = Gdx.files.internal("assets/" + relativePath);
        if (prefixed.exists() && prefixed.isDirectory()) {
            return prefixed;
        }

        File directFile = new File(relativePath);
        if (directFile.exists() && directFile.isDirectory()) {
            return Gdx.files.absolute(directFile.getAbsolutePath());
        }

        File prefixedFile = new File("assets/" + relativePath);
        if (prefixedFile.exists() && prefixedFile.isDirectory()) {
            return Gdx.files.absolute(prefixedFile.getAbsolutePath());
        }

        return direct;
    }
}
