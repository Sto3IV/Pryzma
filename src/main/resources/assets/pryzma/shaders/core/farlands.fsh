#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <pryzma:farlands_info.glsl>
#include <pryzma:farlands_fog.glsl>

uniform sampler2D Sampler0;
uniform samplerBuffer FarlandsSprites;
#ifdef FARLANDS_CLIP
uniform usamplerBuffer FarlandsMask;
#endif

layout(location = 0) in vec3 localPos;
layout(location = 1) in vec4 litAo;
layout(location = 2) flat in uint slimQuad;
layout(location = 3) flat in uint packedTint;
layout(location = 4) flat in vec3 tileOffset;

layout(location = 0) out vec4 fragColor;

float faceShade(uint face) {
    if (face >= 6u) {
        return 0.9;
    }
    if (face == 0u) {
        return 0.5;
    }
    if (face == 1u) {
        return 1.0;
    }
    if (face < 4u) {
        return 0.8;
    }
    return 0.6;
}

vec2 faceCoords(uint face, vec3 p) {
    if (face >= 6u) {
        return vec2(p.x, -p.y);
    }
    if (face < 2u) {
        return p.xz;
    }
    if (face < 4u) {
        return vec2(p.x, -p.y);
    }
    return vec2(p.z, -p.y);
}

