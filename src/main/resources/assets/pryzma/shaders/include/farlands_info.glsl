#ifndef FARLANDS_LOD_INFO_GLSL
#define FARLANDS_LOD_INFO_GLSL

layout(std140) uniform FarlandsInfo {
    mat4 FarlandsModelView;
    vec4 FarlandsParams;
    ivec4 FarlandsCameraBlock;
    vec4 FarlandsCameraFraction;
};

#endif
