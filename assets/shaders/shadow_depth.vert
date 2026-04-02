attribute vec3 a_position;

uniform mat4 u_lightSpaceMVP;

void main() {
    gl_Position = u_lightSpaceMVP * vec4(a_position, 1.0);
}
