#ifdef GL_ES
precision mediump float;
#endif

#include "brdf.glsl"

uniform sampler2D u_texture;

// PBR material
uniform float u_metallic;
uniform float u_roughness;
uniform vec3  u_emission;
uniform float u_emissiveMask;

// Lighting
uniform vec3 u_camPos;
uniform vec3 u_sunDir;
uniform vec3 u_sunColor;
uniform vec3 u_ambientColor;

// Fog
uniform vec4 u_fogColor;
uniform float u_fogStart;
uniform float u_fogEnd;

// Cascaded Shadow Maps
uniform sampler2D u_shadowMap0;
uniform sampler2D u_shadowMap1;
uniform sampler2D u_shadowMap2;
uniform sampler2D u_shadowMap3;
uniform mat4  u_lightSpaceMatrix0;
uniform mat4  u_lightSpaceMatrix1;
uniform mat4  u_lightSpaceMatrix2;
uniform mat4  u_lightSpaceMatrix3;
uniform float u_cascadeSplit0;
uniform float u_cascadeSplit1;
uniform float u_cascadeSplit2;
uniform float u_cascadeSplit3;
uniform float u_shadowBias0;
uniform float u_shadowBias1;
uniform float u_shadowBias2;
uniform float u_shadowBias3;

// Debug
uniform bool u_debugCascades;

// Override color (hit flash)
uniform vec4 u_overrideColor;

varying vec2 v_texCoord;
varying vec3 v_normal;
varying vec4 v_color;
varying vec3 v_worldPos;
varying float v_viewDepth;
varying vec3 v_viewNormal;

float sampleShadowMap(sampler2D shadowMap, mat4 lightSpaceMatrix, vec3 worldPos, float bias, float texelSize) {
    vec4 lsPos = lightSpaceMatrix * vec4(worldPos, 1.0);
    vec3 proj = lsPos.xyz / lsPos.w * 0.5 + 0.5;

    if (proj.x < 0.0 || proj.x > 1.0 || proj.y < 0.0 || proj.y > 1.0 || proj.z > 1.0) {
        return 1.0;
    }

    // PCF 3x3
    float shadow = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            float d = texture2D(shadowMap, proj.xy + vec2(float(x), float(y)) * texelSize).r;
            shadow += (proj.z - bias > d) ? 0.35 : 1.0;
        }
    }
    return shadow / 9.0;
}

float calcCsmShadow(vec3 worldPos, float viewDepth) {
    // If/else ladder for ES 2.0 compatibility (no dynamic sampler indexing)
    if (viewDepth < u_cascadeSplit0) {
        return sampleShadowMap(u_shadowMap0, u_lightSpaceMatrix0, worldPos, u_shadowBias0, 1.0 / 2048.0);
    } else if (viewDepth < u_cascadeSplit1) {
        return sampleShadowMap(u_shadowMap1, u_lightSpaceMatrix1, worldPos, u_shadowBias1, 1.0 / 2048.0);
    } else if (viewDepth < u_cascadeSplit2) {
        return sampleShadowMap(u_shadowMap2, u_lightSpaceMatrix2, worldPos, u_shadowBias2, 1.0 / 1024.0);
    } else {
        return sampleShadowMap(u_shadowMap3, u_lightSpaceMatrix3, worldPos, u_shadowBias3, 1.0 / 1024.0);
    }
}

vec3 getCascadeDebugColor(float viewDepth) {
    if (viewDepth < u_cascadeSplit0) return vec3(1.0, 0.2, 0.2);       // Red
    else if (viewDepth < u_cascadeSplit1) return vec3(0.2, 1.0, 0.2);  // Green
    else if (viewDepth < u_cascadeSplit2) return vec3(0.2, 0.2, 1.0);  // Blue
    else return vec3(1.0, 1.0, 0.2);                                    // Yellow
}

void main() {
    vec4 texel = texture2D(u_texture, v_texCoord);
    if (texel.a < 0.1) discard;

    vec3 albedo = texel.rgb;

    vec3 N = normalize(v_normal);
    vec3 V = normalize(u_camPos - v_worldPos);
    vec3 L = normalize(u_sunDir);
    vec3 H = normalize(V + L);

    float NdotL = max(dot(N, L), 0.0);
    float NdotV = max(dot(N, V), 0.001);
    float NdotH = max(dot(N, H), 0.0);
    float HdotV = max(dot(H, V), 0.0);

    // PBR: F0 is 0.04 for dielectrics, albedo for metals
    vec3 F0 = mix(vec3(0.04), albedo, u_metallic);

    float D = D_GGX(NdotH, u_roughness);
    float G = G_Smith(NdotV, NdotL, u_roughness);
    vec3  F = F_Schlick(HdotV, F0);

    vec3 specular = (D * G * F) / max(4.0 * NdotV * NdotL, 0.001);

    vec3 kD = (1.0 - F) * (1.0 - u_metallic);
    vec3 diffuse = kD * albedo / PI;

    // Shadow
    float shadow = calcCsmShadow(v_worldPos, v_viewDepth);

    vec3 Lo = (diffuse + specular) * u_sunColor * NdotL;

    // Ambient with baked AO from vertex color
    vec3 ambient = u_ambientColor * albedo * v_color.r;

    vec3 color = ambient + Lo * shadow + u_emission;

    // Reinhard tone mapping
    color = color / (color + vec3(1.0));
    // Gamma correction
    color = pow(color, vec3(1.0 / 2.2));

    // Distance fog
    float dist = length(v_worldPos - u_camPos);
    float fogFac = clamp((dist - u_fogStart) / (u_fogEnd - u_fogStart), 0.0, 1.0);
    color = mix(color, u_fogColor.rgb, fogFac);

    // Debug cascade visualization
    if (u_debugCascades) {
        color = mix(color, getCascadeDebugColor(v_viewDepth), 0.3);
    }

    // Hit flash override
    if (u_overrideColor.a > 0.0) {
        color = mix(color, u_overrideColor.rgb, u_overrideColor.a);
    }

    // Preserve texture alpha so transparent textures (e.g., water) render see-through.
    gl_FragColor = vec4(color, texel.a);
}
