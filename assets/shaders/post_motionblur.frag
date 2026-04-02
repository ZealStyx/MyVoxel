#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_scene;
uniform sampler2D u_velocity;
uniform float u_shutterSpeed;

varying vec2 v_texCoord;

void main() {
    vec2 velocity = texture2D(u_velocity, v_texCoord).rg;
    velocity *= u_shutterSpeed;

    vec3 result = texture2D(u_scene, v_texCoord).rgb;
    result += texture2D(u_scene, v_texCoord + velocity * (-0.5 + 1.0 / 7.0)).rgb;
    result += texture2D(u_scene, v_texCoord + velocity * (-0.5 + 2.0 / 7.0)).rgb;
    result += texture2D(u_scene, v_texCoord + velocity * (-0.5 + 3.0 / 7.0)).rgb;
    result += texture2D(u_scene, v_texCoord + velocity * (-0.5 + 4.0 / 7.0)).rgb;
    result += texture2D(u_scene, v_texCoord + velocity * (-0.5 + 5.0 / 7.0)).rgb;
    result += texture2D(u_scene, v_texCoord + velocity * (-0.5 + 6.0 / 7.0)).rgb;
    result += texture2D(u_scene, v_texCoord + velocity * 0.5).rgb;

    gl_FragColor = vec4(result / 8.0, 1.0);
}
