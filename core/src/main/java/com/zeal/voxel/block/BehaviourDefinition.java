package com.zeal.voxel.block;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BehaviourDefinition {
    public final String type;
    public final Map<String, Object> baseParams;
    public final String sourceFile;

    public BehaviourDefinition(String type, Map<String, Object> baseParams, String sourceFile) {
        this.type = type;
        this.baseParams = Collections.unmodifiableMap(new HashMap<>(baseParams));
        this.sourceFile = sourceFile;
    }

    public float getFloat(String key, float defaultVal) {
        Object value = baseParams.get(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return defaultVal;
    }

    public String getString(String key, String defaultVal) {
        Object value = baseParams.get(key);
        if (value instanceof String str) {
            return str;
        }
        return defaultVal;
    }

    public boolean getBoolean(String key, boolean defaultVal) {
        Object value = baseParams.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        return defaultVal;
    }
}
