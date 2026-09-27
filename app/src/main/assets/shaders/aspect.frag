precision highp float;
uniform sampler2D uInput;
uniform float uSourceAspect;
uniform float uTargetAspect;
varying vec2 vUv;
void main() {
    vec2 p = vUv - 0.5;
    vec2 fit = uSourceAspect > uTargetAspect ? vec2(1.0,uTargetAspect/uSourceAspect) : vec2(uSourceAspect/uTargetAspect,1.0);
    vec2 fg = p / fit + 0.5;
    if (fg.x >= 0.0 && fg.x <= 1.0 && fg.y >= 0.0 && fg.y <= 1.0) {
        gl_FragColor = texture2D(uInput,fg);
    } else {
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
}
