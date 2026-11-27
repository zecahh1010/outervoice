"""Coordinate an isolated emulator instrumentation probe with the virtual peripheral."""
from pathlib import Path
import subprocess, time, urllib.request, sys

root=Path(__file__).resolve().parents[3]
adb=root/'.build-tools/platform-tools/adb.exe'
def run(*args):
    return subprocess.run([str(adb),'-s','emulator-5556',*args],capture_output=True,text=True,timeout=15)
def control(path):
    with urllib.request.urlopen('http://127.0.0.1:8878'+path,timeout=8) as response:return response.read().decode()

baseline='--baseline' in sys.argv
out=root/'OuterVoice/build'/('baseline-radio-console.txt' if baseline else 'dual-radio-console.txt')
remote='/sdcard/Android/data/com.zecadev.outervoice/files/v163-radio/'
run('shell','rm','-f',remote+'phase.txt',remote+'ack.txt')
control('/stop')
with out.open('w',encoding='utf-8') as stream:
    args=[str(adb),'-s','emulator-5556','shell','am','instrument','-w']
    if baseline:args+=['-e','baseline','true']
    args+=['com.zecadev.outervoice.probe/.RadioProbe']
    proc=subprocess.Popen(args,stdout=stream,stderr=subprocess.STDOUT)
    seen=set();deadline=time.monotonic()+180
    try:
        while proc.poll() is None and time.monotonic()<deadline:
            phase=run('shell','cat',remote+'phase.txt').stdout.strip()
            if phase and phase not in seen:
                seen.add(phase)
                if phase=='DONE':break
                allowed={m+'-'+e for m in ['baseline','shortcut','callback'] for e in ['advert','press1','press2','double','stop']}|{'callback-config'}
                if phase not in allowed:raise RuntimeError('Unknown test phase')
                if phase=='callback-config':
                    run('shell','pm','grant','com.zecadev.outervoice','android.permission.ACCESS_FINE_LOCATION');run('shell','cmd','location','set-location-enabled','true');reply=control('/state')
                elif phase.endswith('-advert'):reply=control('/start?gesture=1')
                elif phase.endswith('-press1'):reply=control('/notify?value=1')
                elif phase.endswith('-press2'):time.sleep(1.7);reply=control('/notify?value=1')
                elif phase.endswith('-double'):reply=control('/notify?value=2')
                elif phase.endswith('-stop'):reply=control('/stop')
                else:raise RuntimeError('Unexpected phase '+phase)
                print(phase+': '+reply,flush=True)
                run('shell','sh','-c',"'echo "+phase+" > "+remote+"ack.txt'")
            time.sleep(.2)
        proc.wait(timeout=15)
    finally:
        control('/stop')
        if proc.poll() is None:proc.terminate()
text=out.read_text(encoding='utf-8')
print(text)
assert 'INSTRUMENTATION_RESULT: result=PASS' in text,'Radio probe failed; see '+str(out)
