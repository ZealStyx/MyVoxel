package com.zeal.voxel.block;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.zeal.voxel.render.ao.FaceDirection;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BlockLoader {
    public static final float DEFAULT_MASS = 1.0f;
    public static final float DEFAULT_RESTITUTION = 0.2f;
    public static final float DEFAULT_FRICTION = 0.8f;

    public static final float DEFAULT_METALLIC = 0.0f;
    public static final float DEFAULT_ROUGHNESS = 0.9f;
    public static final Vector3 DEFAULT_EMISSION = new Vector3(0f, 0f, 0f);

    private static final Set<String> ALLOWED_TOP_LEVEL_KEYS = new HashSet<>(Arrays.asList(
            "id", "name", "numericId", "textures", "physics", "pbr", "tags", "model", "item",
            "behaviourFile", "behaviourParams"));

    private final JsonReader jsonReader = new JsonReader();

    public BlockDefinition load(FileHandle file) {
        JsonValue root;
        try {
            root = jsonReader.parse(file);
        } catch (Exception e) {
            throw new BlockLoadException("Block file " + file.name()
                    + " field root is invalid JSON. Hint: expected a top-level object.", e);
        }

        if (root == null || !root.isObject()) {
            throw new BlockLoadException("Block file " + file.name()
                    + " field root is invalid. Hint: top-level object required.");
        }

        validateNoUnknownTopLevelKeys(file, root);

        String id = requireString(file, root, "id");
        String name = requireString(file, root, "name");
        int numericId = requireInt(file, root, "numericId");

        JsonValue texturesNode = root.get("textures");
        if (texturesNode == null || !texturesNode.isObject()) {
            throw new BlockLoadException("Block file " + file.name()
                    + " field textures is missing or invalid. Hint: provide object with all/top/face keys.");
        }
        Map<FaceDirection, String> texturePaths = parseTextures(file, id, texturesNode);

        JsonValue physicsNode = root.get("physics");
        float mass = readOptionalFloat(file, physicsNode, "physics", "mass", DEFAULT_MASS);
        float restitution = readOptionalFloat(file, physicsNode, "physics", "restitution", DEFAULT_RESTITUTION);
        float friction = readOptionalFloat(file, physicsNode, "physics", "friction", DEFAULT_FRICTION);

        JsonValue pbrNode = root.get("pbr");
        float metallic = readOptionalFloat(file, pbrNode, "pbr", "metallic", DEFAULT_METALLIC);
        float roughness = readOptionalFloat(file, pbrNode, "pbr", "roughness", DEFAULT_ROUGHNESS);
        Vector3 emission = readOptionalEmission(file, pbrNode, DEFAULT_EMISSION);

        Set<BlockTag> tags = parseTags(file, root.get("tags"));

        String modelPath = optionalString(file, root, "model");
        String behaviourFile = optionalString(file, root, "behaviourFile");

        Map<String, Object> behaviourOverrides = null;
        JsonValue behaviourParamsNode = root.get("behaviourParams");
        if (behaviourParamsNode != null) {
            if (behaviourFile == null || behaviourFile.isBlank()) {
                throw new BlockLoadException("Block file " + file.name()
                        + " field behaviourParams is present without behaviourFile."
                        + " Hint: add behaviourFile or remove behaviourParams.");
            }
            if (!behaviourParamsNode.isObject()) {
                throw new BlockLoadException("Block file " + file.name()
                        + " field behaviourParams has invalid value " + behaviourParamsNode
                        + ". Hint: behaviourParams must be an object.");
            }
            behaviourOverrides = jsonObjectToMap(behaviourParamsNode);
        }

        String itemTexture = null;
        JsonValue itemNode = root.get("item");
        if (itemNode != null) {
            if (!itemNode.isObject()) {
                throw new BlockLoadException("Block file " + file.name()
                        + " field item has invalid value " + itemNode
                        + ". Hint: item must be an object.");
            }
            JsonValue textureNode = itemNode.get("texture");
            if (textureNode != null) {
                if (!textureNode.isString()) {
                    throw new BlockLoadException("Block file " + file.name()
                            + " field item.texture has invalid value " + textureNode
                            + ". Hint: item.texture must be a string path.");
                }
                itemTexture = textureNode.asString();
            }
        }

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
                behaviourOverrides,
                null,
                itemTexture);
    }

    private void validateNoUnknownTopLevelKeys(FileHandle file, JsonValue root) {
        for (JsonValue child = root.child; child != null; child = child.next) {
            if (!ALLOWED_TOP_LEVEL_KEYS.contains(child.name)) {
                throw new BlockLoadException("Block file " + file.name() + " field " + child.name
                        + " is unknown. Hint: valid keys are " + ALLOWED_TOP_LEVEL_KEYS);
            }
        }
    }

    private Map<FaceDirection, String> parseTextures(FileHandle file, String blockId, JsonValue texturesNode) {
        Set<String> valid = Set.of("all", "top", "bottom", "north", "south", "east", "west");
        for (JsonValue child = texturesNode.child; child != null; child = child.next) {
            if (!valid.contains(child.name)) {
                throw new BlockLoadException("Block file " + file.name() + " field textures." + child.name
                        + " is invalid for block '" + blockId
                        + "'. Hint: valid texture keys are " + valid);
            }
        }

        String all = optionalString(file, texturesNode, "all");
        Map<FaceDirection, String> out = new EnumMap<>(FaceDirection.class);

        for (FaceDirection face : FaceDirection.values()) {
            String key = face.yamlKey();
            String path = optionalString(file, texturesNode, key);
            if (path == null) {
                path = all;
            }
            if (path == null) {
                throw new BlockLoadException("Block file " + file.name() + " field textures." + key
                        + " is missing for block '" + blockId
                        + "'. Hint: define textures.all or explicit face path.");
            }
            out.put(face, path);
        }
        return out;
    }

    private Set<BlockTag> parseTags(FileHandle file, JsonValue tagsNode) {
        Set<BlockTag> tags = new HashSet<>();
        if (tagsNode == null) {
            return tags;
        }
        if (!tagsNode.isArray()) {
            throw new BlockLoadException("Block file " + file.name() + " field tags has invalid value " + tagsNode
                    + ". Hint: tags must be an array of strings.");
        }

        for (JsonValue child = tagsNode.child; child != null; child = child.next) {
            if (!child.isString()) {
                throw new BlockLoadException("Block file " + file.name() + " field tags has invalid value " + child
                        + ". Hint: every tag must be a string.");
            }
            String raw = child.asString();
            String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            try {
                tags.add(BlockTag.valueOf(normalized));
            } catch (IllegalArgumentException ex) {
                throw new BlockLoadException("Block file " + file.name() + " field tags has invalid value '" + raw
                        + "'. Hint: valid tags are " + Arrays.toString(BlockTag.values()));
            }
        }
        return tags;
    }

    private String requireString(FileHandle file, JsonValue root, String key) {
        JsonValue node = root.get(key);
        if (node == null || !node.isString() || node.asString().isBlank()) {
            throw new BlockLoadException("Block file " + file.name() + " field " + key
                    + " is missing or invalid. Hint: expected non-empty string.");
        }
        return node.asString();
    }

    private String optionalString(FileHandle file, JsonValue root, String key) {
        JsonValue node = root.get(key);
        if (node == null) {
            return null;
        }
        if (!node.isString()) {
            throw new BlockLoadException("Block file " + file.name() + " field " + key
                    + " has invalid value " + node + ". Hint: expected string.");
        }
        return node.asString();
    }

    private int requireInt(FileHandle file, JsonValue root, String key) {
        JsonValue node = root.get(key);
        if (node == null || !node.isNumber()) {
            throw new BlockLoadException("Block file " + file.name() + " field " + key
                    + " is missing or invalid. Hint: expected integer.");
        }
        return node.asInt();
    }

    private float readOptionalFloat(FileHandle file, JsonValue parent, String parentName, String key, float defaultValue) {
        if (parent == null) {
            return defaultValue;
        }
        if (!parent.isObject()) {
            throw new BlockLoadException("Block file " + file.name() + " field " + parentName
                    + " has invalid value " + parent + ". Hint: expected object.");
        }
        JsonValue node = parent.get(key);
        if (node == null) {
            return defaultValue;
        }
        if (!node.isNumber()) {
            throw new BlockLoadException("Block file " + file.name() + " field " + parentName + "." + key
                    + " has invalid value " + node + ". Hint: expected number.");
        }
        return (float) node.asDouble();
    }

    private Vector3 readOptionalEmission(FileHandle file, JsonValue pbrNode, Vector3 defaultValue) {
        if (pbrNode == null) {
            return new Vector3(defaultValue);
        }
        if (!pbrNode.isObject()) {
            throw new BlockLoadException("Block file " + file.name() + " field pbr has invalid value " + pbrNode
                    + ". Hint: expected object.");
        }
        JsonValue node = pbrNode.get("emission");
        if (node == null) {
            return new Vector3(defaultValue);
        }
        if (!node.isArray() || node.size != 3) {
            throw new BlockLoadException("Block file " + file.name() + " field pbr.emission has invalid value " + node
                    + ". Hint: expected [r,g,b] array of 3 numbers.");
        }
        JsonValue a = node.get(0);
        JsonValue b = node.get(1);
        JsonValue c = node.get(2);
        if (!a.isNumber() || !b.isNumber() || !c.isNumber()) {
            throw new BlockLoadException("Block file " + file.name() + " field pbr.emission has invalid value " + node
                    + ". Hint: expected numeric components.");
        }
        return new Vector3((float) a.asDouble(), (float) b.asDouble(), (float) c.asDouble());
    }

    private Map<String, Object> jsonObjectToMap(JsonValue objectNode) {
        Map<String, Object> map = new HashMap<>();
        for (JsonValue child = objectNode.child; child != null; child = child.next) {
            map.put(child.name, toJavaObject(child));
        }
        return map;
    }

    private Object toJavaObject(JsonValue value) {
        if (value.isString()) {
            return value.asString();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.isObject()) {
            return jsonObjectToMap(value);
        }
        if (value.isArray()) {
            Map<String, Object> fallback = new HashMap<>();
            int i = 0;
            for (JsonValue child = value.child; child != null; child = child.next) {
                fallback.put(String.valueOf(i++), toJavaObject(child));
            }
            return fallback;
        }
        return value.toString();
    }
}
