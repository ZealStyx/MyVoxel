package com.zeal.voxel.block.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockModel {
    public final int textureWidth;
    public final int textureHeight;
    public final Map<String, String> textures;
    public final List<ModelElement> elements;
    public final String sourceFile;

    public BlockModel(int textureWidth,
                      int textureHeight,
                      Map<String, String> textures,
                      List<ModelElement> elements,
                      String sourceFile) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.textures = Collections.unmodifiableMap(new HashMap<>(textures));
        this.elements = List.copyOf(elements);
        this.sourceFile = sourceFile;
    }

    public String resolveTexturePath(String textureRef) {
        if (textureRef == null || textureRef.isBlank()) {
            throw new ModelLoadException("Model file " + sourceFile
                    + " has invalid face texture reference: " + textureRef);
        }

        String key = textureRef.startsWith("#") ? textureRef.substring(1) : textureRef;
        String path = textures.get(key);
        if (path == null) {
            throw new ModelLoadException("Model file " + sourceFile
                    + " has unknown texture variable '" + textureRef
                    + "'. Available: " + textures.keySet());
        }

        String normalized = path.replace('\\', '/');
        if (!normalized.endsWith(".png")) {
            normalized += ".png";
        }
        return normalized;
    }
}
