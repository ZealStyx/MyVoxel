package com.zeal.voxel.render.shader;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public class ShaderUniform {
    public static void setMatrix4(ShaderProgram sp, String name, Matrix4 mat) {
        if (sp.isCompiled() && sp.hasUniform(name)) {
            sp.setUniformMatrix(name, mat);
        }
    }

    public static void setMatrix3(ShaderProgram sp, String name, Matrix3 mat) {
        if (sp.isCompiled() && sp.hasUniform(name)) {
            sp.setUniformMatrix(name, mat);
        }
    }

    public static void setVector3(ShaderProgram sp, String name, Vector3 vec) {
        if (sp.isCompiled() && sp.hasUniform(name)) {
            sp.setUniformf(name, vec);
        }
    }

    public static void setFloat(ShaderProgram sp, String name, float val) {
        if (sp.isCompiled() && sp.hasUniform(name)) {
            sp.setUniformf(name, val);
        }
    }

    public static void setInt(ShaderProgram sp, String name, int val) {
        if (sp.isCompiled() && sp.hasUniform(name)) {
            sp.setUniformi(name, val);
        }
    }

    public static void setTexture(ShaderProgram sp, String name, Texture tex, int unit) {
        if (sp.isCompiled() && sp.hasUniform(name)) {
            tex.bind(unit);
            sp.setUniformi(name, unit);
        }
    }
    
    public static void set4f(ShaderProgram sp, String name, float r, float g, float b, float a) {
        if (sp.isCompiled() && sp.hasUniform(name)) {
            sp.setUniformf(name, r, g, b, a);
        }
    }
}
