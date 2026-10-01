precision highp float;
uniform sampler2D uInput;
uniform float uSourceAspect;
uniform float uTargetAspect;
uniform float uBackgroundMode;
varying vec2 vUv;

void main() {
    vec2 p = vUv - 0.5;
    vec2 fit = uSourceAspect > uTargetAspect ? vec2(1.0,uTargetAspect/uSourceAspect) : vec2(uSourceAspect/uTargetAspect,1.0);
    vec2 fg = p / fit + 0.5;
    if (fg.x >= 0.0 && fg.x <= 1.0 && fg.y >= 0.0 && fg.y <= 1.0) {
        gl_FragColor = texture2D(uInput,fg);
        return;
    }

    if (uBackgroundMode > 1.5) {
        vec2 cell = floor(gl_FragCoord.xy / 28.0);
        float checker = mod(cell.x + cell.y, 2.0);
        vec3 a = vec3(0.035,0.035,0.055);
        vec3 b = vec3(0.085,0.055,0.115);
        gl_FragColor = vec4(mix(a,b,checker),1.0);
        return;
    }
    if (uBackgroundMode > 0.5) {
        gl_FragColor = vec4(0.025,0.025,0.035,1.0);
        return;
    }

    vec2 cover = uSourceAspect > uTargetAspect ? vec2(uTargetAspect/uSourceAspect,1.0) : vec2(1.0,uSourceAspect/uTargetAspect);
    vec2 uv = p * cover + 0.5;
    vec3 sum = vec3(0.0); float weight = 0.0;
    for (int y=-2;y<=2;y++) for (int x=-2;x<=2;x++) {
        float w = exp(-float(x*x+y*y)/3.0);
        sum += texture2D(uInput,clamp(uv+vec2(float(x),float(y))*0.014,0.0,1.0)).rgb*w;
        weight += w;
    }
    gl_FragColor = vec4(sum/weight*0.65,1.0);
}
