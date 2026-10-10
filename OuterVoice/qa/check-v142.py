"""Android 9 checks for direct built-in sound browsing; disposable emulator only."""
from pathlib import Path
import runpy,sys,time,json,io,wave,re,subprocess,xml.etree.ElementTree as ET
sys.argv=['helpers','helpers']
h=runpy.run_path(str(Path(__file__).with_name('check-v140.py')))
globals().update({k:v for k,v in h.items() if not k.startswith('__') and k!='phase'})
prefpath='/data/data/'+PKG+'/shared_prefs/floating.xml'
original=adb('shell','cat',prefpath)
sounds_before=adb('shell','cat','/data/data/'+PKG+'/shared_prefs/sounds.xml')
permission='android.permission.READ_EXTERNAL_STORAGE'
granted=permission+': granted=true' in adb('shell','dumpsys','package',PKG)
docs='com.android.documentsui'
docs_disabled=docs in adb('shell','pm','list','packages','-d')
fixture_dir='/sdcard/Download/OuterVoice-v142-QA'

def inside_app(tree):
    raw=adb('shell','dumpsys','activity','activities')
    assert PKG in next(line for line in raw.splitlines() if 'mResumedActivity:' in line)
    assert 'Android file picker unavailable' not in texts(tree)

def add_screen(label):
    start();t=dump('v142-'+label+'-home');click(t,'+ Add Sound');return dump('v142-'+label+'-add')

def fixtures(t):
    assert 'Choose storage' in texts(t);inside_app(t)
    click(t,next(s for s in texts(t) if s.startswith('Internal storage:')))
    t=dump('v142-storage');click(t,'Folder: Download');t=dump('v142-download')
    click(t,'Folder: OuterVoice-v142-QA');return dump('v142-fixtures')

try:
    adb('shell','am','force-stop',PKG)
    t=ET.fromstring(original)
    for key in ['enabled','auto_minimize']:
        n=next((n for n in t if n.get('name')==key),None)
        if n is None:n=ET.SubElement(t,'boolean',{'name':key})
        n.set('value','false')
    local=ROOT/'.build-tools/v142-floating.xml';local.write_bytes(ET.tostring(t))
    adb('push',local,'/sdcard/v142-floating.xml');adb('shell','cp','/sdcard/v142-floating.xml',prefpath)
    adb('shell','logcat','-c');adb('shell','pm','enable',docs)
    adb('shell','pm','revoke',PKG,permission)
    t=add_screen('permission');click(t,'Import Sound');t=dump('v142-permission-request')
    click(t,'Deny');t=dump('v142-permission-denied');assert 'Add Sound' in texts(t) and 'Choose storage' not in texts(t)
    click(t,'Import Sound');t=dump('v142-permission-retry');click(t,'Allow')
    t=dump('v142-built-in-browser');assert 'Choose storage' in texts(t);inside_app(t);shot('v142-built-in-browser')
    click(t,'Cancel');t=dump('v142-browser-cancel');assert 'Add Sound' in texts(t)
    print('PASS storage permission deny/retry/grant and direct app browser with Android DocumentsUI enabled',flush=True)
    adb('shell','mkdir','-p',fixture_dir)
    for name in ['tone24.wav','tone.mp3','different.bin','broken.wav']:
        adb('shell','cp','/sdcard/Download/OuterVoice-QA/'+name,fixture_dir+'/'+name)
    for name in ['tone24.wav','tone.mp3','different.bin','broken.wav']:
        click(t,'Import Sound');t=dump('v142-choose-storage');t=fixtures(t)
        assert all('Sound: '+n in texts(t) for n in ['tone24.wav','tone.mp3','different.bin','broken.wav'])
        if name=='tone24.wav':
            click(t,'Up');t=dump('v142-up');assert '/storage/emulated/0/Download' in texts(t)
            click(t,'Folder: OuterVoice-v142-QA');t=dump('v142-return-to-fixtures')
        click(t,'Sound: '+name);time.sleep(.8);t=dump('v142-import-'+name.replace('.','-'))
        play=node(t,'Test imported sound','content-desc')
        if name=='broken.wav':
            assert play.get('enabled')=='false' and any('decode' in s.lower() for s in texts(t))
        else:
            assert play.get('enabled')=='true' and name in texts(t)
            pending=[n for n in adb('shell','ls','/data/data/'+PKG+'/files').split() if n.startswith('pending-')]
            assert len(pending)==1
            raw=subprocess.check_output([str(ADB),'-s','emulator-5554','exec-out','cat','/data/data/'+PKG+'/files/'+pending[0]])
            with wave.open(io.BytesIO(raw)) as w:
                assert w.getframerate()==44100 and w.getnchannels()==1 and w.getsampwidth()==2 and abs(w.getnframes()/44100-2)<.15
            click(t,'Test imported sound','content-desc');time.sleep(.3)
            assert 'Outer speaker is unavailable (BUS12)' in adb('shell','logcat','-d')
            if name=='tone.mp3':shot('v142-import-mp3')
        click(t,'Cancel');t=dump('v142-after-cancel-home')
        assert not any(n.startswith('pending-') for n in adb('shell','ls','/data/data/'+PKG+'/files').split())
        t=add_screen('next-import')
        print('PASS built-in browser '+name+' content import, preview request and cancel cleanup',flush=True)
    adb('shell','pm','disable-user','--user','0',docs)
    click(t,'Import Sound');t=dump('v142-no-system-picker');assert 'Choose storage' in texts(t);inside_app(t)
    click(t,'Cancel');t=dump('v142-no-system-cancel');click(t,'Cancel')
    assert adb('shell','cat','/data/data/'+PKG+'/shared_prefs/sounds.xml')==sounds_before
    assert 'FATAL EXCEPTION' not in adb('shell','logcat','-d','-s','AndroidRuntime')
    print('PASS browsing without DocumentsUI, saved-sound preservation and no fatal crash',flush=True)
finally:
    adb('shell','am','force-stop',PKG)
    local=ROOT/'.build-tools/v142-original-floating.xml';local.write_text(original,encoding='utf-8')
    adb('push',local,'/sdcard/v142-original-floating.xml');adb('shell','cp','/sdcard/v142-original-floating.xml',prefpath)
    adb('shell','pm','grant' if granted else 'revoke',PKG,permission)
    adb('shell','pm','disable-user' if docs_disabled else 'enable',*(['--user','0'] if docs_disabled else []),docs)
    adb('shell','rm','-f',fixture_dir+'/tone24.wav',fixture_dir+'/tone.mp3',fixture_dir+'/different.bin',fixture_dir+'/broken.wav')
    try:adb('shell','rmdir',fixture_dir)
    except subprocess.CalledProcessError:pass
