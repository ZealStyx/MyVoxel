package com.zeal.voxel.block;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ResolvedBehaviour {
    public final String type;
    public final BehaviourDefinition base;
    public final Map<String, Object> overrides;

    public ResolvedBehaviour(String type, BehaviourDefinition base, Map<String, Object> overrides) {
        this.type = type;
        this.base = base;
        this.overrides = Collections.unmodifiableMap(new HashMap<>(overrides));
    }

    public float getFloat(String key, float defaultVal) {
        Object value = resolve(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return defaultVal;
    }

    public String getString(String key, String defaultVal) {
        Object value = resolve(key);
        if (value instanceof String str) {
            return str;
        }
        return defaultVal;
    }

    public boolean getBoolean(String key, boolean defaultVal) {
        Object value = resolve(key);
        if (value instanceof Boolean b) {
            return b;
        }
        return defaultVal;
    }

    private Object resolve(String key) {
        if (overrides.containsKey(key)) {
            return overrides.get(key);
        }
        return base.baseParams.get(key);
    }
}
