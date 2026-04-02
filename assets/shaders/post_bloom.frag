#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_scene;
uniform vec2 u_texelSize;
uniform int u_horizontal;

varying vec2 v_texCoord;

void main() {
    vec4 sceneSample = texture2D(u_scene, v_texCoord);

    // u_horizontal == 2: bright-pass extraction mode
    if (u_horizontal == 2) {
        float emissive = sceneSample.a;
        if (emissive < 0.5) {
            gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
        } else {
            gl_FragColor = vec4(sceneSample.rgb * 2.0, 1.0);
        }
        return;
    }

    // Gaussian blur pass (u_horizontal == 1: horizontal, 0: vertical)
    vec2 dir;
    if (u_horizontal == 1) {
        dir = vec2(u_texelSize.x, 0.0);
    } else {
        dir = vec2(0.0, u_texelSize.y);
    }

    // 9-tap Gaussian weights (sigma ~2)
    vec3 result = texture2D(u_scene, v_texCoord).rgb * 0.170;
    result += texture2D(u_scene, v_texCoord + dir * 1.0).rgb * 0.121;
    result += texture2D(u_scene, v_texCoord - dir * 1.0).rgb * 0.121;
    result += texture2D(u_scene, v_texCoord + dir * 2.0).rgb * 0.065;
    result += texture2D(u_scene, v_texCoord - dir * 2.0).rgb * 0.065;
    result += texture2D(u_scene, v_texCoord + dir * 3.0).rgb * 0.028;
    result += texture2D(u_scene, v_texCoord - dir * 3.0).rgb * 0.028;
    result += texture2D(u_scene, v_texCoord + dir * 4.0).rgb * 0.0093;
    result += texture2D(u_scene, v_texCoord - dir * 4.0).rgb * 0.0093;

    gl_FragColor = vec4(result, 1.0);
}
