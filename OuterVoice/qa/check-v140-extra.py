"""Focused release regressions: timer safety, maximum sizes, six rows, audio import.
Uses only disposable emulator data and restores original saved sounds/picker state.
"""
from pathlib import Path
import runpy,sys,time,json,subprocess,xml.etree.ElementTree as ET,io,wave
phase=sys.argv[1];sys.argv=['helpers','helpers']
h=runpy.run_path(str(Path(__file__).with_name('check-v140.py')))
globals().update({k:v for k,v in h.items() if not k.startswith('__') and k!='phase'})
def sounds():
    t=ET.fromstring(adb('shell','cat','/data/data/'+PKG+'/shared_prefs/sounds.xml'))
    return json.loads(next(n.text for n in t if n.get('name')=='items'))
def enter(t,label,value,attr='content-desc'):
    click(t,label,attr);adb('shell','input','keyevent','KEYCODE_MOVE_END');adb('shell','input','keyevent',*(['KEYCODE_DEL']*65));adb('shell','input','text',value.replace(' ','%s'));adb('shell','input','keyevent','KEYCODE_BACK')

if phase=='timer':
    t=settings();click(t,'Size & Minimize');t=dump('v140-final-size');shot('v140-final-size')
    if node(t,'Auto minimize').get('checked')!='true':click(t,'Auto minimize')
    t=dump('v140-timer-on');enter(t,'Auto minimize time in seconds','5');t=dump('v140-timer-ready');p=prefs();click(t,'Save Settings');time.sleep(.15)
    f=frame();x=(f[0]+f[2])//2;y=f[1]+96+int(p['size'])*(1+int(p['mic_enlargement'])/100)/2
    process=subprocess.Popen([str(ADB),'-s','emulator-5554','shell','input','swipe',str(int(x)),str(int(y)),str(int(x)),str(int(y)),'9000'])
    time.sleep(5.7);assert frame()==f;shot('v140-timer-during-hold');process.wait(timeout=15);time.sleep(5.7);assert frame()[2]-frame()[0]==int(prefs()['minimized_size'])
    t=settings();click(t,'Size & Minimize');t=dump('v140-timer-off');click(t,'Auto minimize');t=dump('v140-max-ready');seek(t,'Floating button size',1);t=dump('v140-max-base');seek(t,'Live Speak button enlargement percent',1);t=dump('v140-max-live');seek(t,'Minimized button size',1);t=dump('v140-max-bubble');click(t,'Save Settings');time.sleep(.4);f=frame();assert f[3]-f[1]==446
    shot('v140-max-panel');minimize();assert frame()[2]-frame()[0]==144;shot('v140-max-minimized')
    t=settings();click(t,'Size & Minimize');t=dump('v140-restoring');seek(t,'Floating button size',.3);t=dump('v140-restore-base');seek(t,'Live Speak button enlargement percent',.5);t=dump('v140-restore-live');seek(t,'Minimized button size',.3);t=dump('v140-restore-bubble');click(t,'Save Settings')
    print('PASS 5-second timeout cannot interrupt a 9-second hold; minimizes after release; 288px live circle and 144px bubble; defaults restored',flush=True)
