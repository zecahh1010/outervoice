"""Final APK visual check and minimized-state persistence, after the import matrix."""
from pathlib import Path
import sys,runpy,time,xml.etree.ElementTree as ET,json,re
sys.argv=['helpers','helpers'];h=runpy.run_path(str(Path(__file__).with_name('check-v120.py')))
adb,dump,texts,click,tap,shot,private=[h[k] for k in ['adb','dump','texts','click','tap','shot','private']]
PKG=h['PKG']
def frame():
    state=adb('shell','dumpsys','window','windows')
    block=re.search(r'Window #\d+ Window\{[^\n]+ u0 '+re.escape(PKG)+r'\}:(.*?)(?=  Window #|\Z)',state,re.S)
    assert block and 'ty=APPLICATION_OVERLAY' in block.group(1)
    return tuple(map(int,re.search(r'mFrame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]',block.group(1)).groups()))
adb('shell','am','start','-W','-n',PKG+'/.MainActivity');t=dump('v130-final-home');shot('v130-final-home')
assert any(n.get('content-desc')=='Exit app' for n in t.iter('node'))
click(t,'Floating Buttons settings','content-desc');t=dump('v130-final-settings');click(t,'Live Speak');t=dump('v130-final-live-settings');shot('v130-final-settings')
help_node=next(n for n in t.iter('node') if (n.get('text') or '').startswith('0–100%. Button and microphone'))
assert int(re.findall(r'\d+',help_node.get('bounds'))[-1])<=406
if not any(n.get('text')=='Enable floating panel' and n.get('checked')=='true' for n in t.iter('node')):click(t,'Enable floating panel')
click(t,'Save Settings');adb('shell','am','start','-W','-a','android.settings.SETTINGS');time.sleep(.3)
f=frame();shot('v130-final-overlay');tap(f[2]-74,f[1]+91);time.sleep(.3);b=frame();assert b[2]-b[0]==60
adb('shell','am','force-stop',PKG);adb('shell','am','start','-W','-n',PKG+'/.MainActivity');time.sleep(.3)
assert frame()==b,(b,frame())
tap(b[0]+30,b[1]+30);time.sleep(.3);assert frame()==f
t=dump('v130-final-exit');click(t,'Exit app','content-desc');time.sleep(.3)
assert 'FATAL EXCEPTION' not in adb('shell','logcat','-d','-s','AndroidRuntime')
print('PASS final APK home/settings visual controls and complete helper text; minimized restart restores bubble; reopen restores panel; Exit',flush=True)
def imported(tree, name):
    click(tree,'Browse files on this device');tree=dump('v130-replacement-browser')
    click(tree,next(v for v in texts(tree) if v.startswith('Internal storage:')));tree=dump('v130-replacement-storage')
    click(tree,'Folder: Download');tree=dump('v130-replacement-download');click(tree,'Folder: OuterVoice-QA');tree=dump('v130-replacement-files')
    for _ in range(4):
        if 'Sound: '+name in texts(tree):break
        adb('shell','input','swipe','600','450','600','200','300');tree=dump('v130-replacement-files-scroll')
    click(tree,'Sound: '+name);time.sleep(1);return dump('v130-replacement-result')
adb('shell','am','start','-W','-n',PKG+'/.MainActivity');t=dump('v130-replacement-home');click(t,'+ Add Sound');t=dump('v130-replacement-add')
t=imported(t,'tone.mp3');assert any(n.get('content-desc')=='Test imported sound' and n.get('enabled')=='true' for n in t.iter('node'))
t=imported(t,'broken.wav');assert any(n.get('content-desc')=='Test imported sound' and n.get('enabled')=='false' for n in t.iter('node'))
assert not any(n.startswith('pending-') for n in adb('shell','ls',private('files')).split())
click(t,'Cancel');t=dump('v130-replacement-cancel');click(t,'Exit app','content-desc')
print('PASS failed replacement clears stale preview audio and disables Play; draft cancels cleanly',flush=True)
