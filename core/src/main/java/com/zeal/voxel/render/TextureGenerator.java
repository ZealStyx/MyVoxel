package com.zeal.voxel.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.zeal.voxel.block.BlockType;
import com.zeal.voxel.render.ao.FaceDirection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Generates procedural block textures at runtime to avoid missing asset issues.
 */
public class TextureGenerator {
    private static final int GENERATED_TEXTURE_SIZE = 16;
    private static final Map<Integer, TextureRegion> blockTextures = new HashMap<>();
    private static final Map<String, TextureRegion> pathRegions = new HashMap<>();
    private static Texture atlasTexture;

    public static final int TEX_AIR = 0;
    public static final int TEX_STONE = 1;
    public static final int TEX_WOOD = 2;
    public static final int TEX_IRON = 3;
    public static final int TEX_THRUSTER = 4;
    public static final int TEX_GYRO = 5;
    public static final int TEX_GRASS_TOP = 6;
    public static final int TEX_DIRT = 7;
    public static final int TEX_GRASS_SIDE = 8;

    public static void generateTextures() {
        generateTextures(Collections.emptySet());
    }

    public static void generateTextures(Set<String> extraPaths) {
        if (atlasTexture != null) {
            atlasTexture.dispose();
            atlasTexture = null;
        }

        blockTextures.clear();
        pathRegions.clear();

        LinkedHashSet<String> requiredPaths = new LinkedHashSet<>();
        requiredPaths.add("textures/blocks/air.png");
        requiredPaths.add("textures/blocks/stone.png");
        requiredPaths.add("textures/blocks/wood.png");
        requiredPaths.add("textures/blocks/iron.png");
        requiredPaths.add("textures/blocks/grass_top.png");
        requiredPaths.add("textures/blocks/dirt.png");
        requiredPaths.add("textures/blocks/grass_side.png");
        requiredPaths.add("textures/blocks/thruster_top.png");
        requiredPaths.add("textures/blocks/thruster_bottom.png");
        requiredPaths.add("textures/blocks/thruster_side.png");
        requiredPaths.add("textures/blocks/thruster_exhaust.png");
        requiredPaths.add("textures/blocks/gyroscope.png");
        requiredPaths.add("textures/blocks/heavy_thruster.png");
        requiredPaths.add("textures/items/grass_block.png");

        for (String path : extraPaths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            String normalized = normalizePath(path);
            requiredPaths.add(normalized);
            if (!normalized.endsWith(".png")) {
                requiredPaths.add(normalized + ".png");
            }
        }

        List<String> sorted = new ArrayList<>(requiredPaths);
        Collections.sort(sorted);

        if (sorted.isEmpty()) {
            sorted.add("textures/blocks/stone.png");
        }

        Map<String, Integer> pathOffsets = new HashMap<>();
        List<Pixmap> pixmaps = new ArrayList<>();
        
        // Try to load actual texture files; fall back to procedural if not found
        for (String path : sorted) {
            Pixmap pixmap = loadOrGenerateTexture(path);
            pixmaps.add(pixmap);
            pathOffsets.put(path, pixmaps.size() - 1);
        }
        
        // Pack pixmaps into a horizontal atlas
        int atlasWidth = GENERATED_TEXTURE_SIZE * pixmaps.size();
        int atlasHeight = GENERATED_TEXTURE_SIZE;
        Pixmap atlasPixmap = new Pixmap(atlasWidth, atlasHeight, Pixmap.Format.RGBA8888);
        
        for (int i = 0; i < pixmaps.size(); i++) {
            Pixmap pixmap = pixmaps.get(i);
            // Draw pixmap into atlas at horizontal position i
            atlasPixmap.drawPixmap(pixmap, i * GENERATED_TEXTURE_SIZE, 0);
            pixmap.dispose();
        }
        
        atlasTexture = new Texture(atlasPixmap);
        atlasPixmap.dispose();

        for (int i = 0; i < sorted.size(); i++) {
            String path = sorted.get(i);
            TextureRegion region = new TextureRegion(
                atlasTexture,
                i * GENERATED_TEXTURE_SIZE,
                0,
                GENERATED_TEXTURE_SIZE,
                GENERATED_TEXTURE_SIZE);
            pathRegions.put(path, region);
        }
        
        registerAliases();
        assignLegacyBlockSlots();
    }
    
    /**
     * Attempt to load a texture file from assets. If it doesn't exist or fails to load,
     * generate a fallback procedural texture based on the path name.
     */
    private static Pixmap loadOrGenerateTexture(String path) {
        String normalized = normalizePath(path);
        FileHandle textureFile = Gdx.files.internal(normalized);
        
        try {
            if (textureFile.exists()) {
                Texture tex = new Texture(textureFile);
                Pixmap pixmap = new Pixmap(GENERATED_TEXTURE_SIZE, GENERATED_TEXTURE_SIZE, Pixmap.Format.RGBA8888);
                // Resize the loaded texture to fit our atlas slot
                Pixmap loaded = new Pixmap(textureFile);
                pixmap.drawPixmap(loaded, 0, 0, loaded.getWidth(), loaded.getHeight(),
                                 0, 0, GENERATED_TEXTURE_SIZE, GENERATED_TEXTURE_SIZE);
                loaded.dispose();
                tex.dispose();
                return pixmap;
            }
        } catch (Exception e) {
            // File doesn't exist or failed to load; fall back to procedural
        }
        
        // Generate fallback procedural texture
        Pixmap pixmap = new Pixmap(GENERATED_TEXTURE_SIZE, GENERATED_TEXTURE_SIZE, Pixmap.Format.RGBA8888);
        Color color = colorForPath(path);
        pixmap.setColor(color);
        pixmap.fillRectangle(0, 0, GENERATED_TEXTURE_SIZE, GENERATED_TEXTURE_SIZE);
        
        Color borderColor = new Color(color).mul(0.8f);
        borderColor.a = 1f;
        pixmap.setColor(borderColor);
        pixmap.drawRectangle(0, 0, GENERATED_TEXTURE_SIZE, GENERATED_TEXTURE_SIZE);
        
        return pixmap;
    }

