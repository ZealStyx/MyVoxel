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

// Rim lighting
uniform vec3 u_rimColor;
uniform float u_rimPower;

// Emissive (1.0 for thrusters, 0.0 otherwise)
uniform float u_emissive;

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

    // Rim lighting
    vec3 viewDir = normalize(u_camPos - v_worldPos);
    float rim = 1.0 - max(dot(viewDir, normal), 0.0);
    rim = pow(rim, u_rimPower);
    vec3 rimContrib = u_rimColor * rim;

    vec3 finalColor = (light + rimContrib) * texel.rgb;

    // Distance fog
    float dist = length(v_worldPos - u_camPos);
    float fogFac = clamp((dist - u_fogStart) / (u_fogEnd - u_fogStart), 0.0, 1.0);

    vec3 fogged = mix(finalColor, u_fogColor.rgb, fogFac);

    // Emissive mask stored in alpha for bloom extraction
    gl_FragColor = vec4(fogged, u_emissive);
}
