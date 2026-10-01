precision highp float;
uniform sampler2D uInput;
varying vec2 vUv;
void main() {
    gl_FragColor = texture2D(uInput, vec2(1.0-vUv.x, vUv.y));
}
