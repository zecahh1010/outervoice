"""Focused Android 9 regressions: anchored reopen and native/OEM picker import."""
from pathlib import Path
import runpy,sys,json,time,xml.etree.ElementTree as ET
phase=sys.argv[1];sys.argv=['helpers','helpers']
h=runpy.run_path(str(Path(__file__).with_name('check-v140.py')))
globals().update({k:v for k,v in h.items() if not k.startswith('__') and k not in ['phase','dump','settings']})
def dump(name):return h['dump'](name)
def resumed():return adb('shell','dumpsys','activity','activities')
def fixture(order=0,large=False,enabled=True):
    adb('shell','am','force-stop',PKG)
    t=ET.fromstring(original);items=json.loads(next(n.text for n in t if n.get('name')=='items'))
    live=next(i for i in items if i['id']=='live-speak');items.remove(live);items.insert(order,live)
    for i in items:i['selected']=True
    values={'enabled':'true' if enabled else 'false','minimized':'false','auto_minimize':'false','size':'200' if large else '88','spacing':'40' if large else '12','mic_enlargement':'100' if large else '50','minimized_size':'200' if large else '88','x':'8' if large else '100','y':'20' if large else '80','strip_scroll':'0'}
    for key,value in values.items():
        n=next((n for n in t if n.get('name')==key),None)
        if n is None:n=ET.SubElement(t,'boolean' if value in ['true','false'] else 'int',{'name':key})
        n.set('value',value)
    next(n for n in t if n.get('name')=='items').text=json.dumps(items)
    local=ROOT/'.build-tools/v141-floating.xml';local.write_bytes(ET.tostring(t));adb('push',local,'/sdcard/v141-floating.xml');adb('shell','cp','/sdcard/v141-floating.xml',prefpath);start();time.sleep(.5)

def live_center(scroll=0):
    f=frame();p=prefs();items=[i for i in json.loads(p['items']) if i['selected']];n=next(j for j,i in enumerate(items) if i['id']=='live-speak');base=int(p['size']);diam=round(base*(1+int(p['mic_enlargement'])/100))
    return f[0]+14+n*(base+int(p['spacing']))+diam/2-scroll,f[1]+96+diam/2
def bubble_center():f=frame();return (f[0]+f[2])/2,(f[1]+f[3])/2
def reopen():c=bubble_center();tap(*c);time.sleep(.65)

