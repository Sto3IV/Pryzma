#version 150
#extension GL_ARB_explicit_attrib_location : enable

// Farlands LOD terrain (net.pryzma.lod.NativeLodRenderer). The vertex is the Distant Horizons vertex ABI that shader
// packs' dh_terrain programs read as well: vPosition = x, y + 2048, z relative to the sector's minimum corner and the
// light (block << 4 | sky) in w; iris_color = albedo x biome tint; irisExtra = material, normal index.
// ChunkOffset moves the sector to the camera and removes the y bias, as vanilla's terrain shaders offset chunks.
// Under GPU-driven MDI (UseInstancedOffset == 1), SectorOriginAttr is fetched per-instance at divisor 1 and camera-relative
// position is computed with high-precision block+fraction split.

layout(location = 0) in uvec4 vPosition;
layout(location = 1) in vec4 iris_color;
layout(location = 2) in uvec4 irisExtra;
layout(location = 3) in ivec2 SectorOriginAttr;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform ivec2 SectorOrigin;
uniform ivec3 CameraBlock;
uniform vec3 CameraFrac;
uniform int UseInstancedOffset;

out vec3 relativePos;
out vec3 localPos;
out vec4 vertexColor;
flat out uint material;
flat out uint normalIndex;
flat out ivec2 sectorOrigin;

// Vanilla's directional shade per face: down, up, north, south, west, east.
const float FACE_SHADE[6] = float[](0.5, 1.0, 0.8, 0.8, 0.6, 0.6);

// Vanilla's minecraft_sample_lightmap: UV2 holds block and sky light times 16.
vec4 sampleLightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp((vec2(uv) + vec2(8.0)) / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

void main() {
    localPos = vec3(vPosition.xyz);
    if (UseInstancedOffset == 1) {
        ivec3 o = ivec3(SectorOriginAttr.x, -2048, SectorOriginAttr.y) - CameraBlock;
        relativePos = localPos + (vec3(o) - CameraFrac);
        sectorOrigin = SectorOriginAttr;
    } else {
        relativePos = localPos + ChunkOffset;
        sectorOrigin = SectorOrigin;
    }
    gl_Position = ProjMat * ModelViewMat * vec4(relativePos, 1.0);
    uint light = vPosition.w & 255u;
    ivec2 uv2 = ivec2(int(light >> 4u) * 16, int(light & 15u) * 16);
    normalIndex = min(irisExtra.y, 5u);
    material = irisExtra.x;
    vertexColor = vec4(iris_color.rgb * FACE_SHADE[int(normalIndex)], iris_color.a) * sampleLightmap(Sampler2, uv2);
}
