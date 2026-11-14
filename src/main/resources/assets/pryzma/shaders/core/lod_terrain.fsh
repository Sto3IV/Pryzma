#version 150

// Farlands LOD terrain.
// Incorporates Voxy-inspired stochastic Bayer dither dissolve at the chunk boundary (eliminating hard cut seams)
// and cellular alpha cutout discard on foliage (preventing solid plastic leaf slabs and allowing see-through canopies).
// Sub-block 16x16 texel micro-grain preserves high-frequency crispness under zoom and near inspection.

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

// 8x8 Bayer matrix for ordered stochastic dissolve at chunk transitions
const float BAYER8[64] = float[](
     0.0/64.0, 32.0/64.0,  8.0/64.0, 40.0/64.0,  2.0/64.0, 34.0/64.0, 10.0/64.0, 42.0/64.0,
    48.0/64.0, 16.0/64.0, 56.0/64.0, 24.0/64.0, 50.0/64.0, 18.0/64.0, 58.0/64.0, 26.0/64.0,
    12.0/64.0, 44.0/64.0,  4.0/64.0, 36.0/64.0, 14.0/64.0, 46.0/64.0,  6.0/64.0, 38.0/64.0,
    60.0/64.0, 28.0/64.0, 52.0/64.0, 20.0/64.0, 62.0/64.0, 30.0/64.0, 54.0/64.0, 22.0/64.0,
     3.0/64.0, 35.0/64.0, 11.0/64.0, 43.0/64.0,  1.0/64.0, 33.0/64.0,  9.0/64.0, 41.0/64.0,
    51.0/64.0, 19.0/64.0, 59.0/64.0, 27.0/64.0, 49.0/64.0, 17.0/64.0, 57.0/64.0, 25.0/64.0,
    15.0/64.0, 47.0/64.0,  7.0/64.0, 39.0/64.0, 13.0/64.0, 45.0/64.0,  5.0/64.0, 37.0/64.0,
    63.0/64.0, 31.0/64.0, 55.0/64.0, 23.0/64.0, 61.0/64.0, 29.0/64.0, 53.0/64.0, 21.0/64.0
);

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

uint hash3D(ivec3 b) {
    uint h = uint(b.x) * 73856093u ^ uint(b.y) * 19349663u ^ uint(b.z) * 83492791u;
    h ^= h >> 13u;
    h *= 0x85EBCA6Bu;
    h ^= h >> 16u;
    return h;
}

uint hashTexel(ivec3 b, ivec2 t) {
    uint h = hash3D(b) ^ (uint(t.x) * 0x27D4EB2Du + uint(t.y) * 0x165667B1u);
    h ^= h >> 15u;
    h *= 0xC2B2AE35u;
    h ^= h >> 16u;
    return h;
}

vec2 getSubCoord(vec3 pos, uint norm) {
    if (norm <= 1u) {
        return pos.xz;
    } else if (norm <= 3u) {
        return vec2(pos.x, pos.y);
    } else {
        return vec2(pos.z, pos.y);
    }
}

void main() {
    float horizontal = length(relativePos.xz);
    const float FADE_BAND = 16.0;
    float innerRadius = max(0.0, ClipRadius - FADE_BAND);

    // Seamless stochastic dissolve across the vanilla/LOD boundary
    if (horizontal < innerRadius) {
        discard;
    } else if (horizontal < ClipRadius + FADE_BAND) {
        float blend = (horizontal - innerRadius) / (2.0 * FADE_BAND);
        ivec2 ditherCoord = ivec2(gl_FragCoord.xy) & 7;
        float threshold = BAYER8[ditherCoord.x + (ditherCoord.y << 3)];
        if (blend < threshold) {
            discard;
        }
    }

    vec4 color = vertexColor * ColorModulator;

    if (material != MATERIAL_WATER) {
        ivec2 orig = UseInstancedOffset == 1 ? sectorOrigin : SectorOrigin;
        ivec2 block = orig + ivec2(floor(localPos.xz + INWARD[int(normalIndex)]));
        int blockY = int(floor(localPos.y)) - 2048;
        ivec3 block3D = ivec3(block.x, blockY, block.y);

        vec2 sub = getSubCoord(localPos, normalIndex);
        ivec2 texel = clamp(ivec2(floor(fract(sub) * 16.0)), 0, 15);

        if (material == MATERIAL_LEAVES) {
            ivec2 cell = texel / 2;
            uint clusterH = hashTexel(block3D, cell);
            uint pixelH = hashTexel(block3D, texel);

            // Voxy-inspired cellular cutout mask:
            // ~28% of texels are discarded as transparent voids between leaf clumps
            bool isVoid = ((clusterH & 15u) <= 3u) || (((clusterH & 15u) <= 5u) && ((pixelH & 3u) == 0u));
            if (isVoid) {
                discard;
            }

            // Volumetric leaf micro-depth and cluster illumination
            float leafShade = 0.84 + 0.32 * (float(pixelH & 255u) / 255.0);
            ivec2 intra = texel % 2;
            float clumpEdge = ((intra.x ^ intra.y) == 1) ? 0.96 : 1.04;
            color.rgb *= leafShade * clumpEdge;
        } else {
            float amplitude = 0.035;
            float noise = float(hashBlock(block) & 255u) / 255.0;
            // High-frequency sub-block grain preserving voxel clarity under optical zoom
            uint microH = hashTexel(block3D, texel);
            float grain = 0.97 + 0.06 * (float(microH & 255u) / 255.0);
            color.rgb *= (1.0 - amplitude + 2.0 * amplitude * noise) * grain;
        }
    }

    float fogDistance = length(relativePos);
    if (fogDistance > FogStart) {
        float fogValue = fogDistance < FogEnd ? smoothstep(FogStart, FogEnd, fogDistance) : 1.0;
        color.rgb = mix(color.rgb, FogColor.rgb, fogValue);
    }

    fragColor = color;
}
