"""Android 9 native upgrade/record/import checks; never claims BUS12 hardware output."""
from pathlib import Path
import subprocess,time,re,xml.etree.ElementTree as ET,io,wave
ROOT=Path(__file__).resolve().parents[2]; QA=Path(__file__).parent
ADB=ROOT/'.build-tools/platform-tools/adb.exe'; PKG='com.zecadev.outervoice'
def adb(*args): return subprocess.check_output([str(ADB),'-s','emulator-5554',*args],text=True,encoding='utf-8',errors='replace')
def dump(name):
    for i in range(6):
        result=adb('shell','uiautomator','dump','/sdcard/qa.xml')
        if 'dumped to' in result: break
        time.sleep(1)
    assert 'dumped to' in result,result
    adb('pull','/sdcard/qa.xml',str(QA/(name+'.xml')))
    return ET.parse(QA/(name+'.xml')).getroot()
def texts(tree): return [n.get('text') for n in tree.iter('node') if n.get('text')]
def point(tree,label,attr='text'):
    node=next(n for n in tree.iter('node') if (n.get(attr) or '').lower()==label.lower())
    a,b,c,d=map(int,re.findall(r'\d+',node.get('bounds'))); return ((a+c)//2,(b+d)//2)
def tap(x,y): adb('shell','input','tap',str(x),str(y)); time.sleep(.5)
def click(tree,label,attr='text'): tap(*point(tree,label,attr))
def shot(name):
    adb('shell','screencap','-p','/sdcard/qa.png'); adb('pull','/sdcard/qa.png',str(QA/(name+'.png')))
def private(relative): return '/data/data/'+PKG+'/'+relative
def filelist(folder): return adb('shell','ls',private(folder)).split()
def verify_wav(folder,name):
    raw=subprocess.check_output([str(ADB),'-s','emulator-5554','exec-out','cat',private(folder+'/'+name)])
    with wave.open(io.BytesIO(raw),'rb') as f:
        assert (f.getframerate(),f.getnchannels(),f.getsampwidth())==(44100,1,2)
        assert f.getnframes()>0
        print('PASS recorded WAV metadata/frames:',f.getnframes(),flush=True)
    (QA/('recording-'+folder+'.wav')).write_bytes(raw)
adb('root'); adb('wait-for-device')
adb('install','-r',str(ROOT/'OuterVoice/dist/OuterVoice-1.1.0.apk'))
adb('shell','logcat','-c')
adb('shell','pm','grant',PKG,'android.permission.RECORD_AUDIO')
adb('shell','am','force-stop',PKG); adb('shell','am','start','-W','-n',PKG+'/.MainActivity')
tree=dump('v110-home'); assert 'Welcome' in texts(tree); shot('v110-home')
print('PASS upgrade retains saved sounds',flush=True)
click(tree,'Record & Play'); tree=dump('v110-record-idle'); shot('v110-record-idle')
mic=point(tree,'Start Recording','content-desc'); tap(*mic); time.sleep(2); shot('v110-recording'); tap(*mic); time.sleep(1)
tree=dump('v110-record-ready'); shot('v110-record-ready')
node=next(n for n in tree.iter('node') if n.get('text')=='Play'); assert node.get('enabled')=='true',texts(tree)
files=[x for x in filelist('cache') if x.startswith('voice-')]; assert len(files)==1,files
verify_wav('cache',files[0]); click(tree,'Play'); time.sleep(1)
tree=dump('v110-record-no-bus'); assert any('BUS12' in t for t in texts(tree)); print('PASS temporary Play rejects missing BUS12',flush=True)
click(tree,'Live Speaking'); assert not any(x.startswith('voice-') for x in filelist('cache'))
tree=dump('v110-live'); print('PASS temporary recording removed on mode change',flush=True)
click(tree,'+ Add Sound'); tree=dump('v110-add'); shot('v110-add')
node=next(n for n in tree.iter('node') if n.get('class')=='android.widget.EditText'); tap(*point(tree,node.get('text')))
adb('shell','input','text','Recorded%stest'); adb('shell','input','keyevent','4'); time.sleep(.5)
tree=dump('v110-add-named'); position=point(tree,'Record microphone'); tap(*position); time.sleep(2); shot('v110-add-recording'); tap(*position); time.sleep(1)
tree=dump('v110-add-ready'); assert 'Microphone recording.wav' in texts(tree),texts(tree); shot('v110-add-ready')
click(tree,'Save'); tree=dump('v110-record-saved'); assert any(x.startswith('voice-') for x in filelist('cache'))==False
files=filelist('files'); recorded=max([x for x in files if x.endswith('.wav')],key=lambda x:int(adb('shell','stat','-c','%Y',private('files/'+x)).strip()))
verify_wav('files',recorded); print('PASS Add Sound record/name/save',flush=True)
# Recorded test is the sixth item; scroll down to observe its remove button.
adb('shell','input','swipe','800','500','800','200','400'); time.sleep(.5)
tree=dump('v110-scroll'); assert 'Recorded test' in texts(tree)
click(tree,'Remove Recorded test','content-desc'); tree=dump('v110-remove-confirm'); click(tree,'Cancel')
tree=dump('v110-remove-cancel'); assert 'Recorded test' in texts(tree)
click(tree,'Remove Recorded test','content-desc'); tree=dump('v110-remove-confirm'); click(tree,'Remove')
assert recorded not in filelist('files'); print('PASS Remove Cancel and confirmed deletion of WAV/index',flush=True)
tree=dump('v110-after-remove'); click(tree,'+ Add Sound')
# Simulate a head unit without DocumentsUI and exercise automatic built-in fallback.
adb('shell','pm','disable-user','--user','0','com.android.documentsui')
adb('shell','pm','disable-user','--user','0','com.android.music')
try:
    tree=dump('v110-add-fallback'); click(tree,'Import WAV'); tree=dump('v110-storage-permission')
    if any(t.lower()=='allow' for t in texts(tree)):
        click(tree,next(t for t in texts(tree) if t.lower()=='allow')); tree=dump('v110-storage')
    assert 'Choose storage' in texts(tree),texts(tree); shot('v110-storage')
    click(tree,next(t for t in texts(tree) if t.startswith('Internal storage:')))
    tree=dump('v110-folders'); click(tree,'Folder: Download'); tree=dump('v110-downloads'); shot('v110-downloads')
    click(tree,'WAV: invalid.wav'); tree=dump('v110-invalid'); assert 'Not a WAV file' in texts(tree),texts(tree)
    click(tree,'Browse files on this device'); tree=dump('v110-storage'); click(tree,next(t for t in texts(tree) if t.startswith('Internal storage:')))
    tree=dump('v110-folders'); click(tree,'Folder: Download'); tree=dump('v110-downloads'); click(tree,'WAV: welcome.wav')
    tree=dump('v110-imported'); assert 'welcome.wav' in texts(tree); shot('v110-imported'); click(tree,'Cancel')
    print('PASS picker absent -> permission -> built-in browser; invalid rejection and valid WAV import',flush=True)
finally:
    adb('shell','pm','enable','com.android.documentsui')
    adb('shell','pm','enable','com.android.music')
tree=dump('v110-final-home'); click(tree,'Audio settings and diagnostics','content-desc'); tree=dump('v110-audio'); shot('v110-audio')
assert 'Live microphone level: 100%' in texts(tree)
click(tree,'Test speaker'); click(tree,'Diagnostics'); tree=dump('v110-diagnostics'); shot('v110-diagnostics')
assert any('BUS12_OUTER_NOTIFY' in t for t in texts(tree)); click(tree,'Copy report')
tree=dump('v110-audio'); click(tree,'Close'); tree=dump('v110-final-home')
click(tree,'App information and credits','content-desc'); tree=dump('v110-credits'); assert '1.1.0 (2)' in texts(tree); shot('v110-credits')
log=adb('shell','logcat','-d','-s','AndroidRuntime:E'); (QA/'v110-runtime.log').write_text(log,encoding='utf-8'); assert 'FATAL EXCEPTION' not in log,log
print('PASS audio settings, diagnostics, test tone missing-route rejection, credits and no runtime crash',flush=True)
