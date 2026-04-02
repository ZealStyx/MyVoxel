#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
uniform float u_opacity;

void main() {
    gl_FragColor = vec4(v_color.rgb, v_color.a * u_opacity);
}
