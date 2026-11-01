#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <the_sift:portal_fields.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(location = 0) in vec4 texProj0;
layout(location = 1) in vec3 viewPosition;
layout(location = 2) in float sphericalVertexDistance;
layout(location = 3) in float cylindricalVertexDistance;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 uv=texProj0.xy/max(texProj0.w,.0001);
    uv.x=(uv.x-.5)*ScreenSize.x/max(ScreenSize.y,1.0)*.65+.5;
    vec2 ray=viewPosition.xy/max(abs(viewPosition.z),1.0);
    vec3 color=siftPortalField(uv,ray,GameTime*1200.0);
    fragColor=apply_fog(vec4(color,1),sphericalVertexDistance,cylindricalVertexDistance,
        FogEnvironmentalStart,FogEnvironmentalEnd,FogRenderDistanceStart,FogRenderDistanceEnd,FogColor);
}
