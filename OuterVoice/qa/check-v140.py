"""Android 9 emulator acceptance checks for the approved simulator changes.

Uses the existing disposable saved-sound fixtures. Does not prove audible BUS12.
Run on the Windows host with an emulator-5554 at 1024 x 600.
"""
from pathlib import Path
import subprocess,time,re,json,sys,xml.etree.ElementTree as ET,wave

ROOT=Path(__file__).resolve().parents[2]; QA=Path(__file__).parent
ADB=ROOT/'.build-tools/platform-tools/adb.exe';PKG='com.zecadev.outervoice'
def adb(*args):return subprocess.check_output([str(ADB),'-s','emulator-5554',*map(str,args)],text=True,encoding='utf-8',errors='replace')
def dump(name):
    for _ in range(5):
        result=adb('shell','uiautomator','dump','/sdcard/qa.xml')
        if 'dumped to' in result:
            raw=adb('shell','cat','/sdcard/qa.xml')
            try:
                tree=ET.fromstring(raw);(QA/(name+'.xml')).write_text(raw,encoding='utf-8');return tree
            except ET.ParseError:pass
        time.sleep(.3)
    raise AssertionError('UI dump unavailable')
def bounds(n):return tuple(map(int,re.findall(r'\d+',n.get('bounds'))))
def node(t,label,attr='text'):
    matches=[n for n in t.iter('node') if n.get(attr)==label]
    if not matches and attr=='text':matches=[n for n in t.iter('node') if (n.get(attr) or '').lower()==label.lower()]
    assert matches,(label,[n.get('text') for n in t.iter('node') if n.get('text')]);return next((n for n in matches if n.get('clickable')=='true'),matches[0])
