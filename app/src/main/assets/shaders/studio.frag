precision highp float;
uniform sampler2D uInput;
uniform float uEngine;
uniform float uRecipe;
uniform float uEnvelope;
uniform float uTime;
uniform float uIntensity;
uniform float uDirection;
uniform float uZoom;
uniform float uRotation;
uniform float uTranslateX;
uniform float uTranslateY;
varying vec2 vUv;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
vec3 sampleAt(vec2 p){return texture2D(uInput,clamp(p,vec2(.001),vec2(.999))).rgb;}
void main(){
 vec2 q=vUv-.5-vec2(uTranslateX,uTranslateY);
 float baseAngle=-uRotation*0.01745329252;
 q=mat2(cos(baseAngle),-sin(baseAngle),sin(baseAngle),cos(baseAngle))*q;
 vec2 p=q/uZoom+.5;
 float t=uTime; float r=uRecipe; float e=uEnvelope;
 float phase=fract(t*.6); float wave=.5+.5*sin(t*6.283185);
 float env=.35+.65*wave;
 if(e>0.5 && e<1.5)env=.15+.85*phase;
 else if(e<2.5 && e>1.5)env=1.-.85*phase;
 else if(e<3.5 && e>2.5)env=.2+.8*abs(sin(t*4.));
 else if(e<4.5 && e>3.5)env=.3+.7*pow(abs(sin(t*9.)),3.);
 else if(e<5.5 && e>4.5)env=.5+.5*sin(t*.8);
 else if(e<6.5 && e>5.5)env=.2+.8*step(.72,phase);
 else if(e<7.5 && e>6.5)env=.3+.7*(.5+.5*cos(t*2.));
 else if(e<8.5 && e>7.5)env=.15+.85*exp(-phase*7.);
 else if(e>8.5)env=1.;
 float a=clamp(uIntensity,0.,2.)*env; float k=1.+r*.6;
 vec2 dir=vec2(cos(uDirection),sin(uDirection));
 vec3 original=sampleAt(p); vec3 c=original; float n=uEngine;
 if(n<.5){float band=floor(p.y*(8.+r*13.));p.x+=step(.6,hash(vec2(band,floor(t*12.))))*(hash(vec2(band,t))-.5)*.18*a*k;c=sampleAt(p);}
 else if(n<1.5){vec2 d=dir*.008*a*k;c=vec3(sampleAt(p+d).r,original.g,sampleAt(p-d).b);}
 else if(n<2.5){p+=vec2(sin(t*37.),cos(t*29.))*.009*a*k;c=sampleAt(p);}
 else if(n<3.5){p=(p-.5)/(1.+.12*a*k)+.5;c=sampleAt(p);}
 else if(n<4.5){float angle=.08*a*k*sin(t*2.);vec2 q=p-.5;p=mat2(cos(angle),-sin(angle),sin(angle),cos(angle))*q+.5;c=sampleAt(p);}
 else if(n<5.5){p+=dir*sin((p.x+p.y)*12.*k+t*3.)*.015*a;c=sampleAt(p);}
 else if(n<6.5){vec2 q=p-.5;float len=length(q);p+=q/max(len,.001)*sin(len*40.*k-t*6.)*.015*a;c=sampleAt(p);}
 else if(n<7.5){vec2 q=p-.5;float angle=a*k*(.7-length(q))*sin(t+1.);p=mat2(cos(angle),-sin(angle),sin(angle),cos(angle))*q+.5;c=sampleAt(p);}
 else if(n<8.5){vec2 q=p-.5;float angle=atan(q.y,q.x);float sector=6.283185/(3.+r);angle=abs(mod(angle,sector)-sector*.5);vec2 mirrored=vec2(cos(angle),sin(angle))*length(q)+.5;c=mix(original,sampleAt(mirrored),min(a,1.));}
 else if(n<9.5){float grid=max(8.,90./k);c=mix(original,sampleAt((floor(p*grid)+.5)/grid),min(a,1.));}
 else if(n<10.5){float gray=dot(original,vec3(.2126,.7152,.0722));vec2 cell=fract(p*(50.+r*20.))-.5;float ink=step(length(cell),sqrt(gray)*.62);c=mix(original,vec3(ink),min(a,1.));}
 else if(n<11.5){float levels=3.+r*2.;c=mix(original,floor(original*levels)/levels,min(a,1.));}
 else if(n<12.5){c=original*(1.-.35*a*(.5+.5*sin(p.y*(160.+r*80.)+t*4.)));}
 else if(n<13.5){float stripe=exp(-pow((p.y-fract(t*.2))*30.,2.));p.x+=stripe*.08*a*k;c=sampleAt(p);c+=vec3((hash(p+t)-.5)*.16*a);}
 else if(n<14.5){float grain=(hash(floor(p*(200.+r*70.))+floor(t*24.))-.5)*.22*a;c=original+grain;}
 else if(n<15.5){float gray=dot(original,vec3(.2126,.7152,.0722));vec3 lo=vec3(.04+r*.025,.025,.12);vec3 hi=vec3(1.-r*.13,.22+r*.15,.55+r*.10);c=mix(original,mix(lo,hi,gray),min(a,1.));}
 else if(n<16.5){float glow=exp(-length(p-vec2(.2+.15*r,.5+.3*sin(t)))*3.);c=original+vec3(1.,.12+r*.14,.18+r*.10)*glow*a*.6;}
 else if(n<17.5){vec2 q=p-.5;vec2 d=q*.05*a*k;c=vec3(sampleAt(p+d).r,sampleAt(p).g,sampleAt(p-d).b);}
 else if(n<18.5){vec2 d=dir*.004*a*k;c=original*.2;for(int i=1;i<=4;i++){float f=float(i);c+=sampleAt(p+d*f)*.1+sampleAt(p-d*f)*.1;}}
 else {float v=smoothstep(.2,.8,length(p-.5)*k);c=original*(1.-v*.7*a);p=(p-.5)/(1.+.06*a*k)+.5;c=mix(c,sampleAt(p),.2);}
 gl_FragColor=vec4(clamp(mix(original,c,step(.00001,uIntensity)),0.,1.),1.);
}
