#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform vec3 u_sunDir;
uniform vec3 u_sunColor;
uniform vec3 u_ambientCol;
uniform vec4 u_fogColor;
uniform float u_fogStart;
uniform float u_fogEnd;
uniform vec3 u_camPos;

// Shadow mapping
uniform sampler2D u_shadowMap;
uniform mat4 u_lightSpaceMatrix;
uniform float u_shadowBias;

varying vec2 v_texCoord;
varying vec3 v_normal;
varying vec4 v_color;
varying vec3 v_worldPos;

float calcShadow() {
    vec4 fragLightSpace = u_lightSpaceMatrix * vec4(v_worldPos, 1.0);
    vec3 projCoords = fragLightSpace.xyz / fragLightSpace.w;
    projCoords = projCoords * 0.5 + 0.5;

    // Outside shadow frustum — fully lit
    if (projCoords.x < 0.0 || projCoords.x > 1.0 ||
        projCoords.y < 0.0 || projCoords.y > 1.0 ||
        projCoords.z > 1.0) {
        return 1.0;
    }

    float currentDepth = projCoords.z;

    // PCF 3x3
    float shadow = 0.0;
    vec2 texelSize = vec2(1.0 / 2048.0);
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            float pcfDepth = texture2D(u_shadowMap, projCoords.xy + vec2(x, y) * texelSize).r;
            shadow += (currentDepth - u_shadowBias > pcfDepth) ? 0.35 : 1.0;
        }
    }
    shadow /= 9.0;
    return shadow;
}

void main() {
    vec3 normal = normalize(v_normal);
    float diff = max(dot(normal, u_sunDir), 0.0);

    float shadow = calcShadow();

    // Shadow on diffuse only, not ambient
    vec3 light = u_ambientCol + u_sunColor * diff * shadow;

    // AO from vertex color R channel
    light *= v_color.r;

    vec4 texel = texture2D(u_texture, v_texCoord);
    if (texel.a < 0.1) discard;

    vec3 finalColor = light * texel.rgb;

    // Distance fog (applied after AO and shadow)
    float dist = length(v_worldPos - u_camPos);
    float fogFac = clamp((dist - u_fogStart) / (u_fogEnd - u_fogStart), 0.0, 1.0);

    gl_FragColor = mix(vec4(finalColor, 1.0), u_fogColor, fogFac);
}
