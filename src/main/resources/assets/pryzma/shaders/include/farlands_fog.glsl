#ifndef FARLANDS_LOD_FOG_GLSL
#define FARLANDS_LOD_FOG_GLSL

float farlands_lod_fog(float sphericalDistance, float cylindricalDistance) {
    float haze = max(FarlandsParams.z * 16.0, 320.0) * 1.7320508;
    float environmental = linear_fog_value(sphericalDistance, FogEnvironmentalStart, FogEnvironmentalEnd);
    if (FarlandsCameraBlock.w == 0) {
        environmental = min(environmental, linear_fog_value(haze, FogEnvironmentalStart, FogEnvironmentalEnd));
    }
    float edge = linear_fog_value(cylindricalDistance, FogRenderDistanceStart, FogRenderDistanceEnd);
    return (1.0 - (1.0 - environmental) * (1.0 - edge)) * FogColor.a;
}

#endif