void main() {
    uint spriteIndex = slimQuad & 16383u;
    uint faceIndex = (slimQuad >> 14) & 7u;
    uint materialFlags = (slimQuad >> 17) & 127u;
    uint quadMask = (slimQuad >> 24) & 15u;
    float halfSize = float(64u << ((slimQuad >> 28) & 7u)) * 0.5;
    vec3 relativePos = localPos + vec3(tileOffset.x, -tileOffset.y, tileOffset.z);
    float sphericalVertexDistance = fog_spherical_distance(relativePos);
    float cylindricalVertexDistance = fog_cylindrical_distance(relativePos);
    float shade = faceShade(faceIndex);
    vec3 tintColor = vec3(float((packedTint >> 16) & 255u), float((packedTint >> 8) & 255u), float(packedTint & 255u)) / 255.0;
    vec4 vertexColor = vec4(tintColor * shade * litAo.a * litAo.rgb, 1.0);
    vec3 vertexLight = vec3(shade) * litAo.rgb;
    if (quadMask != 15u) {
        uint quadrant = (localPos.x >= halfSize ? 1u : 0u) + (localPos.z >= halfSize ? 2u : 0u);
        if (((quadMask >> quadrant) & 1u) == 0u) {
            discard;
        }
    }
#ifdef FARLANDS_CLIP
    int reach = max(int(FarlandsParams.z) - 1, 1);
    vec3 inward = vec3(0.0);
    if (faceIndex == 0u) {
        inward.y = 0.5;
    } else if (faceIndex == 1u) {
        inward.y = -0.5;
    } else if (faceIndex == 2u) {
        inward.z = 0.5;
    } else if (faceIndex == 3u) {
        inward.z = -0.5;
    } else if (faceIndex == 4u) {
        inward.x = 0.5;
    } else if (faceIndex == 5u) {
        inward.x = -0.5;
    }
    ivec3 block = FarlandsCameraBlock.xyz + ivec3(floor(relativePos + FarlandsCameraFraction.xyz + inward));
    ivec2 chunk = block.xz >> 4;
    ivec2 offset = chunk - (FarlandsCameraBlock.xz >> 4);
    int section = block.y >> 4;
    if (offset.x * offset.x + offset.y * offset.y <= reach * reach && abs(section - (FarlandsCameraBlock.y >> 4)) <= reach) {
        int bit = section - int(FarlandsParams.w);
        if (bit >= 0 && bit < 64) {
            uint word = texelFetch(FarlandsMask, ((chunk.y & 127) * 128 + (chunk.x & 127)) * 2 + (bit >> 5)).r;
            if (((word >> uint(bit & 31)) & 1u) != 0u) {
                discard;
            }
        }
    }
#endif

    vec4 rect = texelFetch(FarlandsSprites, int(spriteIndex) * 2);
    vec4 average = texelFetch(FarlandsSprites, int(spriteIndex) * 2 + 1);
    bool layered = average.a > 3.5;
    float layerPacked = average.a - 4.0;
    int layerIndex = layered ? int(floor(layerPacked)) : 0;
    vec4 layerRect = vec4(0.0);
    vec4 layerAverage = vec4(0.0);
    if (layered) {
        layerRect = texelFetch(FarlandsSprites, layerIndex * 2);
        layerAverage = texelFetch(FarlandsSprites, layerIndex * 2 + 1);
    }
    float layer = layered ? fract(layerPacked) * 2.0 : 0.0;
    vec3 layerColor = layerAverage.rgb;
    vec3 color = (materialFlags & 1u) != 0u ? average.rgb * 0.62 : average.rgb;
    float alpha = layered ? 1.0 : min(average.a, 1.0);
    bool plant = (materialFlags & 4u) != 0u;
    if (plant && FarlandsParams.y < 0.5) {
        discard;
    }
    uint segments = (materialFlags >> 4) & 7u;
    if (segments != 0u && segments < 4u) {
        vec2 cell = fract(localPos.xz);
        uint quarter = (cell.x >= 0.5 ? 1u : 0u) + (cell.y >= 0.5 ? 2u : 0u);
        if (quarter >= segments) {
            discard;
        }
    }
    if (FarlandsParams.y > 0.5) {
        vec2 st = faceCoords(faceIndex, localPos);
        vec2 stdx = dFdx(st);
        vec2 stdy = dFdy(st);
        vec2 size = rect.zw - rect.xy;
        vec2 uv = rect.xy + fract(st) * size;
        vec2 dx = stdx * size;
        vec2 dy = stdy * size;
        vec4 texel = textureGrad(Sampler0, uv, dx, dy);
        if (plant && texel.a < 0.5) {
            discard;
        }
        bool cutout = (materialFlags & 1u) != 0u;
        vec3 shaded = cutout ? average.rgb * 0.62 : average.rgb;
        vec3 sampled = plant || texel.a >= 0.5 ? texel.rgb : average.rgb * 0.42;
        float texels = max(length(dx), length(dy)) * float(textureSize(Sampler0, 0).x);
        float lod = log2(max(texels, 1e-6));
        float blend = smoothstep(FarlandsParams.x - 0.5, FarlandsParams.x + 1.5, lod);
        color = mix(sampled, shaded, blend);
        alpha = mix(texel.a, alpha, blend);
        if (layered) {
            vec2 layerSize = layerRect.zw - layerRect.xy;
            vec4 layerTexel = textureGrad(Sampler0, layerRect.xy + fract(st) * layerSize, stdx * layerSize, stdy * layerSize);
            layerColor = mix(layerTexel.rgb, layerAverage.rgb, blend);
            layer = mix(layerTexel.a >= 0.5 ? 1.0 : 0.0, layer, blend);
            alpha = 1.0;
        }
    }
    bool legacyFog = FarlandsCameraFraction.w > 0.5;
    float fogValue = legacyFog ? total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd) * FogColor.a
            : farlands_lod_fog(fog_spherical_distance(relativePos), fog_cylindrical_distance(relativePos));
#ifdef FARLANDS_FLUID
    vec4 shaded = vec4(color, alpha) * vertexColor;
    if (legacyFog) {
        shaded.a *= 1.0 - fogValue;
    }
#else
    vec4 shaded = vec4(color, 1.0) * vertexColor;
    if (layered) {
        shaded = vec4(mix(color * vertexLight, layerColor * vertexColor.rgb, layer), vertexColor.a);
    }
#endif
    fragColor = vec4(mix(shaded.rgb, FogColor.rgb, fogValue), shaded.a);
}
