package com.zeal.voxel.block.model;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.zeal.voxel.render.ao.FaceDirection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockModelLoader {
    static final boolean SWAP_NORTH_SOUTH = false;
    static final boolean SWAP_EAST_WEST = false;

    private static final float BLOCKBENCH_UNIT_SCALE = 1f / 16f;
    private static final int DEFAULT_TEXTURE_SIZE = 16;

    private final JsonReader jsonReader = new JsonReader();

    public BlockModel load(FileHandle file) {
        JsonValue root;
        try {
            root = jsonReader.parse(file);
        } catch (Exception e) {
            throw new ModelLoadException("Model file " + file.name() + " has invalid JSON", e);
        }

        if (root == null || !root.isObject()) {
            throw new ModelLoadException("Model file " + file.name() + " root must be an object");
        }

        int texW = DEFAULT_TEXTURE_SIZE;
        int texH = DEFAULT_TEXTURE_SIZE;
        JsonValue textureSize = root.get("textureSize");
        if (textureSize != null) {
            if (!textureSize.isArray() || textureSize.size != 2 || !textureSize.get(0).isNumber() || !textureSize.get(1).isNumber()) {
                throw new ModelLoadException("Model file " + file.name() + " field textureSize must be [w,h]");
            }
            texW = textureSize.getInt(0);
            texH = textureSize.getInt(1);
        }

        Map<String, String> textures = new HashMap<>();
        JsonValue texturesNode = root.get("textures");
        if (texturesNode != null) {
            if (!texturesNode.isObject()) {
                throw new ModelLoadException("Model file " + file.name() + " field textures must be object");
            }
            for (JsonValue child = texturesNode.child; child != null; child = child.next) {
                if (!child.isString()) {
                    throw new ModelLoadException("Model file " + file.name() + " texture key " + child.name + " must map to string path");
                }
                if (!"particle".equals(child.name)) {
                    textures.put(child.name, child.asString());
                }
            }
        }

        JsonValue elementsNode = root.get("elements");
        if (elementsNode == null || !elementsNode.isArray()) {
            throw new ModelLoadException("Model file " + file.name() + " field elements must be array");
        }

        List<ModelElement> elements = new ArrayList<>();
        for (JsonValue elementNode = elementsNode.child; elementNode != null; elementNode = elementNode.next) {
            elements.add(parseElement(file, elementNode));
        }

        return new BlockModel(texW, texH, textures, elements, file.path());
    }

    private ModelElement parseElement(FileHandle file, JsonValue node) {
        String name = node.has("name") && node.get("name").isString() ? node.getString("name") : "element";

        Vector3 from = parseVec3(file, node.get("from"), "from");
        Vector3 to = parseVec3(file, node.get("to"), "to");

        ElementRotation rotation = null;
        JsonValue rotNode = node.get("rotation");
        if (rotNode != null) {
            if (!rotNode.isObject()) {
                throw new ModelLoadException("Model file " + file.name() + " element " + name + " field rotation must be object");
            }
            float angle = rotNode.has("angle") && rotNode.get("angle").isNumber() ? (float) rotNode.getDouble("angle") : 0f;
            String axis = rotNode.has("axis") && rotNode.get("axis").isString() ? rotNode.getString("axis") : "y";
            Vector3 origin = parseVec3(file, rotNode.get("origin"), "rotation.origin");
            boolean rescale = rotNode.has("rescale") && rotNode.get("rescale").isBoolean() && rotNode.getBoolean("rescale");
            rotation = new ElementRotation(angle, axis, origin, rescale);
        }

        JsonValue facesNode = node.get("faces");
        if (facesNode == null || !facesNode.isObject()) {
            throw new ModelLoadException("Model file " + file.name() + " element " + name + " field faces must be object");
        }

        Map<FaceDirection, ModelFace> faces = new EnumMap<>(FaceDirection.class);
        for (JsonValue faceNode = facesNode.child; faceNode != null; faceNode = faceNode.next) {
            FaceDirection direction = parseFaceDirection(faceNode.name);
            ModelFace face = parseFace(file, name, faceNode);
            faces.put(direction, face);
        }

        return new ModelElement(name, from, to, rotation, faces);
    }

    private ModelFace parseFace(FileHandle file, String elementName, JsonValue faceNode) {
        JsonValue uvNode = faceNode.get("uv");
        if (uvNode == null || !uvNode.isArray() || uvNode.size != 4) {
            throw new ModelLoadException("Model file " + file.name() + " element " + elementName
                    + " face " + faceNode.name + " has invalid uv");
        }
        if (!uvNode.get(0).isNumber() || !uvNode.get(1).isNumber() || !uvNode.get(2).isNumber() || !uvNode.get(3).isNumber()) {
            throw new ModelLoadException("Model file " + file.name() + " element " + elementName
                    + " face " + faceNode.name + " uv must be numeric");
        }

        JsonValue texNode = faceNode.get("texture");
        if (texNode == null || !texNode.isString()) {
            throw new ModelLoadException("Model file " + file.name() + " element " + elementName
                    + " face " + faceNode.name + " texture ref missing");
        }

        return new ModelFace(
                (float) uvNode.getDouble(0),
                (float) uvNode.getDouble(1),
                (float) uvNode.getDouble(2),
                (float) uvNode.getDouble(3),
                texNode.asString());
    }

    private Vector3 parseVec3(FileHandle file, JsonValue node, String fieldName) {
        if (node == null || !node.isArray() || node.size != 3) {
            throw new ModelLoadException("Model file " + file.name() + " field " + fieldName + " must be [x,y,z]");
        }
        if (!node.get(0).isNumber() || !node.get(1).isNumber() || !node.get(2).isNumber()) {
            throw new ModelLoadException("Model file " + file.name() + " field " + fieldName + " must be numeric");
        }
        return new Vector3(
                (float) node.getDouble(0) * BLOCKBENCH_UNIT_SCALE,
                (float) node.getDouble(1) * BLOCKBENCH_UNIT_SCALE,
                (float) node.getDouble(2) * BLOCKBENCH_UNIT_SCALE);
    }

    private FaceDirection parseFaceDirection(String name) {
        String n = name.toLowerCase();
        return switch (n) {
            case "up" -> FaceDirection.TOP;
            case "down" -> FaceDirection.BOTTOM;
            case "north" -> SWAP_NORTH_SOUTH ? FaceDirection.SOUTH : FaceDirection.NORTH;
            case "south" -> SWAP_NORTH_SOUTH ? FaceDirection.NORTH : FaceDirection.SOUTH;
            case "east" -> SWAP_EAST_WEST ? FaceDirection.WEST : FaceDirection.EAST;
            case "west" -> SWAP_EAST_WEST ? FaceDirection.EAST : FaceDirection.WEST;
            default -> throw new ModelLoadException("Unknown face direction in model: " + name);
        };
    }
}