elif phase=='order':
    t=settings()
    for n in list(t.iter('node')):
        if (n.get('content-desc') or '').startswith('Show ') and n.get('checked')!='true':click(t,n.get('content-desc'),'content-desc');t=dump('v140-select-all')
    for _ in range(2):click(t,'Move up Live Speak','content-desc');t=dump('v140-live-first')
    click(t,'Size & Minimize');t=dump('v140-wide-size');seek(t,'Floating button size',1);t=dump('v140-wide-base');seek(t,'Live Speak button enlargement percent',1);t=dump('v140-wide-live');seek(t,'Floating button spacing',1);t=dump('v140-wide-gap');click(t,'Save Settings');time.sleep(.5)
    adb('shell','am','start','-W','-a','android.settings.SETTINGS');f=frame();assert f[2]-f[0]==1008 and f[3]-f[1]==446;shot('v140-wide-over-settings');assert json.loads(prefs()['items'])[0]['id']=='live-speak'
    adb('shell','input','swipe',f[2]-80,f[1]+260,f[0]+350,f[1]+260,500);time.sleep(.3);assert frame()==f;shot('v140-wide-scrolled');minimize();b=frame();tap((b[0]+b[2])//2,(b[1]+b[3])//2);time.sleep(.5);assert frame()==f
    t=settings();click(t,'Size & Minimize');t=dump('v140-wide-restore');seek(t,'Floating button size',.3);t=dump('v140-wide-base-default');seek(t,'Live Speak button enlargement percent',.5);t=dump('v140-wide-live-default');seek(t,'Floating button spacing',.3);t=dump('v140-wide-gap-default');click(t,'Save Settings')
    print('PASS reorder Live Speak; six-button maximum-width overlay over Android Settings, horizontal scroll, expanding toolbar and reopen',flush=True)
elif phase=='six':
    original=sounds();assert len(original)==5
    start();t=dump('v140-six-home');click(t,'+ Add Sound');t=dump('v140-six-add');enter(t,'Enter a sound name','QA sixth','text');t=dump('v140-six-named');a,b,c,d=bounds(node(t,'Record'));click(t,'Record');time.sleep(1);tap((a+c)//2,(b+d)//2);time.sleep(.6);t=dump('v140-six-ready');click(t,'Add Sound');t=dump('v140-home-six');shot('v140-home-six');assert len(sounds())==6
    rows=[n for n in t.iter('node') if (n.get('content-desc') or '').startswith('Play ')];assert len(rows)==6 and all(bounds(n)[3]<=576 for n in rows)
    click(t,'Edit List');t=dump('v140-edit-six');shot('v140-edit-six');rows=[n for n in t.iter('node') if (n.get('content-desc') or '').startswith('Rename ')];assert len(rows)==6 and all(bounds(n)[3]<=506 for n in rows)
    click(t,'Delete QA sixth','content-desc');t=dump('v140-six-delete');click(t,'Delete');t=dump('v140-six-staged');assert len(sounds())==6;click(t,'Save Changes');assert sounds()==original
    start();t=dump('v140-exit');click(t,'Exit app','content-desc');assert prefs()['enabled']=='false'
    for _ in range(10):
        if 'FloatingPanelService' not in adb('shell','dumpsys','activity','services',PKG):break
        time.sleep(.3)
    assert 'FloatingPanelService' not in adb('shell','dumpsys','activity','services',PKG)
    print('PASS six Home and Edit rows fit; confirmed staged deletion preserves originals; Exit disables overlay and stops service',flush=True)
elif phase=='exit':
    start();assert prefs()['enabled']=='true';assert frame()[2]>frame()[0];t=dump('v140-final-before-exit');click(t,'Exit app','content-desc');assert prefs()['enabled']=='false'
    for _ in range(10):
        if 'FloatingPanelService' not in adb('shell','dumpsys','activity','services',PKG):break
        time.sleep(.3)
    assert 'FloatingPanelService' not in adb('shell','dumpsys','activity','services',PKG)
    resumed=[s for s in adb('shell','dumpsys','activity','activities').splitlines() if ('mResumedActivity' in s or 'ResumedActivity:' in s) and PKG in s];assert not resumed
    print('PASS Exit from enabled overlay disables it, removes the service and closes the app task',flush=True)
elif phase=='motion':
    from PIL import Image
    old=adb('shell','settings','get','global','animator_duration_scale').strip()
    try:
        adb('shell','settings','put','global','animator_duration_scale','1');adb('shell','am','force-stop',PKG);t=settings()
        if node(t,'Enable floating panel').get('checked')!='true':click(t,'Enable floating panel');t=dump('v140-motion-enable')
        click(t,'Save Settings');time.sleep(.5);f=frame();d=round(int(prefs()['size'])*(1+int(prefs()['mic_enlargement'])/100));cx=(f[0]+f[2])//2;cy=f[1]+96+d//2
        def span(name):
            im=Image.open(QA/(name+'.png')).convert('RGB');pixels=[]
            for x in range(cx-d//2,cx+d//2+1):
                for y in range(cy-d//2,cy+d//2+1):
                    r,g,b=im.getpixel((x,y))
                    if r>165 and r-g>35 and b-g>20:pixels.append(x);break
            assert pixels;return max(pixels)-min(pixels)+1
        shot('v140-motion-rest');rest=span('v140-motion-rest');process=subprocess.Popen([str(ADB),'-s','emulator-5554','shell','input','swipe',str(cx),str(cy),str(cx),str(cy),'16000']);widths=[]
        for i in range(10):
            time.sleep(.13+i*.019);name='v140-motion-hold-'+str(i);shot(name);widths.append(span(name))
        process.wait(timeout=15);time.sleep(.4);shot('v140-motion-released');assert min(widths)<=rest*.93,(rest,widths);assert abs(span('v140-motion-released')-rest)<=2
        print('PASS animated hold pulse widths:',rest,widths,'and full-size return',flush=True)
    finally:adb('shell','settings','put','global','animator_duration_scale',old)
elif phase=='import':
    original=sounds();disabled=adb('shell','pm','list','packages','-d');changed=[]
    try:
        for package in ['com.android.documentsui','com.android.music']:
            if package not in disabled:
                adb('shell','pm','disable-user','--user','0',package);changed.append(package)
        for name in ['tone24.wav','tone.mp3','different.bin','broken.wav']:
            start();t=dump('v140-import-home');click(t,'+ Add Sound');t=dump('v140-import-add');click(t,'Import Sound');t=dump('v140-browser');label=next(s for s in texts(t) if s.startswith('Internal storage:'));click(t,label);t=dump('v140-storage');click(t,'Folder: Download');t=dump('v140-download');click(t,'Folder: OuterVoice-QA');t=dump('v140-fixtures')
            for _ in range(5):
                if 'Sound: '+name in texts(t):break
                adb('shell','input','swipe',600,450,600,200,300);t=dump('v140-fixtures-scroll')
            click(t,'Sound: '+name);time.sleep(.8);t=dump('v140-import-'+name.replace('.','-'));play=node(t,'Test imported sound','content-desc')
            if name=='broken.wav':assert play.get('enabled')=='false' and any('decode' in s.lower() for s in texts(t))
            else:
                assert play.get('enabled')=='true' and name in texts(t)
                files=adb('shell','ls','/data/data/'+PKG+'/files').split();pending=[n for n in files if n.startswith('pending-')];assert len(pending)==1
                raw=subprocess.check_output([str(ADB),'-s','emulator-5554','exec-out','cat','/data/data/'+PKG+'/files/'+pending[0]])
                with wave.open(io.BytesIO(raw)) as w:assert w.getframerate()==44100 and w.getnchannels()==1 and w.getsampwidth()==2 and abs(w.getnframes()/44100-2)<.15
                click(t,'Test imported sound','content-desc');time.sleep(.3);assert 'Outer speaker is unavailable (BUS12)' in adb('shell','logcat','-d')
                if name=='tone.mp3':shot('v140-import-mp3')
            click(t,'Cancel');assert not any(n.startswith('pending-') for n in adb('shell','ls','/data/data/'+PKG+'/files').split())
            print('PASS Import Sound '+name+' and preview/cleanup',flush=True)
        assert sounds()==original
    finally:
        for package in changed:adb('shell','pm','enable',package)
else:raise AssertionError('Unknown phase '+phase)
assert 'FATAL EXCEPTION' not in adb('shell','logcat','-d','-s','AndroidRuntime')
