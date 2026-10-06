"""Android 9 system-overlay checks; does not claim audible vehicle BUS12 output.

Requires emulator-5554 with the existing five saved-sound fixtures. Initial
permission phases are setup, permission, grant, other. Phase suite repeats the
final suite using the saved three-sound configuration from the permission phase.
Phase cancel checks cancelling a held recording by closing the panel.
"""
from pathlib import Path
import subprocess, time, re, xml.etree.ElementTree as ET, json, io, wave, sys

ROOT = Path(__file__).resolve().parents[2]
QA = Path(__file__).parent
ADB = ROOT / '.build-tools/platform-tools/adb.exe'
PKG = 'com.zecadev.outervoice'

def adb(*args):
    return subprocess.check_output([str(ADB), '-s', 'emulator-5554', *args], text=True, encoding='utf-8', errors='replace')

def dump(name):
    for _ in range(4):
        if 'dumped to' in adb('shell', 'uiautomator', 'dump', '/sdcard/qa.xml'):
            break
        time.sleep(.5)
    adb('pull', '/sdcard/qa.xml', str(QA / (name + '.xml')))
    return ET.parse(QA / (name + '.xml')).getroot()

def texts(tree): return [n.get('text') for n in tree.iter('node') if n.get('text')]

def point(tree, label, attr='text'):
    matches=[n for n in tree.iter('node') if (n.get(attr) or '').casefold() == label.casefold()]
    n=next((n for n in matches if n.get('clickable')=='true'),matches[0])
    a,b,c,d = map(int, re.findall(r'\d+', n.get('bounds')))
    return (a+c)//2, (b+d)//2

def tap(x,y):
    adb('shell', 'input', 'tap', str(x), str(y)); time.sleep(.35)

def click(tree,label,attr='text'): tap(*point(tree,label,attr))

def shot(name):
    adb('shell','screencap','-p','/sdcard/qa.png'); adb('pull','/sdcard/qa.png',str(QA/(name+'.png')))

def private(relative): return '/data/data/'+PKG+'/'+relative

def prefs():
    text=adb('shell','cat',private('shared_prefs/floating.xml'))
    return ET.fromstring(text)

def settings():
    tree=dump('v120-main')
    click(tree,'Floating Buttons settings','content-desc')
    tree=dump('v120-settings');shot('v120-settings')
    return tree

def setup():
    adb('root');adb('wait-for-device')
    adb('install','-r',str(ROOT/'OuterVoice/dist/OuterVoice-1.2.0.apk'))
    adb('shell','am','force-stop',PKG)
    adb('shell','appops','set',PKG,'SYSTEM_ALERT_WINDOW','deny')
    adb('shell','pm','grant',PKG,'android.permission.RECORD_AUDIO')
    adb('shell','am','start','-W','-n',PKG+'/.MainActivity')
    t=settings()
    assert '88 px' in texts(t) and 'Default: 88 px' in texts(t)
    assert all(c in texts(t) for c in ['Sky','Mint','Yellow','Peach','Light Red','Lavender','Aqua'])
    assert all(c in texts(t) for c in ['Angry','Thank You','Warmly Remind'])
    print('PASS settings screen defaults, seven colors, three icons',flush=True)
    return t

phase=sys.argv[1] if len(sys.argv)>1 else 'setup'
if phase=='setup':
    t=setup();print(texts(t),flush=True)
elif phase=='permission':
    t=dump('v120-settings-before-enable')
    click(t,'Enable floating panel')
    for name in ['Welcome','Please wait','Thank you']:
        t=dump('v120-selected-'+name.replace(' ','-'))
        click(t,'Show '+name+' in floating panel','content-desc')
    t=dump('v120-customize');click(t,'Color Light Red','content-desc')
    t=dump('v120-red');click(t,'Icon Angry','content-desc')
    t=dump('v120-angry');click(t,'Move up Thank you','content-desc')
    t=dump('v120-reordered');shot('v120-configured');click(t,'Save Settings')
    t=dump('v120-permission-explainer');assert 'Display over other apps' in texts(t)
    click(t,'Open Settings');t=dump('v120-android-overlay-permission');shot('v120-android-overlay-permission');print(texts(t),flush=True)
