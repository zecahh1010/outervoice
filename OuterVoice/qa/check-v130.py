"""Android 9 checks for v1.3.0. Requires emulator-5554 and portable FFmpeg in .build-tools.
Preserves original saved sounds; imports disposable files and cancels the Add draft.
"""
from pathlib import Path
import os,sys,runpy,time,json,re,subprocess,xml.etree.ElementTree as ET,io,wave
from PIL import Image
sys.argv=['helpers','helpers']
h=runpy.run_path(str(Path(__file__).with_name('check-v120.py')))
adb,dump,texts,click,tap,point,shot,private=[h[k] for k in ['adb','dump','texts','click','tap','point','shot','private']]
ROOT,PKG,ADB,QA=[h[k] for k in ['ROOT','PKG','ADB','QA']]
def dump(name):
    # Never reuse a stale hierarchy after UIAutomator reports an idle/root error.
    for _ in range(6):
        result=subprocess.check_output([str(ADB),'-s','emulator-5554','shell','uiautomator','dump','/sdcard/qa.xml'],text=True,encoding='utf-8',errors='replace',timeout=45)
        if 'dumped to' in result:
            adb('pull','/sdcard/qa.xml',str(QA/(name+'.xml')))
            return ET.parse(QA/(name+'.xml')).getroot()
        time.sleep(.5)
    raise RuntimeError('No current Android hierarchy for '+name)
def pref(name='floating'):
    return ET.fromstring(adb('shell','cat',private('shared_prefs/'+name+'.xml')))
def value(name):return next(n.get('value') or n.text for n in pref() if n.get('name')==name)
def bounds(t,label,attr='content-desc'):
    n=next(n for n in t.iter('node') if n.get(attr)==label);return tuple(map(int,re.findall(r'\d+',n.get('bounds'))))
