"""Local Android 9 checks. BUS12 audible output requires vehicle testing."""
from pathlib import Path
import subprocess,time,xml.etree.ElementTree as ET,re,io,wave,sys
ROOT=Path(__file__).resolve().parents[2]; QA=Path(__file__).parent
ADB=ROOT/'.build-tools/platform-tools/adb.exe'; PKG='com.zecadev.outervoice'
def adb(*args): return subprocess.check_output([str(ADB),'-s','emulator-5554',*args],text=True,encoding='utf-8',errors='replace')
def dump(name):
    for _ in range(6):
        result=adb('shell','uiautomator','dump','/sdcard/qa.xml')
        if 'dumped to' in result: break
        time.sleep(1)
    adb('pull','/sdcard/qa.xml',str(QA/(name+'.xml')))
    return ET.parse(QA/(name+'.xml')).getroot()
def texts(tree): return [n.get('text') for n in tree.iter('node') if n.get('text')]
def point(tree,label,attr='text'):
    n=next(n for n in tree.iter('node') if n.get(attr)==label)
    a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')));return (a+c)//2,(b+d)//2
def click(tree,label,attr='text'):
    x,y=point(tree,label,attr);adb('shell','input','tap',str(x),str(y));time.sleep(.5)
adb('root');adb('wait-for-device')
version=sys.argv[1] if len(sys.argv)>1 else '1.1.1'
adb('install','-r',str(ROOT/('OuterVoice/dist/OuterVoice-'+version+'.apk')))
adb('shell','pm','grant',PKG,'android.permission.RECORD_AUDIO')
adb('shell','am','force-stop',PKG);adb('shell','am','start','-W','-n',PKG+'/.MainActivity');time.sleep(1)
t=dump('v111-home');assert 'Live Speaking' in texts(t) and 'Record & Play' not in texts(t)
x,y=point(t,'Hold to Speak','content-desc')
adb('shell','input','swipe',str(x),str(y),str(x),str(y),'2200');time.sleep(1)
t=dump('v111-released');assert any('BUS12' in s for s in texts(t)),texts(t)
files=adb('shell','ls','/data/data/'+PKG+'/cache').split();clips=[f for f in files if f.startswith('voice-')];assert len(clips)==1,files
raw=subprocess.check_output([str(ADB),'-s','emulator-5554','exec-out','cat','/data/data/'+PKG+'/cache/'+clips[0]])
with wave.open(io.BytesIO(raw),'rb') as wav:
    assert (wav.getframerate(),wav.getnchannels(),wav.getsampwidth())==(44100,1,2)
    assert wav.getnframes()>44100
    print('PASS hold/release WAV:',wav.getnframes(),'frames')
(QA/'v111-held-recording.wav').write_bytes(raw)
print('PASS release automatically attempts BUS12 playback; unavailable route reported')
click(t,'+ Add Sound');t=dump('v111-add');assert 'Add Sound' in texts(t) and 'Save' not in texts(t)
rx,ry=point(t,'Record microphone');click(t,'Record microphone');time.sleep(1);adb('shell','input','tap',str(rx),str(ry));time.sleep(1)
t=dump('v111-add-ready');assert 'Microphone recording.wav' in texts(t)
click(t,'Cancel');t=dump('v111-home-after-cancel');assert 'Hold to Speak' in texts(t)
adb('shell','input','swipe',str(x),str(y),str(x),str(y),'1000');time.sleep(.5)
adb('shell','input','keyevent','3');time.sleep(.5)
assert not any(f.startswith('voice-') for f in adb('shell','ls','/data/data/'+PKG+'/cache').split())
print('PASS Add Sound label and recording; background removes temporary WAV')