prefpath='/data/data/'+PKG+'/shared_prefs/floating.xml'
original=adb('shell','cat',prefpath);sounds_before=adb('shell','cat','/data/data/'+PKG+'/shared_prefs/sounds.xml');old_scale=adb('shell','settings','get','global','animator_duration_scale').strip()
try:
    if phase=='anchor':
        adb('shell','settings','put','global','animator_duration_scale','1')
        fixture(order=2);f=frame();c=live_center();shot('v141-reordered-expanded');minimize();time.sleep(.25);assert bubble_center()==c;reopen();assert live_center()==c and frame()==f
        minimize();time.sleep(.25);c=bubble_center();adb('shell','input','swipe',int(c[0]),int(c[1]),int(c[0]+70),int(c[1]+40),500);time.sleep(.3);c=bubble_center();reopen();assert live_center()==c,(c,live_center());shot('v141-reopened-after-drag')
        # Persist the moved expanded frame across service/process restart.
        moved=frame();adb('shell','am','force-stop',PKG);start();time.sleep(.5);assert frame()==moved and live_center()==c
        minimize();time.sleep(.25);c=bubble_center();adb('shell','input','swipe',int(c[0]),int(c[1]),1000,580,600);time.sleep(.2);reopen();f=frame();assert 0<=f[0]<f[2]<=1024 and 0<=f[1]<f[3]<=600;shot('v141-reopened-screen-edge')
        print('PASS reordered Live Speak min/reopen center, moved bubble anchor, expanded restart and screen-edge clamping',flush=True)
        fixture(order=2,large=True);f=frame()
        # Drag on the label row: starting inside the enlarged microphone records
        # deliberately and therefore prevents horizontal scrolling.
        drag_y=f[1]+96+400+15
        adb('shell','input','swipe',850,drag_y,500,drag_y,600);time.sleep(.3);minimize();time.sleep(.25);scroll=int(prefs()['strip_scroll']);assert scroll>0
        c=bubble_center();bf=frame();assert bf[2]-bf[0]==200 and bf[3]-bf[1]==200;reopen();assert frame()==f and live_center(scroll)==c,(scroll,c,live_center(scroll));shot('v141-reopened-scrolled')
        print('PASS wide horizontal strip preserves its scroll and Live Speak anchor when reopening',flush=True)
    elif phase=='sizes':
        fixture(large=True,enabled=False)
        adb('shell','am','start','-W','-n',PKG+'/.FloatingButtonsActivity');t=dump('v141-sizes-start');click(t,'Size & Minimize');t=dump('v141-sizes-max')
        assert texts(t).count('200px')>=2,texts(t)
        shot('v141-sizes-200')
        for label in ['Floating button size','Minimized button size']:seek(t,label,0)
        t=dump('v141-sizes-min');assert texts(t).count('64px')>=2
        click(t,'Save Settings');assert prefs()['size']=='64' and prefs()['minimized_size']=='64'
        adb('shell','am','start','-W','-n',PKG+'/.FloatingButtonsActivity');t=dump('v141-sizes-reload-min');click(t,'Size & Minimize');t=dump('v141-sizes-reloaded-min');assert texts(t).count('64px')>=2
        for label in ['Floating button size','Minimized button size']:seek(t,label,1)
        t=dump('v141-sizes-max-save');assert texts(t).count('200px')>=2
        click(t,'Save Settings');assert prefs()['size']=='200' and prefs()['minimized_size']=='200'
        adb('shell','am','force-stop',PKG);start();adb('shell','am','start','-W','-n',PKG+'/.FloatingButtonsActivity');t=dump('v141-sizes-restart');click(t,'Size & Minimize');t=dump('v141-sizes-reloaded-max');assert texts(t).count('200px')>=2
        print('PASS both sliders select and persist 64px and 200px across restart, with fitted previews',flush=True)
    elif phase=='picker':
        probe='com.zecadev.outervoice.pickerqa';docs='com.android.documentsui';disabled=adb('shell','pm','list','packages','-d');changed=[]
        adb('install','-r',ROOT/'.build-tools/picker-probe/signed.apk');adb('shell','pm','disable',probe+'/.ProbeActivity')
        try:
            adb('shell','pm','enable',docs);start();t=dump('v141-picker-home');click(t,'+ Add Sound');t=dump('v141-picker-add');click(t,'Import Sound');t=dump('v141-android-picker');shot('v141-android-picker')
            assert 'mResumedActivity:' in resumed() and docs in next(s for s in resumed().splitlines() if 'mResumedActivity:' in s)
            assert 'Android file picker unavailable' not in texts(t) and 'Choose storage' not in texts(t)
            adb('shell','input','keyevent','KEYCODE_BACK');t=dump('v141-picker-cancel');assert 'Add Sound' in texts(t)
            print('PASS Import Sound opens Android DocumentsUI; cancel returns to Add Sound',flush=True)
            adb('shell','pm','disable-user','--user','0',docs);adb('shell','pm','enable',probe+'/.ProbeActivity')
            click(t,'Import Sound');t=dump('v141-oem-chooser')
            if 'choose qa sound' not in [s.lower() for s in texts(t)]:click(t,'QA File Picker');t=dump('v141-oem-picker')
            assert 'QA Images Only' not in texts(t);shot('v141-oem-picker');click(t,'Choose QA sound');time.sleep(.7);t=dump('v141-oem-imported');assert 'picker-qa.wav' in texts(t) and node(t,'Test imported sound','content-desc').get('enabled')=='true';shot('v141-oem-imported')
            click(t,'Cancel');t=dump('v141-no-picker-home');click(t,'+ Add Sound');t=dump('v141-no-picker-add');adb('shell','pm','disable',probe+'/.ProbeActivity');click(t,'Import Sound');t=dump('v141-no-picker');shot('v141-no-picker')
            assert 'Android file picker unavailable' in texts(t) and 'Choose storage' not in texts(t)
            assert 'GALLERY_OPENED' not in adb('shell','logcat','-d','-s','PickerProbe')
            click(t,'Browse files');t=dump('v141-explicit-browser');assert 'Choose storage' in texts(t) or 'Allow Outer Voice' in ' '.join(texts(t))
            print('PASS legacy arbitrary-file picker URI import, images-only gallery exclusion, explicit missing-picker fallback',flush=True)
            adb('shell','input','keyevent','KEYCODE_BACK');t=dump('v141-browser-cancel');click(t,'Cancel')
        finally:
            adb('uninstall',probe)
            if docs in disabled:adb('shell','pm','disable-user','--user','0',docs)
            else:adb('shell','pm','enable',docs)
    else:raise AssertionError(phase)
    assert adb('shell','cat','/data/data/'+PKG+'/shared_prefs/sounds.xml')==sounds_before
    assert 'FATAL EXCEPTION' not in adb('shell','logcat','-d','-s','AndroidRuntime')
finally:
    adb('shell','am','force-stop',PKG);local=ROOT/'.build-tools/v141-original-floating.xml';local.write_text(original,encoding='utf-8');adb('push',local,'/sdcard/v141-original-floating.xml');adb('shell','cp','/sdcard/v141-original-floating.xml',prefpath)
    adb('shell','settings','put','global','animator_duration_scale',old_scale)
