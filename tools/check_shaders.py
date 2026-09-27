"""Render the actual APK shaders with Mesa EGL; requires libEGL and libGL."""
import ctypes as C
import os
from pathlib import Path
os.environ.setdefault('EGL_PLATFORM', 'surfaceless')
e = C.CDLL('libEGL.so.1')
g = C.CDLL('libGL.so.1')
I, U, F, P = C.c_int, C.c_uint, C.c_float, C.c_void_p

def bind(lib, name, result, *args):
    fn = getattr(lib, name); fn.restype = result; fn.argtypes = list(args); return fn
getdisplay=bind(e,'eglGetDisplay',P,P); initialize=bind(e,'eglInitialize',U,P,P,P)
choose=bind(e,'eglChooseConfig',U,P,P,P,I,P); api=bind(e,'eglBindAPI',U,U)
pbuffer=bind(e,'eglCreatePbufferSurface',P,P,P,P); create=bind(e,'eglCreateContext',P,P,P,P,P)
current=bind(e,'eglMakeCurrent',U,P,P,P,P)
display=getdisplay(None); assert initialize(display,None,None)
assert api(0x30A0)
attrs=(I*15)(0x3033,1,0x3040,4,0x3024,8,0x3023,8,0x3022,8,0x3021,8,0x3025,0,0x3038)
config=P(); count=I(); assert choose(display,attrs,C.byref(config),1,C.byref(count)) and count.value
size=96
surf=pbuffer(display,config,(I*5)(0x3057,size,0x3056,size,0x3038))
ctx=create(display,config,None,(I*3)(0x3098,2,0x3038)); assert ctx and current(display,surf,surf,ctx)
shader=bind(g,'glCreateShader',U,U); source=bind(g,'glShaderSource',None,U,I,P,P)
compile_=bind(g,'glCompileShader',None,U); shaderiv=bind(g,'glGetShaderiv',None,U,U,P)
shaderlog=bind(g,'glGetShaderInfoLog',None,U,I,P,P)
program=bind(g,'glCreateProgram',U); attach=bind(g,'glAttachShader',None,U,U)
link=bind(g,'glLinkProgram',None,U); programiv=bind(g,'glGetProgramiv',None,U,U,P)
use=bind(g,'glUseProgram',None,U); location=bind(g,'glGetUniformLocation',I,U,C.c_char_p)
uniform=bind(g,'glUniform1f',None,I,F); uniformi=bind(g,'glUniform1i',None,I,I)
gentex=bind(g,'glGenTextures',None,I,P); active=bind(g,'glActiveTexture',None,U)
bindtex=bind(g,'glBindTexture',None,U,U); texparam=bind(g,'glTexParameteri',None,U,U,I)
teximage=bind(g,'glTexImage2D',None,U,I,I,I,I,I,U,U,P)
attrib=bind(g,'glGetAttribLocation',I,U,C.c_char_p); enable=bind(g,'glEnableVertexAttribArray',None,U)
vertex=bind(g,'glVertexAttribPointer',None,U,I,U,U,I,P); draw=bind(g,'glDrawArrays',None,U,I,I)
viewport=bind(g,'glViewport',None,I,I,I,I); read=bind(g,'glReadPixels',None,I,I,I,I,U,U,P)
error=bind(g,'glGetError',U)
root=Path(__file__).resolve().parents[1]/'app/src/main/assets/shaders'
p=program()
for file,kind in [('chaos.vert',0x8B31),('chaos.frag',0x8B30)]:
    s=shader(kind); src=C.c_char_p((root/file).read_bytes()); source(s,1,C.byref(src),None); compile_(s)
    ok=I(); shaderiv(s,0x8B81,C.byref(ok))
    if not ok.value:
        msg=C.create_string_buffer(8192); shaderlog(s,8192,None,msg); raise AssertionError(msg.value)
    attach(p,s)
