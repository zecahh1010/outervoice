"""Reorderable Live Speak and microphone-only enlargement on emulator-5554."""
from pathlib import Path
import sys,runpy,time,json,re,xml.etree.ElementTree as ET,subprocess
from PIL import Image
sys.argv=['helpers','helpers']
h=runpy.run_path(str(Path(__file__).with_name('check-v120.py')))
adb,dump,texts,click,tap,point,shot,private=[h[k] for k in ['adb','dump','texts','click','tap','point','shot','private']]
ROOT,PKG,ADB,QA=[h[k] for k in ['ROOT','PKG','ADB','QA']]
def prefs(): return ET.fromstring(adb('shell','cat',private('shared_prefs/floating.xml')))
def values():
    p=prefs();return json.loads(next(n.text for n in p if n.get('name')=='items'))
def panel():
    state=adb('shell','dumpsys','window','windows')
    block=re.search(r'Window #\d+ Window\{[^\n]+ u0 '+re.escape(PKG)+r'\}:(.*?)(?=  Window #|\Z)',state,re.S)
    assert block and 'ty=APPLICATION_OVERLAY' in block.group(1)
    return tuple(map(int,re.search(r'mFrame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]',block.group(1)).groups()))
def open_config():
    adb('shell','am','start','-W','-n',PKG+'/.MainActivity');time.sleep(.3)
    t=dump('v122-main');click(t,'Floating Buttons settings','content-desc');return dump('v122-settings')
def set_percent(t,value):
    x,y=point(t,'Live Speak icon enlargement percent','content-desc');tap(x,y)
    adb('shell','input','keyevent','123');adb('shell','input','keyevent','67','67','67')
    if value: adb('shell','input','text',value)
    adb('shell','input','keyevent','4');time.sleep(.3)
    return dump('v122-percent-'+(value or 'blank'))
def other(): adb('shell','am','start','-W','-a','android.settings.SETTINGS');time.sleep(.4)
def mic_bbox(name,frame,index):
    # In the teal circle, bright white pixels are the fixed microphone glyph.
    im=Image.open(QA/(name+'.png')).convert('RGB');cx=frame[0]+92+index*100;cy=frame[1]+52
    points=[]
    for y in range(cy-39,cy+40):
        for x in range(cx-39,cx+40):
            if (x-cx)**2+(y-cy)**2<=39**2 and min(im.getpixel((x,y)))>=240: points.append((x,y))
    assert points
    return max(y for x,y in points)-min(y for x,y in points)+1
adb('root');adb('wait-for-device');adb('install','-r',str(ROOT/'OuterVoice/dist/OuterVoice-1.2.2.apk'))
adb('shell','logcat','-c');adb('shell','am','force-stop',PKG)
# Recreate the legacy preference shape for a repeatable migration check.
p=prefs()
for n in list(p):
    if n.get('name')=='mic_enlargement': p.remove(n)
    elif n.get('name')=='items': n.text=json.dumps([v for v in json.loads(n.text) if v['id']!='live-speak'])
legacy=QA/'v122-legacy-floating.xml';ET.ElementTree(p).write(legacy,encoding='utf-8',xml_declaration=True)
adb('push',str(legacy),private('shared_prefs/floating.xml'))
before=values();old_order=[v['id'] for v in before if v['selected']]
t=open_config();shot('v122-live-customization')
assert 'Panel buttons' in texts(t) and '50' in texts(t) and 'Microphone icon (fixed)' in texts(t)
assert not any((n.get('content-desc') or '').startswith('Icon ') for n in t.iter('node'))
live=next(n for n in t.iter('node') if n.get('content-desc')=='Live Speak is always included')
assert live.get('checked')=='true' and live.get('enabled')=='false'
click(t,'Move down Live Speak','content-desc');t=dump('v122-live-second')
click(t,'Save Settings');time.sleep(.4)
after=values();assert [v['id'] for v in after if v['selected']]==[old_order[0],'live-speak']+old_order[1:]
for item in before: assert next(v for v in after if v['id']==item['id'])==item
assert next(n for n in prefs() if n.get('name')=='mic_enlargement').get('value')=='50'
other();f50=panel();shot('v122-panel-50');height50=mic_bbox('v122-panel-50',f50,1)
print('PASS upgrade defaults to 50%; Live Speak moves among selected sounds; fixed mic; existing settings preserved',flush=True)
t=open_config();click(t,'Live Speak');t=dump('v122-live-edit');t=set_percent(t,'999');click(t,'Save Settings');t=dump('v122-invalid')
assert 'Floating Buttons' in texts(t)
assert next(n for n in prefs() if n.get('name')=='mic_enlargement').get('value')=='50'
t=set_percent(t,'');click(t,'Save Settings');t=dump('v122-empty-invalid');assert 'Floating Buttons' in texts(t)
t=set_percent(t,'0');click(t,'Save Settings');time.sleep(.4);other();f0=panel();shot('v122-panel-0');height0=mic_bbox('v122-panel-0',f0,1)
assert f0==f50,(f0,f50)
assert 1.4<height50/height0<1.6,(height0,height50)
# Saved-sound glyph/circle pixels are identical when changing only mic enlargement.
im0=Image.open(QA/'v122-panel-0.png');im50=Image.open(QA/'v122-panel-50.png')
box=(f0[0]+48,f0[1]+8,f0[0]+136,f0[1]+96)
assert im0.crop(box).tobytes()==im50.crop(box).tobytes()
print('PASS invalid/blank values block save; 0% vs 50% changes only microphone glyph (heights '+str(height0)+', '+str(height50)+')',flush=True)
t=open_config();click(t,'Live Speak');t=dump('v122-live-edit-100');t=set_percent(t,'100');click(t,'Save Settings');time.sleep(.4);other();f100=panel();shot('v122-panel-100');height100=mic_bbox('v122-panel-100',f100,1)
assert 1.85<height100/height0<2.15,(height0,height100)
adb('shell','am','force-stop',PKG);t=open_config();click(t,'Live Speak');t=dump('v122-persisted');assert '100' in texts(t)
click(t,'Move down Live Speak','content-desc');t=dump('v122-live-third');t=set_percent(t,'50');click(t,'Save Settings');time.sleep(.4);other()
assert [v['id'] for v in values() if v['selected']][2]=='live-speak'
frame=panel();shot('v122-final-panel');x=frame[0]+292;y=frame[1]+52
adb('shell','input','swipe',str(x),str(y),str(x),str(y),'2200');time.sleep(.5)
log=adb('shell','logcat','-d');assert 'Floating WAV saved: 44100 Hz channels=1; PCM16 bytes=' in log
assert 'Floating playback: Outer speaker is unavailable (BUS12)' in log
assert not any(n.startswith('floating-voice-') for n in adb('shell','ls',private('cache')).split())
print('PASS 100% enlargement and position persist; hold/release works with Live Speak in third position above other apps',flush=True)
assert 'FATAL EXCEPTION' not in adb('shell','logcat','-d','-s','AndroidRuntime')
