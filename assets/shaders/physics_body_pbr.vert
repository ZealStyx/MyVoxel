attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec2 a_texCoord0;
attribute vec4 a_color;

uniform mat4 u_projViewTrans;
uniform mat4 u_modelTrans;
uniform mat4 u_viewTrans;
uniform mat3 u_normalMat;

varying vec2 v_texCoord;
varying vec3 v_normal;
varying vec4 v_color;
varying vec3 v_worldPos;
varying float v_viewDepth;
varying vec3 v_viewNormal;

void main() {
    vec4 worldPos = u_modelTrans * vec4(a_position, 1.0);
    vec4 viewPos = u_viewTrans * worldPos;
    gl_Position = u_projViewTrans * worldPos;

    v_texCoord = a_texCoord0;
    v_normal = normalize(u_normalMat * a_normal);
    v_color = a_color;
    v_worldPos = worldPos.xyz;

    // For CSM cascade selection and SSAO
    v_viewDepth = -viewPos.z;
    v_viewNormal = normalize(mat3(u_viewTrans) * v_normal);
}
