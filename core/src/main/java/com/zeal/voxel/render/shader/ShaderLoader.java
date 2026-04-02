package com.zeal.voxel.render.shader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

public class ShaderLoader {

    /** Resolves #include directives in shader source code. */
    private static String resolveIncludes(String source) {
        StringBuilder result = new StringBuilder();
        for (String line : source.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include")) {
                // Extract filename from #include "filename"
                int start = trimmed.indexOf('"');
                int end = trimmed.lastIndexOf('"');
                if (start >= 0 && end > start) {
                    String filename = trimmed.substring(start + 1, end);
                    FileHandle includeFile = Gdx.files.internal("shaders/" + filename);
                    if (includeFile.exists()) {
                        result.append("// --- BEGIN INCLUDE: ").append(filename).append(" ---\n");
                        result.append(includeFile.readString());
                        result.append("\n// --- END INCLUDE: ").append(filename).append(" ---\n");
                    } else {
                        Gdx.app.error("ShaderLoader", "Include file not found: shaders/" + filename);
                        // Keep the line as a comment so the shader still compiles (with errors in lighting)
                        result.append("// MISSING INCLUDE: ").append(filename).append("\n");
                    }
                } else {
                    result.append(line).append("\n");
                }
            } else {
                result.append(line).append("\n");
            }
        }
        return result.toString();
    }

    public static ShaderProgram load(ShaderPrograms program) {
        String vertCode = Gdx.files.internal(program.vertPath).readString();
        String fragCode = Gdx.files.internal(program.fragPath).readString();

        // Resolve #include directives
        vertCode = resolveIncludes(vertCode);
        fragCode = resolveIncludes(fragCode);

        ShaderProgram.pedantic = false;
        ShaderProgram shader = new ShaderProgram(vertCode, fragCode);

        if (!shader.isCompiled()) {
            throw new ShaderCompileException("Failed to compile shader [" + program.name() + "]:\n" + 
                "Vert Path: " + program.vertPath + "\n" +
                "Frag Path: " + program.fragPath + "\n" +
                "Error Log: \n" + shader.getLog());
        }

        return shader;
    }
}
