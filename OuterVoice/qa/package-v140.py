"""Package v1.4.0 source and SHA256 checksums without signing credentials/tools."""
from pathlib import Path
import zipfile,hashlib
root=Path(__file__).resolve().parents[1]
apk=root/'dist/OuterVoice-1.4.0.apk';archive=root/'dist/OuterVoice-1.4.0-source.zip'
assert apk.is_file() and 'android:versionName="1.4.0"' in (root/'app/src/main/AndroidManifest.xml').read_text()
files=[]
for directory in ['app','tests','web-simulator','design-mockups/v1.3']:
    files.extend(p for p in (root/directory).rglob('*') if p.is_file())
files.extend(root.glob('*.md'))
files.extend(p for p in (root/'qa').glob('*.py'))
files.extend(p for p in (root/'qa').glob('v140-*') if p.suffix in ['.png','.xml'])
files.extend(root/name for name in ['build.ps1','LICENSE','app-icon-source.png','qa/README.md'] if (root/name).is_file())
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(set(files)):z.write(p,p.relative_to(root))
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert not any('.build-tools' in n or n.endswith(('.jks','.keystore','.wav')) or 'signing-password' in n for n in z.namelist())
    assert 'web-simulator/app.js' in z.namelist() and 'app/src/main/java/com/zecadev/outervoice/HoldFeedback.java' in z.namelist()
checks=''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in [apk,archive])
(root/'dist/SHA256.txt').write_text(checks,encoding='utf-8')
print(checks,end='');print('Source archive verified:',archive.stat().st_size,'bytes')