elif phase=='grant':
    t=dump('v120-permission-current')
    if 'Display over other apps' in texts(t):
        click(t,'Open Settings');t=dump('v120-android-overlay-permission');shot('v120-android-overlay-permission');print(texts(t),flush=True)
    click(t,'Allow display over other apps')
    adb('shell','input','keyevent','4');time.sleep(1)
    t=dump('v120-enabled');shot('v120-enabled');print(texts(t),flush=True)
elif phase=='other':
    adb('shell','am','start','-W','-a','android.settings.SETTINGS');time.sleep(.5)
    t=dump('v120-over-android-settings');shot('v120-over-android-settings');print(texts(t),flush=True)
elif phase=='inspect':
    t=dump('v120-current');shot('v120-current');print(texts(t),flush=True)
elif phase=='cancel':
    state=adb('shell','dumpsys','window','windows')
    block=re.search(r'Window #\d+ Window\{[^\n]+ u0 '+re.escape(PKG)+r'\}:(.*?)(?=  Window #|\Z)',state,re.S)
    x,y,x2,y2=map(int,re.search(r'mFrame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]',block.group(1)).groups())
    log=adb('shell','logcat','-d');before=log.count('Floating WAV saved:')
    px,py=x+44+48,y+8+44
    proc=subprocess.Popen([str(ADB),'-s','emulator-5554','shell','input','swipe',str(px),str(py),str(px),str(py),'3000'])
    time.sleep(1)
    assert any(n.startswith('floating-voice-') for n in adb('shell','ls',private('cache')).split())
    tap(x2-28,y+70);proc.wait();time.sleep(.5)
    assert not any(n.startswith('floating-voice-') for n in adb('shell','ls',private('cache')).split())
    assert adb('shell','logcat','-d').count('Floating WAV saved:')==before
    # Concurrent adb input streams cancel one another. Close again after the
    # original synthetic hold has ended, to verify the normal close click.
    tap(x2-28,y+70);time.sleep(.5)
    assert next(n for n in prefs() if n.get('name')=='enabled').get('value')=='false'
    print('PASS cancelled held gesture discards WAV and suppresses playback; close disables panel',flush=True)
