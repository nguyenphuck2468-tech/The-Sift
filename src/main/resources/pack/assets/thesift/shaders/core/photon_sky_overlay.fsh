#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 sampled = texture(Sampler0, texCoord0);
    if (sampled.a <= 0.001) {
        discard;
    }
    fragColor = sampled * ColorModulator;
}
