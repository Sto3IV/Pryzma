#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:terrainglobals.glsl>
#include <pryzma:farlands_info.glsl>
#include <pryzma:farlands_fog.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in float chunkVisibility;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(TextureSize);
    vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, texel) : sampleNearest(Sampler0, texCoord0, texel)) * vertexColor;
    color = mix(FogColor * vec4(1.0, 1.0, 1.0, color.a), color, chunkVisibility);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    float fogValue = FarlandsCameraFraction.w > 0.5 ? total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd) * FogColor.a
            : farlands_lod_fog(sphericalVertexDistance, cylindricalVertexDistance);
    fragColor = vec4(mix(color.rgb, FogColor.rgb, fogValue), color.a);
}
