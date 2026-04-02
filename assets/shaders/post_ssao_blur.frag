#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_ssaoInput;
uniform vec2 u_texelSize;

varying vec2 v_texCoord;

void main() {
    // 4x4 box blur to remove SSAO noise
    float result = 0.0;
    for (int x = -2; x < 2; x++) {
        for (int y = -2; y < 2; y++) {
            vec2 offset = vec2(float(x), float(y)) * u_texelSize;
            result += texture2D(u_ssaoInput, v_texCoord + offset).r;
        }
    }
    result /= 16.0;

    gl_FragColor = vec4(result, result, result, 1.0);
}
