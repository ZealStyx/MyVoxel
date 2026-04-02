#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_scene;
uniform sampler2D u_bloom;
uniform sampler2D u_ssao;
uniform float u_bloomStrength;

varying vec2 v_texCoord;

void main() {
    vec3 scene = texture2D(u_scene, v_texCoord).rgb;
    vec3 bloom = texture2D(u_bloom, v_texCoord).rgb;
    float ao   = texture2D(u_ssao, v_texCoord).r;

    // Multiply AO into ambient term only (darken, never brighten)
    scene *= ao;

    // Add bloom on top
    scene += bloom * u_bloomStrength;

    gl_FragColor = vec4(scene, 1.0);
}