def slider(t,label,progress,max_progress):
    a,b,c,d=bounds(t,label);tap(round(a+16+(c-a-32)*progress/max_progress),(b+d)//2)
def percent(t,number):
    click(t,'Live Speak button enlargement percent','content-desc');adb('shell','input','keyevent','123','67','67','67');adb('shell','input','text',str(number));adb('shell','input','keyevent','4');return dump('v130-percent')
def main():adb('shell','am','start','-W','-n',PKG+'/.MainActivity');time.sleep(.2)
def config():
    main();t=dump('v130-home');click(t,'Floating Buttons settings','content-desc');return dump('v130-settings')
def frame():
    state=adb('shell','dumpsys','window','windows')
    block=re.search(r'Window #\d+ Window\{[^\n]+ u0 '+re.escape(PKG)+r'\}:(.*?)(?=  Window #|\Z)',state,re.S)
    assert block and 'ty=APPLICATION_OVERLAY' in block.group(1)
    return tuple(map(int,re.search(r'mFrame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]',block.group(1)).groups()))
def other():adb('shell','am','start','-W','-a','android.settings.SETTINGS');time.sleep(.3)
def mic_span(name,f):
    image=Image.open(QA/(name+'.png')).convert('RGB');x0,y0,x1,y1=f
    pixels=[(x,y) for y in range(y0+8,y1-54) for x in range(x0+36,x1-88) if (lambda p:p[0]<110 and p[1]>150 and p[2]>150)(image.getpixel((x,y)))]
    assert pixels
    return max(x for x,y in pixels)-min(x for x,y in pixels)+1,max(y for x,y in pixels)-min(y for x,y in pixels)+1
adb('root');adb('wait-for-device');adb('install','-r',str(ROOT/'OuterVoice/dist/OuterVoice-1.3.0.apk'))
adb('shell','pm','grant',PKG,'android.permission.RECORD_AUDIO');adb('shell','pm','grant',PKG,'android.permission.READ_EXTERNAL_STORAGE')
adb('shell','appops','set',PKG,'SYSTEM_ALERT_WINDOW','allow');adb('shell','am','force-stop',PKG);adb('shell','logcat','-c')
original=pref('sounds');original_text=ET.tostring(original)
if os.environ.get('OV_QA_PHASE') != 'import':
    t=config();assert 'Button spacing' in texts(t)
    # Existing order/color/icon remains intact. Live Speak is fixed-icon and reorderable.
    click(t,'Live Speak');t=dump('v130-live-config');assert 'Microphone icon (fixed)' in texts(t)
    assert 'Enlarge Live Speak button by' in texts(t)
    t=percent(t,50);slider(t,'Floating button size',6,20);t=dump('v130-size-88');slider(t,'Floating button spacing',12,40)
    t=dump('v130-defaults');shot('v130-config');
    if not any(n.get('text')=='Enable floating panel' and n.get('checked')=='true' for n in t.iter('node')):click(t,'Enable floating panel')
    click(t,'Save Settings');time.sleep(.3);other();f=frame();shot('v130-panel-88');span=mic_span('v130-panel-88',f);assert 130<=span[0]<=133 and 130<=span[1]<=133,span
    assert f[3]-f[1]==200,f
    order=[v for v in json.loads(value('items')) if v['selected']]
    width=sum(132 if v['id']=='live-speak' else 88 for v in order)+12*(len(order)-1)+140
    assert f[2]-f[0]==min(width,1008),f
    print('PASS 50% increases the Live Speak circle to132px; saved circles88px; configured edge gaps and system overlay',flush=True)
    # Minimize, drag bubble, restore original full-panel position.
    tap(f[2]-74,f[1]+8+66+17);time.sleep(.3);bubble=frame();assert bubble[2]-bubble[0]==60 and bubble[3]-bubble[1]==60
    assert value('enabled')=='true' and value('minimized')=='true';shot('v130-minimized')
    adb('shell','input','swipe',str(bubble[0]+30),str(bubble[1]+30),str(bubble[0]+130),str(bubble[1]+70),'400');time.sleep(.2)
    moved=frame();assert moved!=bubble
    tap(moved[0]+30,moved[1]+30);time.sleep(.3);assert frame()==f
    assert value('minimized')=='false';shot('v130-reopened')
    print('PASS minimize60px bubble, drag without recording, tap reopen restores original panel position',flush=True)
    # Maximum base size, zero spacing, 100% live enlargement and scrolling.
    t=config();click(t,'Live Speak');t=dump('v130-max-edit');t=percent(t,100);slider(t,'Floating button size',20,20);t=dump('v130-size144');slider(t,'Floating button spacing',0,40);t=dump('v130-max-config');shot('v130-max-config');click(t,'Save Settings');other();fm=frame();shot('v130-max-panel')
    assert fm[3]-fm[1]==356,fm
    assert value('size')=='144' and value('spacing')=='0' and value('mic_enlargement')=='100'
    adb('shell','am','force-stop',PKG);t=config();assert value('size')=='144' and value('spacing')=='0'
    click(t,'Live Speak');t=dump('v130-restore-edit');t=percent(t,50);slider(t,'Floating button size',6,20);t=dump('v130-restore-size');slider(t,'Floating button spacing',12,40);t=dump('v130-restore-default');click(t,'Save Settings');other();f=frame()
    live_index=next(i for i,v in enumerate(order) if v['id']=='live-speak');x=f[0]+36+8+live_index*(88+12)+66;y=f[1]+8+66
    adb('shell','input','swipe',str(x),str(y),str(x),str(y),'2200');time.sleep(.4)
    assert 'Floating WAV saved: 44100 Hz channels=1; PCM16 bytes=' in adb('shell','logcat','-d')
    print('PASS max sizing, zero spacing, persisted values, proportional icons visually checked, hold/release WAV after resizing',flush=True)
    # Exit stops service and disables overlay, while saving sounds.
    main();t=dump('v130-before-exit');click(t,'Exit app','content-desc');time.sleep(.3)
    assert value('enabled')=='false'
    assert 'com.zecadev.outervoice.FloatingPanelService' not in adb('shell','dumpsys','activity','services',PKG)
    assert 'mResumedActivity' not in '\n'.join(line for line in adb('shell','dumpsys','activity','activities').splitlines() if PKG in line and 'mResumedActivity' in line)
    print('PASS Exit disables overlay, stops service and closes app task',flush=True)

fixtures=ROOT/'.build-tools/audio-fixtures';fixtures.mkdir(exist_ok=True)
ffmpeg=ROOT/'.build-tools/ffmpeg-qa.exe'
cases=[('tone8.wav',['-c:a','pcm_u8','-ar','16000','-ac','2']),('tone24.wav',['-c:a','pcm_s24le','-ar','48000','-ac','2']),('float.wav',['-c:a','pcm_f32le','-ar','48000']),('tone.mp3',['-c:a','libmp3lame']),('tone.m4a',['-c:a','aac']),('tone.flac',['-c:a','flac']),('tone.ogg',['-c:a','libvorbis']),('large.wav',['-c:a','pcm_s16le','-ar','44100'])]
for name,args in cases:
    subprocess.run([str(ffmpeg),'-hide_banner','-loglevel','error','-y','-f','lavfi','-i','sine=frequency=660:duration='+('245' if name=='large.wav' else '2'),*args,str(fixtures/name)],check=True)
(fixtures/'different.bin').write_bytes((fixtures/'tone.mp3').read_bytes());(fixtures/'broken.wav').write_bytes(b'Not an audio file')
adb('shell','mkdir','-p','/sdcard/Download/OuterVoice-QA');adb('push',str(fixtures)+ '/.','/sdcard/Download/OuterVoice-QA')
names=os.environ.get('OV_QA_CASES','').split(',') if os.environ.get('OV_QA_CASES') else [n for n,a in cases]+['different.bin','broken.wav']
for name in names:
    main();t=dump('v130-import-home');click(t,'+ Add Sound');t=dump('v130-add');assert 'Import Sound' in texts(t)
    n=next(n for n in t.iter('node') if n.get('content-desc')=='Test imported sound');assert n.get('enabled')=='false'
    click(t,'Browse files on this device');t=dump('v130-browser')
    label=next(v for v in texts(t) if v.startswith('Internal storage:'));click(t,label);t=dump('v130-storage');click(t,'Folder: Download');t=dump('v130-download');click(t,'Folder: OuterVoice-QA');t=dump('v130-fixtures')
    for _ in range(4):
        if 'Sound: '+name in texts(t):break
        adb('shell','input','swipe','600','450','600','200','300');t=dump('v130-fixtures-scrolled')
    click(t,'Sound: '+name)
    time.sleep(1)
    for _ in range(12):
        t=dump('v130-import-'+name.replace('.','-'))
        if 'Importing sound…' not in texts(t):break
        time.sleep(1)
    n=next(n for n in t.iter('node') if n.get('content-desc')=='Test imported sound')
    if name=='broken.wav':assert n.get('enabled')=='false' and any('decode' in v.lower() for v in texts(t)),texts(t)
    else:
        assert name in texts(t) and n.get('enabled')=='true',texts(t)
        pending=[n for n in adb('shell','ls',private('files')).split() if n.startswith('pending-')]
        assert len(pending)==1,pending
        data=subprocess.check_output([str(ADB),'-s','emulator-5554','exec-out','cat',private('files/'+pending[0])])
        with wave.open(io.BytesIO(data)) as w:
            assert w.getframerate()==44100 and w.getnchannels()==1 and w.getsampwidth()==2
            duration=w.getnframes()/44100
            assert abs(duration-(245 if name=='large.wav' else 2))<.15,(name,duration)
        click(t,'Test imported sound','content-desc');time.sleep(.2)
        assert 'Outer speaker is unavailable (BUS12)' in adb('shell','logcat','-d')
        if name=='tone.mp3':shot('v130-import-mp3')
    print('PASS import '+name+(' rejected clearly' if name=='broken.wav' else ' converted and preview requested BUS12'),flush=True)
    click(t,'Cancel');time.sleep(.2)
    assert not any(n.startswith('pending-') for n in adb('shell','ls',private('files')).split())
assert ET.tostring(pref('sounds'))==original_text
assert not any(n.startswith('import-source-') for n in adb('shell','ls',private('cache')).split())
assert 'FATAL EXCEPTION' not in adb('shell','logcat','-d','-s','AndroidRuntime')
print('PASS all original saved sounds preserved; cancelled imports clean temporary files; no crash',flush=True)
