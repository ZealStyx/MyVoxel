#ifdef GL_ES
precision mediump float;
#endif

uniform vec4 u_lineColor;
uniform float u_time;

void main() {
    float alpha = 0.6 + 0.4 * sin(u_time * 3.0);
    gl_FragColor = vec4(u_lineColor.rgb, alpha);
}
