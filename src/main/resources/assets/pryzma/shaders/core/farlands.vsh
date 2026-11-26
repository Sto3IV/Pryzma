#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <pryzma:farlands_info.glsl>

#ifdef FARLANDS_COMPACT
uniform usamplerBuffer FarlandsCompact;
layout(location = 0) in vec4 TileOrigin;
layout(location = 1) in uvec4 TileExtra;
#else
uniform samplerBuffer FarlandsTiles;
uniform usamplerBuffer FarlandsQuads;
#endif
uniform sampler2D Sampler2;

layout(location = 0) out vec3 localPos;
layout(location = 1) out vec4 litAo;
layout(location = 2) flat out uint slimQuad;
layout(location = 3) flat out uint packedTint;
layout(location = 4) flat out vec3 tileOffset;

void main() {
#ifdef FARLANDS_COMPACT
    uvec2 packedQuad = texelFetch(FarlandsCompact, gl_VertexIndex >> 2).xy;
    uint packedFace = (packedQuad.x >> 24) & 7u;
    uint materialIndex;
    uvec4 quad;
    if (packedFace < 2u) {
        materialIndex = (packedQuad.x >> 28) | (((packedQuad.y >> 21) & 2047u) << 4);
        quad.x = packedQuad.x & 268435455u;
        quad.z = ((packedQuad.y & 8191u) + TileExtra.x) | (((packedQuad.y >> 13) & 255u) << 16);
    } else if (packedFace < 4u) {
        materialIndex = ((packedQuad.x >> 18) & 63u) | ((packedQuad.x >> 28) << 6) | ((packedQuad.y >> 26) << 10);
        quad.x = packedQuad.x & 251920383u;
        quad.z = ((packedQuad.y & 8191u) + TileExtra.x) | ((((packedQuad.y >> 13) & 8191u) + TileExtra.x) << 16);
    } else {
        materialIndex = ((packedQuad.x >> 12) & 63u) | ((packedQuad.x >> 28) << 6) | ((packedQuad.y >> 26) << 10);
        quad.x = packedQuad.x & 268177407u;
        quad.z = ((packedQuad.y & 8191u) + TileExtra.x) | ((((packedQuad.y >> 13) & 8191u) + TileExtra.x) << 16);
    }
    uvec2 material = texelFetch(FarlandsCompact, int(TileExtra.y + materialIndex)).xy;
    quad.x |= ((material.y >> 20) & 15u) << 28;
    quad.y = material.x;
    quad.w = (material.y & 16383u) | (((material.y >> 14) & 63u) << 26);
    vec4 tile = TileOrigin;
#else
    uvec4 quad = texelFetch(FarlandsQuads, gl_VertexIndex >> 2);
    uint slot = (quad.w >> 14) & 4095u;
    vec4 tile = texelFetch(FarlandsTiles, int(slot));
#endif
    uint corner = uint(gl_VertexIndex & 3);
    uint face = (quad.x >> 24) & 7u;
    bool mirrored = ((quad.x >> 27) & 1u) != 0u;
    uint packedTile = uint(tile.w + 0.5);
    uint tileSize = packedTile >> 4;
    uint cellSize = tileSize >> 6;
    uint x0 = (quad.x & 63u) * cellSize;
    uint z0 = ((quad.x >> 6) & 63u) * cellSize;
    uint x1 = x0 + (((quad.x >> 12) & 63u) + 1u) * cellSize;
    uint z1 = z0 + (((quad.x >> 18) & 63u) + 1u) * cellSize;
    bool second = corner >= 2u;
    bool upper = corner == 1u || corner == 2u;
    uint low = quad.z & 65535u;
    uint high = quad.z >> 16;
    uint px = x0;
    uint py = low;
    uint pz = z0;
    if (face < 2u) {
        px = second != (face == 0u) ? x1 : x0;
        pz = upper ? z1 : z0;
    } else {
        if (face >= 6u) {
            float crossX = tile.x + float(x0 + x1) * 0.5;
            float crossZ = tile.z + float(z0 + z1) * 0.5;
            mirrored = mirrored != (face == 6u ? crossX > crossZ : crossX + crossZ < 0.0);
        }
        bool tail = second != mirrored;
        py = upper ? high : low;
        if (face == 2u) {
            px = tail ? x1 : x0;
            pz = z0;
        } else if (face == 3u) {
            px = tail ? x0 : x1;
            pz = z1;
        } else if (face == 4u) {
            px = x0;
            pz = tail ? z0 : z1;
        } else if (face == 5u) {
            px = x1;
            pz = tail ? z1 : z0;
        } else if (face == 6u) {
            px = tail ? x1 : x0;
            pz = tail ? z1 : z0;
        } else {
            px = tail ? x1 : x0;
            pz = tail ? z0 : z1;
        }
    }
    vec3 local = vec3(float(px), float(py) / 16.0 - 2048.0, float(pz));
    vec3 pos = vec3(tile.x + local.x, local.y - tile.y, tile.z + local.z);
    gl_Position = ProjMat * FarlandsModelView * vec4(pos, 1.0);
    vec4 pushed = ProjMat * FarlandsModelView * vec4(pos * 1.0005, 1.0);
    gl_Position.z = pushed.z / pushed.w * gl_Position.w;

    uint light = quad.y >> 24;
    if (!upper) {
        light = (light & 15u) | ((quad.x >> 28) << 4);
    }
    uint rawSky = (light >> 4) & 15u;
    uint skyLight = face == 1u ? max(rawSky, 15u) : max(rawSky, 4u);
    ivec2 lightUv = ivec2(int(light & 15u) * 16, int(skyLight) * 16);
    vec4 lit = sample_lightmap(Sampler2, lightUv);
    uint flags = quad.w >> 26;
    float ao = 1.0;
    if (face < 2u) {
        ao = float(5u - ((quad.z >> (16u + corner * 2u)) & 3u)) * 0.2;
    }
    litAo = vec4(lit.rgb, ao);
    localPos = local;
    tileOffset = tile.xyz;
    slimQuad = (quad.w & 16383u) | (face << 14) | (((flags & 7u) | ((flags >> 3) << 4)) << 17) | ((packedTile & 15u) << 24) | ((uint(log2(float(tileSize)) + 0.5) - 6u) << 28);
    packedTint = quad.y & 16777215u;
}
