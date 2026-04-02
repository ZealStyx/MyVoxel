#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_normalBuffer;  // view-space normals
uniform sampler2D u_depthBuffer;   // linear depth (packed in RGBA)
uniform sampler2D u_noise;         // 4x4 random rotation texture
uniform vec3      u_samples[64];   // hemisphere kernel
uniform mat4      u_projection;
uniform mat4      u_invProjection;
uniform vec2      u_screenSize;
uniform float     u_radius;
uniform float     u_bias;

varying vec2 v_texCoord;

vec3 reconstructPosition(vec2 tc, float depth) {
    // Reconstruct view-space position from screen UV and linear depth
    vec4 clipPos = vec4(tc * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = u_invProjection * clipPos;
    return viewPos.xyz / viewPos.w;
}

void main() {
    vec3 fragNormal = texture2D(u_normalBuffer, v_texCoord).rgb * 2.0 - 1.0;
    float fragDepth = texture2D(u_depthBuffer, v_texCoord).r;

    // Skip fragments with no geometry (depth ~0)
    if (fragDepth < 0.001) {
        gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);
        return;
    }

    vec3 fragPos = reconstructPosition(v_texCoord, fragDepth);

    // Tile the 4x4 noise texture across the screen
    vec3 randomVec = texture2D(u_noise, v_texCoord * u_screenSize / 4.0).rgb * 2.0 - 1.0;

    // Construct TBN (tangent-bitangent-normal) matrix
    vec3 tangent   = normalize(randomVec - fragNormal * dot(randomVec, fragNormal));
    vec3 bitangent = cross(fragNormal, tangent);
    mat3 TBN       = mat3(tangent, bitangent, fragNormal);

    float occlusion = 0.0;
    for (int i = 0; i < 64; i++) {
        // Orient sample in hemisphere around the surface normal
        vec3 s = TBN * u_samples[i];
        vec3 samplePos = fragPos + s * u_radius;

        // Project sample to screen space
        vec4 sClip = u_projection * vec4(samplePos, 1.0);
        vec2 sUV = sClip.xy / sClip.w * 0.5 + 0.5;

        // Sample the depth at this screen position
        float sampleDepth = texture2D(u_depthBuffer, sUV).r;
        vec3 sampleViewPos = reconstructPosition(sUV, sampleDepth);

        // Range check: only count samples within radius
        float rangeCheck = smoothstep(0.0, 1.0, u_radius / abs(fragPos.z - sampleViewPos.z));

        // If the sample is behind the geometry, it's occluded
        occlusion += (sampleViewPos.z >= samplePos.z + u_bias ? 1.0 : 0.0) * rangeCheck;
    }

    occlusion = 1.0 - (occlusion / 64.0);
    occlusion = clamp(occlusion, 0.0, 1.0);

    gl_FragColor = vec4(occlusion, occlusion, occlusion, 1.0);
}
