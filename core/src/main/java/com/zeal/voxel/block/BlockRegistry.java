package com.zeal.voxel.block;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockRegistry {
    private static BlockRegistry active;

    private final Map<String, BlockDefinition> byId = new HashMap<>();
    private final Map<Integer, BlockDefinition> byNumericId = new HashMap<>();
    private final Map<BlockTag, List<BlockDefinition>> byTag = new EnumMap<>(BlockTag.class);

    private final BlockLoader blockLoader;
    private final BehaviourLoader behaviourLoader;
    private final BlockBehaviourRegistry behaviourRegistry;

    public BlockRegistry(BlockLoader blockLoader, BehaviourLoader behaviourLoader, BlockBehaviourRegistry behaviourRegistry) {
        this.blockLoader = blockLoader;
        this.behaviourLoader = behaviourLoader;
        this.behaviourRegistry = behaviourRegistry;
    }

    public void loadAll(FileHandle blocksDir, FileHandle behavioursDir) {
        byId.clear();
        byNumericId.clear();
        byTag.clear();

        Map<String, BehaviourDefinition> behaviourDefinitions = behaviourLoader.loadAll(behavioursDir);
        validateBehaviourTypes(behaviourDefinitions);

        List<FileHandle> blockFiles = listBlockFiles(blocksDir);

        for (FileHandle file : blockFiles) {
            BlockDefinition raw;
            try {
                raw = blockLoader.load(file);
            } catch (BlockLoadException e) {
                throw e;
            } catch (Exception e) {
                throw new BlockLoadException("Block file " + file.name() + " parse failure: " + e.getMessage(), e);
            }

            BlockDefinition resolved = resolveBehaviour(raw, behaviourDefinitions, file.name());
            index(resolved, file.name());
        }

        behaviourRegistry.lock();
        active = this;
    }

    private void validateBehaviourTypes(Map<String, BehaviourDefinition> defs) {
        Set<String> registered = behaviourRegistry.getRegisteredTypes();
        for (Map.Entry<String, BehaviourDefinition> entry : defs.entrySet()) {
            BehaviourDefinition def = entry.getValue();
            if (!behaviourRegistry.isRegistered(def.type)) {
                throw new BlockLoadException("Behaviour file " + entry.getKey() + ".json"
                        + " has unregistered type '" + def.type + "'."
                        + " Hint: register Java class before load. Registered types: " + registered);
            }
        }
    }

    private List<FileHandle> listBlockFiles(FileHandle blocksDir) {
        if (blocksDir == null || !blocksDir.exists() || !blocksDir.isDirectory()) {
            throw new BlockLoadException("Blocks directory not found: " + (blocksDir == null ? "null" : blocksDir.path()));
        }

        FileHandle[] files = blocksDir.list((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            throw new BlockLoadException("No block json files found in " + blocksDir.path());
        }

        List<FileHandle> sorted = new ArrayList<>();
        Collections.addAll(sorted, files);
        sorted.sort(Comparator.comparing(FileHandle::name));
        return sorted;
    }

    private BlockDefinition resolveBehaviour(BlockDefinition raw, Map<String, BehaviourDefinition> defs, String sourceFile) {
        if (raw.behaviourFile == null || raw.behaviourFile.isBlank()) {
            return raw;
        }

        String stem = stem(raw.behaviourFile);
        BehaviourDefinition base = defs.get(stem);
        if (base == null) {
            throw new BlockLoadException("Block file " + sourceFile + " field behaviourFile has invalid value '"
                    + raw.behaviourFile + "'. Hint: available behaviour files: " + defs.keySet());
        }

        Map<String, Object> overrides = raw.behaviourParamOverrides == null
                ? Collections.emptyMap()
                : raw.behaviourParamOverrides;

        for (String key : overrides.keySet()) {
            if (!base.baseParams.containsKey(key)) {
                Gdx.app.log("BlockRegistry", "Warning: block '" + raw.id + "' override key '" + key
                        + "' is not present in base behaviour params for type '" + base.type
                        + "'. Available keys: " + base.baseParams.keySet());
            }
        }

        ResolvedBehaviour resolved = new ResolvedBehaviour(base.type, base, overrides);
        return raw.withResolvedBehaviour(resolved);
    }

    private void index(BlockDefinition def, String sourceFile) {
        BlockDefinition idConflict = byId.get(def.id);
        if (idConflict != null) {
            throw new BlockLoadException("Block file " + sourceFile + " has duplicate id '" + def.id
                    + "'. Existing block: " + idConflict.id);
        }

        BlockDefinition numericConflict = byNumericId.get(def.numericId);
        if (numericConflict != null) {
            throw new BlockLoadException("Block file " + sourceFile + " has duplicate numericId " + def.numericId
                    + ". Existing block id: " + numericConflict.id);
        }

        byId.put(def.id, def);
        byNumericId.put(def.numericId, def);

        for (BlockTag tag : def.tags) {
            byTag.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(def);
        }
    }

    private String stem(String path) {
        String fileName = path.replace('\\', '/');
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) {
            fileName = fileName.substring(0, dot);
        }
        return fileName;
    }

    public BlockDefinition get(int numericId) {
        BlockDefinition def = byNumericId.get(numericId);
        if (def == null) {
            throw new BlockLoadException("Unknown block numericId " + numericId);
        }
        return def;
    }

    public BlockDefinition get(String id) {
        BlockDefinition def = byId.get(id);
        if (def == null) {
            throw new BlockLoadException("Unknown block id '" + id + "'");
        }
        return def;
    }

    public List<BlockDefinition> getByTag(BlockTag tag) {
        return byTag.getOrDefault(tag, Collections.emptyList());
    }

    public boolean hasTag(int numericId, BlockTag tag) {
        BlockDefinition def = byNumericId.get(numericId);
        return def != null && def.hasTag(tag);
    }

    public ResolvedBehaviour getBehaviour(int numericId) {
        BlockDefinition def = byNumericId.get(numericId);
        return def == null ? null : def.resolvedBehaviour;
    }

    public List<BlockDefinition> getAll() {
        return List.copyOf(byNumericId.values());
    }

    public float getMass(int numericId) {
        BlockDefinition def = byNumericId.get(numericId);
        return def == null ? BlockLoader.DEFAULT_MASS : def.mass;
    }

    public Set<String> collectTexturePaths() {
        Set<String> paths = new HashSet<>();
        for (BlockDefinition def : byNumericId.values()) {
            paths.addAll(def.texturePaths.values());
            if (def.itemTexture != null) {
                paths.add(def.itemTexture);
            }
        }
        return paths;
    }

    public static BlockRegistry getActive() {
        return active;
    }
}
