package com.zeal.voxel.block;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BlockBehaviourRegistry {
    private final Map<String, BlockBehaviour> behaviours = new HashMap<>();
    private boolean locked = false;

    public void register(String type, BlockBehaviour behaviour) {
        if (locked) {
            throw new IllegalStateException("BlockBehaviourRegistry is locked; cannot register type '" + type + "'");
        }
        if (behaviours.containsKey(type)) {
            throw new IllegalStateException("Duplicate BlockBehaviour registration for type '" + type + "'");
        }
        behaviours.put(type, behaviour);
    }

    public boolean isRegistered(String type) {
        return behaviours.containsKey(type);
    }

    public BlockBehaviour get(String type) {
        BlockBehaviour behaviour = behaviours.get(type);
        if (behaviour == null) {
            throw new IllegalStateException("No BlockBehaviour registered for type '" + type + "'");
        }
        return behaviour;
    }

    public Set<String> getRegisteredTypes() {
        return Collections.unmodifiableSet(new HashSet<>(behaviours.keySet()));
    }

    public void lock() {
        locked = true;
    }

    public void registerDefaults() {
        register("thruster", new ThrusterBehaviour());
        register("gyroscope", new GyroscopeBehaviour());
    }
}
