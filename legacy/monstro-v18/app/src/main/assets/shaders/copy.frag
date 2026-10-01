precision highp float;
uniform sampler2D uInput;
varying vec2 vUv;
void main(){gl_FragColor=texture2D(uInput,vUv);}
