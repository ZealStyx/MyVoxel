package com.zeal.voxel.render.shader;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import java.util.EnumMap;
import java.util.Map;

public class ShaderManager {
    private final Map<ShaderPrograms, ShaderProgram> shaders = new EnumMap<>(ShaderPrograms.class);

    public void init() {
        for (ShaderPrograms program : ShaderPrograms.values()) {
            get(program);
        }
    }

    public ShaderProgram get(ShaderPrograms key) {
        ShaderProgram shader = shaders.get(key);
        if (shader == null) {
            shader = ShaderLoader.load(key);
            shaders.put(key, shader);
        }
        return shader;
    }

    public void dispose() {
        for (ShaderProgram shader : shaders.values()) {
            if (shader != null) {
                shader.dispose();
            }
        }
        shaders.clear();
    }
}
