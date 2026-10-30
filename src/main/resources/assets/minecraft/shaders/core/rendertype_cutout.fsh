#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

vec4 sampleAtlas(sampler2D source, vec2 uv) {
    vec2 pixelSize = 1.0 / vec2(textureSize(source, 0));
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = max(sqrt(du * du + dv * dv), vec2(1.0e-5));

    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5;
    vec2 texelOffset = uvTexelCoords - texelCenter;

    texelOffset = (texelOffset - 0.5) * pixelSize / texelScreenSize + 0.5;
    texelOffset = clamp(texelOffset, 0.0, 1.0);

    vec2 clampedUV = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(source, clampedUV, du, dv);
}

void main() {
    vec4 color = sampleAtlas(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
