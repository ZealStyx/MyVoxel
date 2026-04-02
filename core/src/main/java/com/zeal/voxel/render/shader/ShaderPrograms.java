package com.zeal.voxel.render.shader;

public enum ShaderPrograms {
    VOXEL_WORLD("shaders/voxel.vert", "shaders/voxel.frag"),
    PHYSICS_BODY("shaders/physics_body.vert", "shaders/physics_body.frag"),
    SELECTION("shaders/selection.vert", "shaders/selection.frag"),
    SHADOW_DEPTH("shaders/shadow_depth.vert", "shaders/shadow_depth.frag"),
    POST_BLOOM("shaders/post_fullscreen.vert", "shaders/post_bloom.frag"),
    POST_MOTIONBLUR("shaders/post_fullscreen.vert", "shaders/post_motionblur.frag"),
    POST_COMPOSITE("shaders/post_fullscreen.vert", "shaders/post_composite.frag"),

    // PBR shaders
    VOXEL_PBR("shaders/voxel_pbr.vert", "shaders/voxel_pbr.frag"),
    PHYSICS_BODY_PBR("shaders/physics_body_pbr.vert", "shaders/physics_body_pbr.frag"),

    // SSAO post-processing
    POST_SSAO("shaders/post_fullscreen.vert", "shaders/post_ssao.frag"),
    POST_SSAO_BLUR("shaders/post_fullscreen.vert", "shaders/post_ssao_blur.frag"),
    GHOST("shaders/ghost.vert", "shaders/ghost.frag");

    public final String vertPath;
    public final String fragPath;

    ShaderPrograms(String vertPath, String fragPath) {
        this.vertPath = vertPath;
        this.fragPath = fragPath;
    }
}
