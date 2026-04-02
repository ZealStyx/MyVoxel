attribute vec4 a_position;
attribute vec3 a_normal;
attribute vec4 a_color;

uniform mat4 u_projTrans;
uniform mat4 u_worldTrans;

varying vec4 v_color;

void main() {
    v_color = a_color;
    gl_Position = u_projTrans * u_worldTrans * a_position;
}
