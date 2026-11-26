#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

uniform usamplerBuffer FarlandsWater;
uniform samplerBuffer FarlandsWaterSprites;
uniform sampler2D Sampler2;

#ifdef MULTIDRAW_TERRAIN
layout(location = 0) in ivec3 ChunkPosition;
layout(location = 1) in float ChunkVisibility;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) out float chunkVisibility;

void main() {
    uvec4 quad = texelFetch(FarlandsWater, gl_VertexIndex >> 2);
    int corner = gl_VertexIndex & 3;
    bool second = corner >= 2;
    bool upper = corner == 1 || corner == 2;
    uint level = (quad.y >> 24) & 7u;
    int scale = 1 << int(level);
    int cellX0 = int(quad.x & 127u);
    int cellZ0 = int((quad.x >> 7) & 127u);
    int x0 = cellX0 * scale;
    int z0 = cellZ0 * scale;
    int x1 = int((quad.x >> 14) & 127u) * scale;
    int z1 = int((quad.x >> 21) & 127u) * scale;
    uint face = (quad.x >> 28) & 7u;
    int low = int(quad.z & 65535u) - 32768;
    int high = int(quad.z >> 16) - 32768;
    vec3 origin = vec3(ChunkPosition - CameraBlockPos) + CameraOffset;
    bool reversed = face == 1u && (quad.x >> 31) != 0u && float(high) / 16.0 + origin.y > 0.0;
    int px;
    int py;
    int pz;
    if (face == 1u && !reversed) {
        px = second ? x1 : x0;
        pz = upper ? z1 : z0;
        py = high;
    } else if (face <= 1u) {
        px = second ? x0 : x1;
        pz = upper ? z1 : z0;
        py = face == 0u ? low : high;
    } else {
        py = upper ? high : low;
        if (face == 2u) {
            px = second ? x1 : x0;
            pz = z0;
        } else if (face == 3u) {
            px = second ? x0 : x1;
            pz = z1;
        } else if (face == 4u) {
            px = x0;
            pz = second ? z0 : z1;
        } else {
            px = x1;
            pz = second ? z1 : z0;
        }
    }
    uint light = (face >= 2u && upper) || face == 1u ? (quad.w >> 22) & 255u : (quad.w >> 14) & 255u;
    vec2 uv;
    if (((quad.y >> 27) & 1u) != 0u) {
        int patchCells = level <= 4u ? 16 >> int(level) : 4;
        int patchX = cellX0 / patchCells * 16;
        int patchZ = cellZ0 / patchCells * 16;
        vec4 tiled = texelFetch(FarlandsWaterSprites, 16384);
        uv = vec2(tiled.x + (tiled.z - tiled.x) * float(px - patchX) / 16.0, tiled.y + (tiled.w - tiled.y) * float(pz - patchZ) / 16.0);
    } else {
        vec4 flats = texelFetch(FarlandsWaterSprites, int(quad.w & 16383u));
        uv = vec2(corner < 2 ? flats.x : flats.z, (face < 2u) == upper ? flats.w : flats.y);
    }
    vec3 pos = vec3(float(px), float(py) / 16.0, float(pz)) + origin;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vec4 color = vec4(float((quad.y >> 16) & 255u), float((quad.y >> 8) & 255u), float(quad.y & 255u), 255.0) / 255.0;
    uint rawSky = (light >> 4) & 15u;
    uint skyLight = face == 1u ? 15u : max(rawSky, 4u);
    vertexColor = color * sample_lightmap(Sampler2, ivec2(int(light & 15u) * 16, int(skyLight) * 16));
    texCoord0 = uv;

    const float chunkFullyVisibleRange = 16.0;
    float dist = length(pos);
    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));
}
