#version 150

// Farlands LOD terrain. Fragments closer than ClipRadius (horizontally) belong to vanilla terrain and are
// discarded. Every block of a face gets its own brightness from a hash of its world position, strongly for foliage
// (crowns read as crowns, not as painted slabs) and faintly elsewhere; water stays even. Fog is vanilla's
// cylindrical linear fog, computed per fragment: LOD quads span up to a whole sector, too large for a per-vertex
// distance. Under GPU-driven MDI (UseInstancedOffset == 1), sectorOrigin is received flat from the vertex shader.

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float ClipRadius;
uniform ivec2 SectorOrigin;
uniform int UseInstancedOffset;

in vec3 relativePos;
in vec3 localPos;
in vec4 vertexColor;
flat in uint material;
flat in uint normalIndex;
flat in ivec2 sectorOrigin;

out vec4 fragColor;

const uint MATERIAL_LEAVES = 1u;
const uint MATERIAL_WATER = 12u;
// Half a block into the face's own block, per normal index: down, up, north, south, west, east.
const vec2 INWARD[6] = vec2[](vec2(0.0), vec2(0.0), vec2(0.0, 0.5), vec2(0.0, -0.5), vec2(0.5, 0.0), vec2(-0.5, 0.0));

uint hashBlock(ivec2 block) {
    uint h = uint(block.x) * 0x27D4EB2Du + uint(block.y) * 0x165667B1u;
    h ^= h >> 15u;
    h *= 0x85EBCA6Bu;
    h ^= h >> 13u;
    h *= 0xC2B2AE35u;
    h ^= h >> 16u;
    return h;
}

void main() {
    float horizontal = length(relativePos.xz);
    if (horizontal < ClipRadius) {
        discard;
    }
    vec4 color = vertexColor * ColorModulator;
    if (material != MATERIAL_WATER) {
        ivec2 orig = UseInstancedOffset == 1 ? sectorOrigin : SectorOrigin;
        ivec2 block = orig + ivec2(floor(localPos.xz + INWARD[int(normalIndex)]));
        float amplitude = material == MATERIAL_LEAVES ? 0.14 : 0.035;
        float noise = float(hashBlock(block) & 255u) / 255.0;
        color.rgb *= 1.0 - amplitude + 2.0 * amplitude * noise;
    }
    float fogDistance = length(relativePos);
    if (fogDistance > FogStart) {
        float fogValue = fogDistance < FogEnd ? smoothstep(FogStart, FogEnd, fogDistance) : 1.0;
        color.rgb = mix(color.rgb, FogColor.rgb, fogValue);
    }
    fragColor = color;
}
