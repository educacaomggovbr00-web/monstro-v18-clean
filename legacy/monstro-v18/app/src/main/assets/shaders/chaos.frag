precision highp float;
uniform sampler2D uInput;
uniform sampler2D uHistory;
uniform float uTime;
uniform float uZoom;
uniform float uAutoZoom;
uniform float uGlitch;
uniform float uRgb;
uniform float uShake;
uniform float uStrobe;
uniform float uHue;
uniform float uVignette;
uniform float uBlurWeight;
varying vec2 vUv;

float noise(float n) { return fract(sin(n * 12.9898 + 78.233) * 43758.5453); }
vec3 source(vec2 uv) { return texture2D(uInput, clamp(uv, 0.0, 1.0)).rgb; }
vec3 rotateHue(vec3 c, float angle) {
    // Rotation around the neutral axis leaves gray pixels unchanged.
    vec3 axis = normalize(vec3(1.0));
    return c * cos(angle) + cross(axis, c) * sin(angle) + axis * dot(axis, c) * (1.0 - cos(angle));
}
void main() {
    float autoScale = 1.0 + uAutoZoom * 0.075 * (1.0 - cos(uTime * 0.8));
    float scale = uZoom * autoScale * (1.0 + uShake * 0.035);
    vec2 uv = (vUv - 0.5) / scale + 0.5;
    float tick = floor(uTime * 24.0);
    uv += uShake * 0.012 * vec2(sin(uTime * 43.0), sin(uTime * 57.0 + 0.9));
    float burst = step(0.77, noise(floor(uTime * 7.0)));
    float band = floor(uv.y * 18.0);
    uv.x += uGlitch * burst * (noise(band + tick) - 0.5) * 0.10 * step(0.5, noise(band + 3.0));
    float split = uRgb * (0.008 + 0.004 * sin(uTime * 4.0));
    vec3 color = vec3(source(uv + vec2(split, 0.0)).r, source(uv).g, source(uv - vec2(split, 0.0)).b);
    color = mix(color, rotateHue(color, uTime * 0.65), uHue);
    // Composite BEFORE final vignette/strobe, so neither accumulates in history.
    if (uBlurWeight > 0.0) {
        vec3 past = texture2D(uHistory, clamp(uv, 0.0, 1.0)).rgb;
        past = mix(past, rotateHue(past, uTime * 0.65), uHue);
        color = mix(color, past, uBlurWeight);
    }
    float edge = smoothstep(0.18, 0.71, length(vUv - 0.5));
    color *= 1.0 - uVignette * 0.65 * edge;
    float pulse = step(0.75, fract(uTime * 2.0));
    color = mix(color, vec3(1.0), uStrobe * pulse * 0.55);
    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
