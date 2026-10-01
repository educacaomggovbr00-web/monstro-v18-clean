#!/usr/bin/env python3
"""Reproducible, source-pinned native integration. Run once, never on app launch."""
from pathlib import Path
import argparse, shutil, subprocess, json
PIN='8a18de40dd474bbc01f43fd5a6d8e1356ce5600f'
def run(root, upstream):
    assert subprocess.check_output(['git','-C',str(upstream),'rev-parse','HEAD'],text=True).strip()==PIN
    if (root/'CLEARCUT_FULL_SOURCE.json').exists():
        print('Full source already present'); return
    old=root/'legacy'/'monstro-v18'
    old.mkdir(parents=True,exist_ok=True)
    for name in ['app','build.gradle','settings.gradle','gradle.properties','README.md','CLEARCUT_INTEGRATION.md','CAPCUT_PARITY.md','tools']:
        p=root/name
        if p.exists():
            if p.is_dir(): shutil.copytree(p,old/name,dirs_exist_ok=True,ignore=shutil.ignore_patterns('build','.gradle'))
            else: shutil.copy2(p,old/name)
    config=(root/'app/google-services.json').read_bytes() if (root/'app/google-services.json').exists() else None
    shutil.rmtree(root/'app')
    for name in ['build.gradle','settings.gradle']: (root/name).unlink(missing_ok=True)
    paths=subprocess.check_output(['git','-C',str(upstream),'ls-files','-z']).decode().split('\0')
    for name in filter(None,paths):
        source=upstream/name; dest=root/name
        if source.is_file():
            dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
    # Runtime and test packages are part of Monstro, with one Activity/editor.
    for p in root.rglob('*'):
        if not p.is_file() or '.git' in p.parts or 'legacy' in p.parts or '.integration' in p.parts: continue
        try: text=p.read_text(encoding='utf-8')
        except (UnicodeError,OSError): continue
        text=text.replace('com.novacut.editor','com.monstro.v18')
        p.write_text(text,encoding='utf-8')
    # Directory moves keep Room generated schemas and test source paths coherent.
    for p in sorted(root.rglob('com/novacut/editor'),key=lambda x:len(x.parts),reverse=True):
        d=p.parent.parent/'monstro/v18';d.parent.mkdir(parents=True,exist_ok=True);shutil.move(str(p),str(d))
    for p in sorted(root.rglob('com.novacut.editor.engine.db.ProjectDatabase')):
        p.rename(p.with_name('com.monstro.v18.engine.db.ProjectDatabase'))
    build=root/'app/build.gradle.kts';s=build.read_text()
    s=s.replace('applicationId = "com.monstro.v18"','applicationId = "com.monstro.v18.studio"').replace('versionCode = 299','versionCode = 25').replace('versionName = "3.81.0"','versionName = "18.7-ClearCut"')
    s=s.replace('buildConfigField("boolean", "UPDATE_CHECK_AVAILABLE", "true")','buildConfigField("boolean", "UPDATE_CHECK_AVAILABLE", "false")')
    s=s.replace('resolveSigningSecret("CLEARCUT_STORE_FILE")','resolveSigningSecret("MONSTRO_RELEASE_KEYSTORE", "CLEARCUT_STORE_FILE")').replace('resolveSigningSecret("CLEARCUT_STORE_PASSWORD", "CLEARCUT_KS_PASS")','resolveSigningSecret("MONSTRO_RELEASE_STORE_PASSWORD", "CLEARCUT_STORE_PASSWORD", "CLEARCUT_KS_PASS")').replace('resolveSigningSecret("CLEARCUT_KEY_ALIAS")','resolveSigningSecret("MONSTRO_RELEASE_KEY_ALIAS", "CLEARCUT_KEY_ALIAS")').replace('resolveSigningSecret("CLEARCUT_KEY_PASSWORD", "CLEARCUT_KEY_PASS")','resolveSigningSecret("MONSTRO_RELEASE_KEY_PASSWORD", "CLEARCUT_KEY_PASSWORD", "CLEARCUT_KEY_PASS")')
    # The fork must preserve MONSTRO's signing identity, not ClearCut's certificate.
    s=s.replace('dependsOn(verifyReleaseSigningIdentity)','// Monstro signing is configured with MONSTRO_RELEASE_* credentials.')
    s=s.replace('    alias(libs.plugins.android.application)','    alias(libs.plugins.android.application)\n    id("com.google.gms.google-services") version "4.5.0" apply false',1)
    s+='\nif (file("google-services.json").exists()) { apply(plugin = "com.google.gms.google-services") }\n'
    s=s.replace('dependencies {\n    constraints {','dependencies {\n    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))\n    implementation("com.google.firebase:firebase-ai")\n    implementation("com.google.firebase:firebase-auth:22.3.1")\n    debugImplementation("com.google.firebase:firebase-appcheck-debug")\n    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")\n    implementation("com.alphacephei:vosk-android:0.3.45") { exclude(group = "net.java.dev.jna", module = "jna") }\n    implementation("net.java.dev.jna:jna:5.13.0@aar")\n    constraints {',1)
    build.write_text(s)
    if config: (root/'app/google-services.json').write_bytes(config)
    settings=root/'settings.gradle.kts';settings.write_text(settings.read_text().replace('rootProject.name = "ClearCut"','rootProject.name = "MonstroV18"'))
    for p in (root/'app/src/main/res').glob('values*/strings.xml'):
        s=p.read_text().replace('<string name="app_name">ClearCut</string>','<string name="app_name">Monstro V18</string>');p.write_text(s)
    # Explicit shortcut targets must use the installed application id.
    for p in (root/'app/src/main/res').glob('xml*/shortcuts.xml'):
        p.write_text(p.read_text().replace('android:targetPackage="com.monstro.v18"','android:targetPackage="com.monstro.v18.studio"'))
    identity=root/'scripts/package_identity.json';data=json.loads(identity.read_text());data['applicationId']='com.monstro.v18.studio';data['shortcutTarget']['package']='com.monstro.v18.studio';identity.write_text(json.dumps(data,indent=2)+'\n')
    (root/'gradlew').chmod(0o755)
    # Preserve the exact MIT notice and source provenance.
    notices=root/'third_party/clearcut';notices.mkdir(parents=True,exist_ok=True)
    shutil.copy2(upstream/'LICENSE',notices/'LICENSE')
    manifest={'repository':'https://github.com/SysAdminDoc/ClearCut','commit':PIN,'upstreamVersion':'3.81.0','applicationId':'com.monstro.v18.studio','sourcePackage':'com.monstro.v18','legacySource':'legacy/monstro-v18','mode':'single-integrated-editor','sourceFiles':len([n for n in paths if n])}
    (root/'CLEARCUT_FULL_SOURCE.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('Integrated',manifest['sourceFiles'],'upstream files')
if __name__=='__main__':
    a=argparse.ArgumentParser();a.add_argument('--root',type=Path,default=Path('.'));a.add_argument('--upstream',type=Path,required=True);o=a.parse_args();run(o.root.resolve(),o.upstream.resolve())