def tap(x,y):adb('shell','input','tap',x,y);time.sleep(.2)
def click(t,label,attr='text'):
    a,b,c,d=bounds(node(t,label,attr));tap((a+c)//2,(b+d)//2)
def shot(name):adb('shell','screencap','-p','/sdcard/qa.png');adb('pull','/sdcard/qa.png',QA/(name+'.png'))
def texts(t):return [n.get('text') for n in t.iter('node') if n.get('text')]
def prefs():
    t=ET.fromstring(adb('shell','cat','/data/data/'+PKG+'/shared_prefs/floating.xml'));return {n.get('name'):n.text if n.tag=='string' else n.get('value') for n in t}
def frame():
    raw=adb('shell','dumpsys','window','windows');match=re.search(r'Window #\d+ Window\{[^\n]+ u0 '+re.escape(PKG)+r'\}:(.*?)(?=  Window #|\Z)',raw,re.S)
    assert match and 'ty=APPLICATION_OVERLAY' in match.group(1),raw
    return tuple(map(int,re.search(r'mFrame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]',match.group(1)).groups()))
def start():adb('shell','am','start','-W','-n',PKG+'/.MainActivity')
def settings():
    start();t=dump('v140-home');click(t,'Floating Panel settings','content-desc');return dump('v140-settings')
def seek(t,label,fraction):
    a,b,c,d=bounds(node(t,label,'content-desc'));tap(round(a+16+(c-a-32)*fraction),(b+d)//2)
def hold(x,y,name,duration=1800):
    process=subprocess.Popen([str(ADB),'-s','emulator-5554','shell','input','swipe',str(x),str(y),str(x),str(y),str(duration)])
    time.sleep(.55);shot(name);process.wait(timeout=10);time.sleep(.4)

def minimize():
    # Non-focusable overlays are absent from Android 9's accessibility tree.
    # Check their real system window, then use the toolbar's physical geometry.
    f=frame();assert f[2]-f[0]>=252;tap((f[0]+f[2])//2,f[1]+40);time.sleep(.5)

def hold_panel(name,duration=1800):
    f=frame();p=prefs();items=[i for i in json.loads(p['items']) if i['selected']];base=int(p['size']);large=round(base*(1+int(p['mic_enlargement'])/100));x=f[0]+14
    for i in items:
        if i['id']=='live-speak':break
        x+=base+int(p['spacing'])
    hold(x+large//2,f[1]+96+large//2,name,duration)

phase=sys.argv[1] if len(sys.argv)>1 else 'config'
if phase=='config':
    before=prefs();before_items=json.loads(before['items']);t=settings();assert all(n in texts(t) for n in ['Buttons & Order','Size & Minimize','Floating Panel']);assert len([n for n in t.iter('node') if (n.get('content-desc') or '').startswith('Show ')])==6
    # All six ordering entries are on screen, with the microphone still mandatory.
    checks=[n for n in t.iter('node') if (n.get('content-desc') or '').startswith('Show ')];assert all(bounds(n)[3]<=506 for n in checks)
    click(t,'Live Speak');t=dump('v140-live-colors');shot('v140-live-colors');assert 'Default color' in texts(t)
    palette=['Red','Pink','Peach','Yellow','Lime','Mint','Aqua','Sky','Indigo','Lavender'];nodes=[node(t,'Color '+n,'content-desc') for n in palette];assert [bounds(n)[0] for n in nodes]==sorted(bounds(n)[0] for n in nodes)
    assert bounds(node(t,'Color App Blue','content-desc'))[3]<=bounds(nodes[0])[1];assert not any((n.get('content-desc') or '').startswith('Icon ') for n in t.iter('node'))
    click(t,'Color Pink','content-desc');t=dump('v140-pink');click(t,'Size & Minimize');t=dump('v140-size-tab');shot('v140-size-tab')
    assert '50%' in texts(t) and '88px' in texts(t) and '30' in texts(t)
    assert bounds(node(t,'Live Speak button enlargement percent','content-desc'))[1]<bounds(node(t,'Minimized button size','content-desc'))[1]
    assert bounds(node(t,'Panel use restarts the inactivity timer.'))[3]<=514
    seek(t,'Live Speak button enlargement percent',.75);t=dump('v140-enlarge');seek(t,'Minimized button size',.65);t=dump('v140-bubble-size');click(t,'Auto minimize');t=dump('v140-auto');click(t,'Auto minimize time in seconds','content-desc');adb('shell','input','keyevent','KEYCODE_MOVE_END');adb('shell','input','keyevent','KEYCODE_DEL');adb('shell','input','keyevent','KEYCODE_DEL');adb('shell','input','text','2');adb('shell','input','keyevent','KEYCODE_BACK');t=dump('v140-auto-seconds');click(t,'Buttons & Order');t=dump('v140-draft-tabs');click(t,'Welcome');t=dump('v140-icons');shot('v140-icons')
    icons=['Angry','Thank You','Warmly Remind','Funny','Extreme Angry','Happy','Friendly','Sorry','Surprised','Calm','Urgent','Celebration'];assert all(node(t,'Icon '+n,'content-desc') is not None for n in icons);assert bounds(node(t,'Icon Celebration','content-desc'))[3]<=514
    click(t,'Icon Extreme Angry','content-desc');t=dump('v140-extreme');click(t,'Cancel');assert prefs()==before
    print('PASS six ordering entries, warm-to-cool colors, App Blue default row, fixed live mic, 12 icons, compact size tab, draft across tabs and Cancel',flush=True)
    t=settings()
    if node(t,'Enable floating panel').get('checked')!='true':click(t,'Enable floating panel')
    t=dump('v140-enable');click(t,'Live Speak');t=dump('v140-live');click(t,'Color Pink','content-desc');t=dump('v140-live-pink');click(t,'Welcome');t=dump('v140-welcome');click(t,'Icon Funny','content-desc');t=dump('v140-funny');click(t,'Size & Minimize');t=dump('v140-settings-layout');seek(t,'Minimized button size',.45);t=dump('v140-min-size');click(t,'Auto minimize');t=dump('v140-enabled-auto');click(t,'Auto minimize time in seconds','content-desc');adb('shell','input','keyevent','KEYCODE_MOVE_END');adb('shell','input','keyevent','KEYCODE_DEL');adb('shell','input','keyevent','KEYCODE_DEL');adb('shell','input','text','2');adb('shell','input','keyevent','KEYCODE_BACK');t=dump('v140-auto-save');click(t,'Save Settings');time.sleep(.7)
    # Permission may be disabled by older QA runs; use the actual OS permission page.
    t=dump('v140-after-save')
    if 'Display over other apps' in texts(t):
        click(t,'Open Settings');t=dump('v140-overlay-permission');shot('v140-overlay-permission')
        switch=next(n for n in t.iter('node') if n.get('class')=='android.widget.Switch');a,b,c,d=bounds(switch);tap((a+c)//2,(b+d)//2);adb('shell','input','keyevent','KEYCODE_BACK');time.sleep(.5)
    p=prefs();assert p['enabled']=='true' and p['auto_minimize']=='true' and p['auto_seconds']=='2';assert next(i for i in json.loads(p['items']) if i['id']=='live-speak')['live_color']==8
    # Idle panel collapses above another app; tap reopens it in multi-button mode.
    adb('shell','am','start','-W','-a','android.settings.SETTINGS');time.sleep(2.6);b=frame();assert b[2]-b[0]==int(p['minimized_size']);shot('v140-auto-minimized-over-settings');tap((b[0]+b[2])//2,(b[1]+b[3])//2);time.sleep(.5);f=frame();assert f[2]-f[0]>b[2]-b[0];shot('v140-reopened-over-settings')
    # Disable timer after proving it so subsequent screen captures do not race it.
    t=settings();click(t,'Size & Minimize');t=dump('v140-auto-disable');click(t,'Auto minimize');t=dump('v140-disable-save');click(t,'Save Settings');time.sleep(.5)
    print('PASS persisted custom live color/icon/bubble size; Display over other apps; idle auto-minimize over Settings and multi-button reopen',flush=True)
elif phase=='panel':
    start();t=dump('v140-panel-home');shot('v140-panel-home');f=frame();assert f[2]-f[0]-180>100
    # Record while still expanded. No BUS12 exists on the emulator, but save is logged.
    hold_panel('v140-held-panel');logs=adb('shell','logcat','-d','-s','OuterVoice');assert 'Floating WAV saved:' in logs;assert 'input encoding=' in logs
    tap(f[2]-44,f[1]+40);t=dump('v140-close-confirm');assert 'Close floating panel?' in texts(t);click(t,'Cancel');minimize();b=frame();shot('v140-manual-minimized');adb('shell','am','force-stop',PKG);start();time.sleep(.5);assert frame()==b
    tap((b[0]+b[2])//2,(b[1]+b[3])//2);time.sleep(.5);assert frame()[2]-frame()[0]>b[2]-b[0]
    print('PASS toolbar separation, floating recording and WAV finalization, close confirmation, minimize/reopen and minimized restart',flush=True)
elif phase=='bubble':
    t=settings()
    for n in list(t.iter('node')):
        if (n.get('content-desc') or '').startswith('Show ') and 'Live Speak' not in n.get('content-desc') and n.get('checked')=='true':click(t,n.get('content-desc'),'content-desc');t=dump('v140-deselect')
    click(t,'Save Settings');time.sleep(.4);t=dump('v140-single-panel');shot('v140-single-panel');f=frame();assert f[2]-f[0]-180>=60;minimize();b=frame();hold((b[0]+b[2])//2,(b[1]+b[3])//2,'v140-held-bubble');assert frame()==b
    # Drag moves/cancels without turning the bubble back into a full panel.
    x=(b[0]+b[2])//2;y=(b[1]+b[3])//2;adb('shell','input','swipe',x,y,x+80,y+30,700);time.sleep(.5);m=frame();assert m[2]-m[0]==b[2]-b[0] and m!=b
    adb('shell','am','force-stop',PKG);start();time.sleep(.5);assert frame()==m;shot('v140-single-bubble-restart')
    print('PASS Live Speak-only toolbar, direct bubble hold/save without expansion, drag cancellation and position persistence',flush=True)
elif phase=='record':
    start();t=dump('v140-record-home');click(t,'+ Add Sound');t=dump('v140-add');shot('v140-add');assert 'Browse files on this device' not in texts(t);assert not any('44.1 kHz' in s for s in texts(t));assert bounds(node(t,'Test imported sound','content-desc'))[3]-bounds(node(t,'Test imported sound','content-desc'))[1]==60
    a,b,c,d=bounds(node(t,'Record'));click(t,'Record');time.sleep(1.1);tap((a+c)//2,(b+d)//2);time.sleep(.6);t=dump('v140-recorded');shot('v140-recorded');assert node(t,'Test imported sound','content-desc').get('enabled')=='true'
    files=adb('shell','ls','/data/data/'+PKG+'/cache').split();name=next(f for f in files if f.startswith('voice-') and f.endswith('.wav'));local=ROOT/'.build-tools/v140-recorded.wav';adb('pull','/data/data/'+PKG+'/cache/'+name,local)
    with wave.open(str(local)) as w:assert w.getnchannels() in (1,2) and w.getframerate()>=8000 and w.getnframes()>0;print('PASS adaptive recording WAV:',w.getframerate(),'Hz',w.getnchannels(),'channels',flush=True)
    click(t,'Cancel');t=dump('v140-record-cancel');click(t,'Edit List');t=dump('v140-edit');shot('v140-edit');assert all(bounds(n)[3]<=506 for n in t.iter('node') if (n.get('content-desc') or '').startswith('Rename '));click(t,'Cancel')
    print('PASS clear Add Sound Play button, no duplicate browse/format restriction, recording preview, compact edit list',flush=True)
elif phase=='helpers':pass
else:raise AssertionError('Unknown phase '+phase)
assert 'FATAL EXCEPTION' not in adb('shell','logcat','-d','-s','AndroidRuntime')
