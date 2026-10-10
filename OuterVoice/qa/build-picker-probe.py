"""Build a disposable picker-compatibility APK, outside production dist."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2];tools=root/'.build-tools';source=Path(__file__).with_name('picker-probe');out=tools/'picker-probe'
jdk=next((tools/'java').iterdir());sdk=next((tools/'android-build').iterdir());android=next((tools/'android-platform').glob('*/android.jar'))
for name in ['classes','dex']: (out/name).mkdir(parents=True,exist_ok=True)
def run(*args):subprocess.run(list(map(str,args)),check=True)
run(sdk/'aapt2.exe','link','-I',android,'--manifest',source/'AndroidManifest.xml','-o',out/'probe.apk')
run(jdk/'bin/javac.exe','--release','8','-classpath',android,'-d',out/'classes',source/'ProbeActivity.java')
run(jdk/'bin/java.exe','-cp',sdk/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--min-api','28','--output',out/'dex',*sorted((out/'classes').rglob('*.class')))
run(jdk/'bin/jar.exe','uf',out/'probe.apk','-C',out/'dex','classes.dex')
run(sdk/'zipalign.exe','-f','4',out/'probe.apk',out/'aligned.apk')
run(jdk/'bin/java.exe','-jar',sdk/'lib/apksigner.jar','sign','--ks',tools/'outervoice-signing.jks','--ks-key-alias','outervoice','--ks-pass','file:'+str(tools/'outervoice-signing-password.txt'),'--out',out/'signed.apk',out/'aligned.apk')
print('Built disposable picker probe:',out/'signed.apk')
