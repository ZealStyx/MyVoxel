package com.zeal.voxel.block;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BehaviourLoader {
    private final JsonReader jsonReader = new JsonReader();

    public Map<String, BehaviourDefinition> loadAll(FileHandle behavioursDir) {
        if (behavioursDir == null || !behavioursDir.exists() || !behavioursDir.isDirectory()) {
            throw new BlockLoadException("Behaviour directory not found: " + (behavioursDir == null ? "null" : behavioursDir.path()));
        }

        FileHandle[] files = behavioursDir.list((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            throw new BlockLoadException("No behaviour json files found in " + behavioursDir.path());
        }

        List<FileHandle> sorted = new ArrayList<>();
        for (FileHandle file : files) {
            sorted.add(file);
        }
        sorted.sort(Comparator.comparing(FileHandle::name));

        Map<String, BehaviourDefinition> byStem = new HashMap<>();
        Map<String, String> typeToFile = new HashMap<>();

        for (FileHandle file : sorted) {
            JsonValue root;
            try {
                root = jsonReader.parse(file);
            } catch (Exception e) {
                throw new BlockLoadException("Behaviour file " + file.name()
                        + " field root is invalid JSON. Hint: valid JSON object expected.", e);
            }

            if (root == null || !root.isObject()) {
                throw new BlockLoadException("Behaviour file " + file.name()
                        + " field root is invalid. Hint: top-level object required.");
            }

            JsonValue typeNode = root.get("type");
            if (typeNode == null || !typeNode.isString() || typeNode.asString().isBlank()) {
                throw new BlockLoadException("Behaviour file " + file.name()
                        + " field type is missing or invalid. Hint: provide non-empty string.");
            }
            String type = typeNode.asString();

            String previousFile = typeToFile.putIfAbsent(type, file.name());
            if (previousFile != null) {
                throw new BlockLoadException("Duplicate behaviour type '" + type
                        + "' in files " + previousFile + " and " + file.name());
            }

            Map<String, Object> params = new HashMap<>();
            JsonValue paramsNode = root.get("params");
            if (paramsNode != null) {
                if (!paramsNode.isObject()) {
                    throw new BlockLoadException("Behaviour file " + file.name()
                            + " field params has invalid value " + paramsNode
                            + ". Hint: params must be an object.");
                }
                for (JsonValue child = paramsNode.child; child != null; child = child.next) {
                    params.put(child.name, toJavaObject(child));
                }
            }

            String stem = stem(file.name());
            byStem.put(stem, new BehaviourDefinition(type, params, file.path()));
        }

        return byStem;
    }

    private String stem(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return name;
        }
        return name.substring(0, dot);
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
            Map<String, Object> out = new HashMap<>();
            for (JsonValue child = value.child; child != null; child = child.next) {
                out.put(child.name, toJavaObject(child));
            }
            return out;
        }
        return value.toString();
    }
}