link(p); ok=I(); programiv(p,0x8B82,C.byref(ok)); assert ok.value, 'Shader linking failed'
use(p)
# A colorful grid shows channel shifts, zoom, vignette and bands independently.
pixels=bytes(v for y in range(size) for x in range(size) for v in ((x*13+y*7)%256,(x*5+y*19)%256,(x*23+y*3)%256,255))
for unit,data in [(0,pixels),(1,bytes(255-v if i%4!=3 else 255 for i,v in enumerate(pixels)))]:
    t=U(); gentex(1,C.byref(t)); active(0x84C0+unit); bindtex(0x0DE1,t)
    for setting,value in [(0x2801,0x2601),(0x2800,0x2601),(0x2802,0x812F),(0x2803,0x812F)]: texparam(0x0DE1,setting,value)
    teximage(0x0DE1,0,0x1908,size,size,0,0x1908,0x1401,C.c_char_p(data))
    uniformi(location(p,b'uInput' if unit==0 else b'uHistory'),unit)
verts=(F*16)(-1,-1,0,1,1,-1,0,1,-1,1,0,1,1,1,0,1)
a=attrib(p,b'aPosition'); enable(a); vertex(a,4,0x1406,0,0,verts); viewport(0,0,size,size)
names=['uTime','uZoom','uAutoZoom','uGlitch','uRgb','uShake','uStrobe','uHue','uVignette','uBlurWeight']

def render(**values):
    for name in names: uniform(location(p,name.encode()),values.get(name,1 if name=='uZoom' else 0))
    draw(5,0,4); out=(C.c_ubyte*(size*size*4))(); read(0,0,size,size,0x1908,0x1401,out)
    assert error()==0
    return bytes(out)
raw=render(); assert max(abs(a-b) for a,b in zip(raw,pixels))<=1, 'Disabled effects must preserve image'
for name in names[1:]:
    amount=1.7 if name=='uZoom' else (0.3 if name=='uBlurWeight' else 1)
    difference=max(sum(abs(a-b) for a,b in zip(raw,render(**{name:amount,'uTime':t/10})))/len(raw) for t in range(1,21))
    assert difference>1, (name,'has no visible effect',difference)
    print(name, 'PASS', round(difference,2))
all_fx=render(**{**dict.fromkeys(names[2:-1],1),'uTime':1.9,'uZoom':1.7,'uBlurWeight':0.3})
assert len(set(all_fx))>20
print('GLSL compiled, identity preserved, all eight FX + zoom change pixels; combined render passes.')

# Exercise every Studio preset using actual rendered pixels, not just unique IDs.
import hashlib
p=program()
for file,kind in [('chaos.vert',0x8B31),('studio.frag',0x8B30)]:
    s=shader(kind); src=C.c_char_p((root/file).read_bytes()); source(s,1,C.byref(src),None); compile_(s)
    ok=I(); shaderiv(s,0x8B81,C.byref(ok))
    if not ok.value:
        msg=C.create_string_buffer(8192); shaderlog(s,8192,None,msg); raise AssertionError(msg.value)
    attach(p,s)
link(p); ok=I(); programiv(p,0x8B82,C.byref(ok)); assert ok.value
use(p); uniformi(location(p,b'uInput'),0)
a=attrib(p,b'aPosition');enable(a);vertex(a,4,0x1406,0,0,verts)
names=['uTime','uZoom','uEngine','uRecipe','uEnvelope','uIntensity','uDirection']
raw=render();assert max(abs(a-b) for a,b in zip(raw,pixels))<=1
signatures={}
for engine in range(20):
    for recipe in range(5):
        for envelope in range(10):
            frames=[render(uEngine=engine,uRecipe=recipe,uEnvelope=envelope,uIntensity=.8,uTime=t,uDirection=.7) for t in [.173,.419,.937,1.231]]
            assert any(sum(abs(a-b) for a,b in zip(raw,f))/len(raw)>.4 for f in frames),(engine,recipe,envelope,'invisible')
            sig=hashlib.sha256(b''.join(frames)).hexdigest()
            assert sig not in signatures,('Duplicate rendered presets',signatures.get(sig),(engine,recipe,envelope))
            signatures[sig]=(engine,recipe,envelope)
print('Studio: 1000 rendered presets have distinct multi-frame pixel signatures.')
