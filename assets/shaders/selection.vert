attribute vec4 a_position;
uniform mat4 u_projViewTrans;
uniform mat4 u_modelTrans;

void main() {
    gl_Position = u_projViewTrans * u_modelTrans * a_position;
}
