package com.zeal.voxel.block;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.zeal.voxel.render.TextureGenerator;
import com.zeal.voxel.render.ao.FaceDirection;

import java.util.HashMap;
import java.util.Map;

public class TextureRegionResolver {
    private final Map<Long, TextureRegion> cache = new HashMap<>();
    private final Map<String, TextureRegion> pathCache = new HashMap<>();

    public void register(BlockDefinition def) {
        for (FaceDirection direction : FaceDirection.values()) {
            String path = def.texturePaths.get(direction);
            TextureRegion region = resolvePath(path);
            if (region == null) {
                throw new BlockLoadException("Texture not found in atlas: " + path + " (block " + def.id + ")");
            }
            cache.put(key(def.numericId, direction), region);
            pathCache.put(normalize(path), region);
        }
    }

    public void buildFromRegistry(BlockRegistry registry) {
        cache.clear();
        pathCache.clear();
        for (BlockDefinition def : registry.getAll()) {
            register(def);
        }
    }

    public TextureRegion resolvePath(String path) {
        String normalized = normalize(path);
        TextureRegion cached = pathCache.get(normalized);
        if (cached != null) {
            return cached;
        }

        TextureRegion region = TextureGenerator.findRegion(normalized);
        if (region == null && normalized.endsWith(".png")) {
            region = TextureGenerator.findRegion(normalized.substring(0, normalized.length() - 4));
        }
        if (region == null && !normalized.endsWith(".png")) {
            region = TextureGenerator.findRegion(normalized + ".png");
        }
        if (region != null) {
            pathCache.put(normalized, region);
        }
        return region;
    }

    public TextureRegion resolve(int numericId, FaceDirection face) {
        TextureRegion region = cache.get(key(numericId, face));
        if (region == null) {
            throw new BlockLoadException("No texture cached for block " + numericId + " face " + face);
        }
        return region;
    }

    private long key(int numericId, FaceDirection face) {
        return (((long) numericId) << 32) | (face.ordinal() & 0xFFFFFFFFL);
    }

    private String normalize(String path) {
        String p = path.replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        return p;
    }
}
