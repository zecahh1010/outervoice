"""Native Android UI checks against the hidden local emulator; no real vehicle audio."""
from pathlib import Path
import re, subprocess, time, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ADB = ROOT / '.build-tools/platform-tools/adb.exe'
QA = ROOT / 'OuterVoice/qa'
def adb(*args):
    return subprocess.check_output([str(ADB), '-s', 'emulator-5554', *args], text=True, encoding='utf-8', errors='replace')
def tap(x,y):
    adb('shell','input','tap',str(x),str(y)); time.sleep(.6)
def dump(name):
    for attempt in range(3):
        output=adb('shell','uiautomator','dump','/sdcard/qa.xml')
        if 'dumped to' in output: break
        time.sleep(1)
    assert 'dumped to' in output, output
    adb('pull','/sdcard/qa.xml',str(QA/(name+'.xml')))
    return ET.parse(QA/(name+'.xml')).getroot()
def texts(tree): return [n.get('text') for n in tree.iter('node') if n.get('text')]
def texttap(tree, text):
    node=next(n for n in tree.iter('node') if n.get('text')==text)
    left,top,right,bottom=map(int,re.findall(r'\d+',node.get('bounds')))
    tap((left+right)//2,(top+bottom)//2)
def shot(name):
    adb('shell','screencap','-p','/sdcard/qa.png'); adb('pull','/sdcard/qa.png',str(QA/(name+'.png')))

for name in ['Welcome','Please wait','Thank you','Announcement','Meeting starting']:
    tap(895,115)
    tree=dump('add-step')
    assert 'Add sound' in texts(tree)
    tap(250,175); adb('shell','input','text',name.replace(' ','%s')); adb('shell','input','keyevent','4'); time.sleep(.4)
    tap(400,310)
    # Downloads grid and fixture coordinates were inspected in current.png/downloads.xml.
    # The Android 9 DocumentsUI accessibility bridge intermittently returns a null root.
    tap(278,338)
    tree=dump('import-step')
    assert 'welcome.wav' in texts(tree), texts(tree)
    if name=='Announcement': shot('add-filled')
    tap(870,540)
    tree=dump('save-step')
    assert 'Saved sounds' in texts(tree), texts(tree)
    print('PASS imported/named/saved:',name,flush=True)

shot('home-list')
adb('shell','input','swipe','800','500','800','230','400'); time.sleep(.5)
tree=dump('scrolled'); assert 'Meeting starting' in texts(tree)
shot('home-scrolled'); print('PASS scrolling exposes final sound',flush=True)
adb('shell','input','swipe','800','230','800','500','400'); time.sleep(.5)
tap(970,34); tree=dump('credits')
for value in ['Zeca','SL','Chris, j.Lun','1.0.0 (1)']: assert value in texts(tree), value
assert 'Based on' not in texts(tree)
shot('credits'); print('PASS app version and requested credits',flush=True)
tap(52,34); tap(174,254)
tree=dump('permission')
assert any('Allow' in value or 'ALLOW' in value for value in texts(tree)), texts(tree)
print('PASS runtime microphone permission requested',flush=True)
texttap(tree,next(value for value in texts(tree) if value.lower()=='deny'))
adb('shell','pm','grant','com.zecadev.outervoice','android.permission.RECORD_AUDIO')
tap(174,254); shot('bus12-unavailable')
print('Captured missing-BUS12 rejection after permission grant',flush=True)
time.sleep(3)
adb('shell','am','force-stop','com.zecadev.outervoice')
adb('shell','am','start','-W','-n','com.zecadev.outervoice/.MainActivity')
tree=dump('final-home'); assert 'Welcome' in texts(tree) and 'Please wait' in texts(tree)
shot('home-final'); print('PASS saved sound persistence after restart',flush=True)
log=adb('shell','logcat','-d','-s','AndroidRuntime:E')
(QA/'android-runtime.log').write_text(log,encoding='utf-8')
assert 'FATAL EXCEPTION' not in log, log
print('PASS no AndroidRuntime crash recorded',flush=True)