elif phase=='suite':
    def pref_value(name):
        return next(node for node in prefs() if node.get('name')==name)
    def overlay():
        state=adb('shell','dumpsys','window','windows')
        block=re.search(r'Window #\d+ Window\{[^\n]+ u0 '+re.escape(PKG)+r'\}:(.*?)(?=  Window #|\Z)',state,re.S)
        assert block, 'No system overlay window'
        assert 'ty=APPLICATION_OVERLAY' in block.group(1)
        frame=re.search(r'mFrame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]',block.group(1))
        return tuple(map(int,frame.groups())),block.group(1)
    def open_main():
        adb('shell','am','start','-W','-n',PKG+'/.MainActivity');time.sleep(.5)
    def other():
        adb('shell','am','start','-W','-a','android.settings.SETTINGS');time.sleep(.5)
        state=adb('shell','dumpsys','activity','activities')
        assert 'mResumedActivity' in state and 'com.android.settings' in state
    adb('root');adb('wait-for-device')
    adb('install','-r',str(ROOT/'OuterVoice/dist/OuterVoice-1.2.0.apk'))
    adb('shell','logcat','-c');open_main()
    t=settings();shot('v120-final-settings')
    for label in ['Angry','Thank You','Warmly Remind']:
        node=next(n for n in t.iter('node') if n.get('text')==label)
        a,b,c,d=map(int,re.findall(r'\d+',node.get('bounds')))
        assert c>a and d>b and d<=600, (label,node.get('bounds'))
    print('PASS final settings labels visible',flush=True)
    click(t,'Reset')
    seek=next(n for n in t.iter('node') if n.get('content-desc')=='Floating button size')
    a,b,c,d=map(int,re.findall(r'\d+',seek.get('bounds')));tap(c-2,(b+d)//2)
    t=dump('v120-size-144');assert '144 px' in texts(t),texts(t)
    click(t,'Save Settings');time.sleep(.5);other()
    frame,window=overlay();assert frame[2]-frame[0]==712 and frame[3]-frame[1]==212,(frame,window)
    assert pref_value('size').get('value')=='144'
    shot('v120-panel-144-over-settings')
    config=json.loads(pref_value('items').text)
    selected=[v for v in config if v['selected']]
    saved=ET.fromstring(adb('shell','cat',private('shared_prefs/sounds.xml')))
    names={v['id']:v['name'] for v in json.loads(next(n.text for n in saved if n.get('name')=='items'))}
    assert [names[v['id']] for v in selected]==['Welcome','Thank you','Please wait']
    assert selected[1]['color']==4 and selected[1]['icon']==0
    print('PASS color, icon, order persistence; actual 144-pixel circles; overlay above Settings',flush=True)
    x,y,x2,y2=frame
    target_x=160 if x<=100 else 60
    target_y=120 if y>180 else 300
    adb('shell','input','swipe',str(x+26),str(y+97),str(target_x+26),str(target_y+97),'600');time.sleep(.5)
    moved,_=overlay();assert moved!=frame,(frame,moved)
    position=(pref_value('x').get('value'),pref_value('y').get('value'))
    shot('v120-panel-moved')
    # Hold using a real down/up touch sequence, without interrupting capture to dump UI.
    x,y,x2,y2=moved
    px,py=x+44+76,y+8+72
    proc=subprocess.Popen([str(ADB),'-s','emulator-5554','shell','input','swipe',str(px),str(py),str(px),str(py),'2400'])
    time.sleep(1)
    clips=[n for n in adb('shell','ls',private('cache')).split() if n.startswith('floating-voice-')]
    assert len(clips)==1,clips
    raw=subprocess.check_output([str(ADB),'-s','emulator-5554','exec-out','cat',private('cache/'+clips[0])])
    assert raw[:4]==b'RIFF' and len(raw)>44+10000
    proc.wait();time.sleep(.6)
    log=adb('shell','logcat','-d')
    assert 'Floating WAV saved: 44100 Hz channels=1; PCM16 bytes=' in log
    assert 'Floating playback: Outer speaker is unavailable (BUS12)' in log
    assert not any(n.startswith('floating-voice-') for n in adb('shell','ls',private('cache')).split())
    shot('v120-panel-live-release')
    print('PASS overlay hold/release captures PCM16 WAV, finalizes header, requests BUS12 playback, cleans clip',flush=True)
    before=log.count('Floating playback: Outer speaker is unavailable (BUS12)')
    tap(x+44+152+4+76,y+80)
    log=adb('shell','logcat','-d');assert log.count('Floating playback: Outer speaker is unavailable (BUS12)')>before
    print('PASS saved-sound button requests playback',flush=True)
    # Close removes the service/window and persists disabled state.
    tap(x2-28,y+90);time.sleep(.5)
    assert pref_value('enabled').get('value')=='false'
    state=adb('shell','dumpsys','window','windows');assert not re.search(r'Window #\d+ Window\{[^\n]+ u0 '+re.escape(PKG)+r'\}:',state)
    open_main();t=settings()
    check=next(n for n in t.iter('node') if n.get('text')=='Enable floating panel');assert check.get('checked')=='false'
    click(t,'Enable floating panel');click(t,'Save Settings');time.sleep(.5);other()
    restored,_=overlay();assert (pref_value('x').get('value'),pref_value('y').get('value'))==position
    assert restored==moved,(restored,moved)
    print('PASS close/disable and drag-position restoration',flush=True)
    open_main();t=settings();click(t,'Enable floating panel');click(t,'Save Settings');time.sleep(.5)
    assert pref_value('enabled').get('value')=='false'
    state=adb('shell','dumpsys','window','windows');assert not re.search(r'Window #\d+ Window\{[^\n]+ u0 '+re.escape(PKG)+r'\}:',state)
    print('PASS checkbox disables system overlay',flush=True)
    open_main();t=settings();click(t,'Reset');click(t,'Enable floating panel');click(t,'Save Settings');time.sleep(.5);other()
    frame,_=overlay();assert frame[2]-frame[0]==488 and frame[3]-frame[1]==156
    shot('v120-final-panel-over-settings')
    log=adb('shell','logcat','-d','-s','AndroidRuntime');assert 'FATAL EXCEPTION' not in log,log
    print('PASS reset returns to 88 pixels; no AndroidRuntime crashes',flush=True)
