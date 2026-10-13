#version 150

// Farlands LOD terrain (net.pryzma.lod.NativeLodRenderer). The vertex is the Distant Horizons vertex ABI that shader
// packs' dh_terrain programs read as well: vPosition = x, y + 2048, z relative to the sector's minimum corner and the
// light (block << 4 | sky) in w; iris_color = albedo x biome tint; irisExtra = material, normal index.
// ChunkOffset moves the sector to the camera and removes the y bias, as vanilla's terrain shaders offset chunks.

in uvec4 vPosition;
in vec4 iris_color;
in uvec4 irisExtra;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;

out vec3 relativePos;
out vec3 localPos;
out vec4 vertexColor;
flat out uint material;
flat out uint normalIndex;

// Vanilla's directional shade per face: down, up, north, south, west, east.
const float FACE_SHADE[6] = float[](0.5, 1.0, 0.8, 0.8, 0.6, 0.6);

// Vanilla's minecraft_sample_lightmap: UV2 holds block and sky light times 16.
vec4 sampleLightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp(vec2(uv) / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

void main() {
    localPos = vec3(vPosition.xyz);
    relativePos = localPos + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(relativePos, 1.0);
    uint light = vPosition.w & 255u;
    ivec2 uv2 = ivec2(int(light >> 4u) * 16, int(light & 15u) * 16);
    normalIndex = min(irisExtra.y, 5u);
    material = irisExtra.x;
    vertexColor = vec4(iris_color.rgb * FACE_SHADE[int(normalIndex)], iris_color.a) * sampleLightmap(Sampler2, uv2);
}