    public static TextureRegion getRegion(int blockId, FaceDirection face) {
        int texIndex = blockId;

        if (blockId == BlockType.GRASS.getId()) {
            if (face == FaceDirection.TOP)
                texIndex = TEX_GRASS_TOP;
            else if (face == FaceDirection.BOTTOM)
                texIndex = TEX_DIRT;
            else
                texIndex = TEX_GRASS_SIDE;
        } else if (blockId == BlockType.DIRT.getId()) {
            texIndex = TEX_DIRT;
        }

        return blockTextures.getOrDefault(texIndex, pathRegions.get("textures/blocks/stone.png"));
    }

    public static Texture getAtlasTexture() {
        return atlasTexture;
    }

    public static TextureRegion findRegion(String path) {
        String normalized = normalizePath(path);
        TextureRegion region = pathRegions.get(normalized);
        if (region != null) {
            return region;
        }
        if (normalized.endsWith(".png")) {
            return pathRegions.get(normalized.substring(0, normalized.length() - 4));
        }
        return pathRegions.get(normalized + ".png");
    }

    private static void registerPath(String path, TextureRegion region) {
        if (region != null) {
            pathRegions.put(normalizePath(path), region);
        }
    }

    private static void registerAliases() {
        Map<String, TextureRegion> copy = new HashMap<>(pathRegions);
        for (Map.Entry<String, TextureRegion> entry : copy.entrySet()) {
            String path = entry.getKey();
            TextureRegion region = entry.getValue();

            if (path.startsWith("textures/")) {
                registerPath(path.substring("textures/".length()), region);
            }

            if (path.endsWith(".png")) {
                registerPath(path.substring(0, path.length() - 4), region);
            }
        }
    }

    private static void assignLegacyBlockSlots() {
        blockTextures.put(TEX_AIR, findRegion("textures/blocks/air.png"));
        blockTextures.put(TEX_STONE, findRegion("textures/blocks/stone.png"));
        blockTextures.put(TEX_WOOD, findRegion("textures/blocks/wood.png"));
        blockTextures.put(TEX_IRON, findRegion("textures/blocks/iron.png"));
        blockTextures.put(TEX_THRUSTER, findRegion("textures/blocks/thruster_side.png"));
        blockTextures.put(TEX_GYRO, findRegion("textures/blocks/gyroscope.png"));
        blockTextures.put(TEX_GRASS_TOP, findRegion("textures/blocks/grass_top.png"));
        blockTextures.put(TEX_DIRT, findRegion("textures/blocks/dirt.png"));
        blockTextures.put(TEX_GRASS_SIDE, findRegion("textures/blocks/grass_side.png"));
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String p = path.replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        return p;
    }

    private static Color colorForPath(String path) {
        String p = path.toLowerCase();
        if (p.contains("air")) return Color.CLEAR;
        if (p.contains("water")) return new Color(0.2f, 0.6f, 1.0f, 0.8f);  // Light blue with transparency
        if (p.contains("dark_stone")) return new Color(0.25f, 0.25f, 0.25f, 1f);
        if (p.contains("cliff_stone")) return Color.GRAY;
        if (p.contains("mossy_stone")) return new Color(0.35f, 0.45f, 0.3f, 1f);
        if (p.contains("gravel")) return new Color(0.55f, 0.5f, 0.45f, 1f);
        if (p.contains("sand")) return new Color(0.9f, 0.85f, 0.6f, 1f);
        if (p.contains("snow")) return Color.WHITE;
        if (p.contains("stone")) return Color.GRAY;
        if (p.contains("wood")) return Color.BROWN;
        if (p.contains("iron")) return Color.LIGHT_GRAY;
        if (p.contains("grass_top")) return new Color(0.3f, 0.8f, 0.3f, 1f);
        if (p.contains("grass_side")) return new Color(0.45f, 0.3f, 0.15f, 1f);
        if (p.contains("dirt")) return new Color(0.45f, 0.3f, 0.15f, 1f);
        if (p.contains("thruster")) return Color.ORANGE;
        if (p.contains("gyroscope")) return Color.CYAN;

        int h = Math.abs(path.hashCode());
        float r = 0.2f + ((h & 0xFF) / 255f) * 0.7f;
        float g = 0.2f + (((h >> 8) & 0xFF) / 255f) * 0.7f;
        float b = 0.2f + (((h >> 16) & 0xFF) / 255f) * 0.7f;
        return new Color(r, g, b, 1f);
    }

    public static void dispose() {
        if (atlasTexture != null) {
            atlasTexture.dispose();
            atlasTexture = null;
        }
        blockTextures.clear();
        pathRegions.clear();
    }
}
