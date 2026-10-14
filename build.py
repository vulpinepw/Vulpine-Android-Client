import glob
import os
import shutil
import subprocess
import sys
import zipfile

R = os.path.dirname(os.path.abspath(__file__))
SDK = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or os.path.expanduser('~/Android/Sdk')
PLATFORM = os.environ.get('ANDROID_PLATFORM', 'android-36')
B = os.environ.get('BUILD') or os.path.join(R, 'build')
ABIS = (os.environ.get('ABIS') or 'arm64-v8a,armeabi-v7a').split(',')
VC = os.environ.get('VERSION_CODE', '42')
VN = os.environ.get('VERSION_NAME', '3.1.0')
OUT = os.environ.get('OUT', os.path.join(B, 'vulpine.apk'))


def fail(msg):
    sys.stderr.write(msg + '\n')
    sys.exit(1)


def run(*a, **k):
    r = subprocess.run(a, capture_output=True, text=True, **k)
    if r.returncode:
        sys.stderr.write(r.stdout[-6000:] + r.stderr[-6000:])
        sys.exit(1)
    return r.stdout


def build_tools():
    base = os.path.join(SDK, 'build-tools')
    if not os.path.isdir(base):
        fail('Android SDK build-tools not found in ' + SDK + ' (set ANDROID_HOME)')
    want = os.environ.get('BUILD_TOOLS')
    if want:
        return os.path.join(base, want)
    vs = sorted(os.listdir(base), key=lambda v: [int(x) if x.isdigit() else 0 for x in v.replace('-', '.').split('.')])
    return os.path.join(base, vs[-1])


def signing():
    ks = os.environ.get('VULPINE_KEYSTORE')
    if ks:
        alias = os.environ.get('VULPINE_KEY_ALIAS', 'vulpine')
        pw = os.environ.get('VULPINE_KEY_PASS')
        if not pw:
            fail('VULPINE_KEY_PASS is required when VULPINE_KEYSTORE is set')
        return ks, alias, pw
    ks = os.path.join(R, 'debug.keystore')
    if not os.path.exists(ks):
        run('keytool', '-genkeypair', '-keystore', ks, '-storepass', 'android', '-keypass', 'android', '-alias', 'debug', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Debug')
    return ks, 'debug', 'android'


BT = build_tools()
AJ = os.path.join(SDK, 'platforms', PLATFORM, 'android.jar')
if not os.path.exists(AJ):
    fail('android.jar not found: ' + AJ + ' (install platform ' + PLATFORM + ')')

shutil.rmtree(B, ignore_errors=True)
for d in ('res', 'gen', 'cls', 'dex'):
    os.makedirs(os.path.join(B, d), exist_ok=True)

run(os.path.join(BT, 'aapt2'), 'compile', '--dir', os.path.join(R, 'app', 'res'), '-o', os.path.join(B, 'res', 'res.zip'))
run(os.path.join(BT, 'aapt2'), 'link', '-I', AJ, '--manifest', os.path.join(R, 'app', 'AndroidManifest.xml'), '--java', os.path.join(B, 'gen'), '-A', os.path.join(R, 'app', 'assets'), '--version-code', VC, '--version-name', VN, '--min-sdk-version', '26', '--target-sdk-version', '36', '-o', os.path.join(B, 'base.apk'), os.path.join(B, 'res', 'res.zip'))

jars = sorted(glob.glob(os.path.join(R, 'libs', 'android', '*.jar')))
srcs = [os.path.join(d, f) for top in (os.path.join(R, 'core', 'src'), os.path.join(R, 'app', 'src'), os.path.join(B, 'gen')) for d, _, fs in os.walk(top) for f in fs if f.endswith('.java')]
with open(os.path.join(B, 'srcs.txt'), 'w') as f:
    f.write('\n'.join(srcs))
run('javac', '-nowarn', '-encoding', 'UTF-8', '--release', '11', '-classpath', os.pathsep.join([AJ] + jars), '-d', os.path.join(B, 'cls'), '@' + os.path.join(B, 'srcs.txt'))

cls = [os.path.join(d, f) for d, _, fs in os.walk(os.path.join(B, 'cls')) for f in fs if f.endswith('.class')]
with open(os.path.join(B, 'cls.txt'), 'w') as f:
    f.write('\n'.join(cls))
run(os.path.join(BT, 'd8'), '--release', '--min-api', '26', '--lib', AJ, '--output', os.path.join(B, 'dex'), *jars, '@' + os.path.join(B, 'cls.txt'))

unsigned = os.path.join(B, 'unsigned.apk')
shutil.copy(os.path.join(B, 'base.apk'), unsigned)
with zipfile.ZipFile(unsigned, 'a') as z:
    for f in sorted(os.listdir(os.path.join(B, 'dex'))):
        z.write(os.path.join(B, 'dex', f), f)
    for abi in ABIS:
        for so in glob.glob(os.path.join(R, 'libs', 'jni', abi, '*.so')):
            z.write(so, 'lib/' + abi + '/' + os.path.basename(so), compress_type=zipfile.ZIP_STORED)

aligned = os.path.join(B, 'aligned.apk')
run(os.path.join(BT, 'zipalign'), '-f', '-P', '16', '4', unsigned, aligned)
ks, alias, pw = signing()
run(os.path.join(BT, 'apksigner'), 'sign', '--ks', ks, '--ks-key-alias', alias, '--ks-pass', 'env:VKP', '--key-pass', 'env:VKP', '--out', OUT, aligned, env=dict(os.environ, VKP=pw))
print('built', OUT, os.path.getsize(OUT))
