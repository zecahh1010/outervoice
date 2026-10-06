"""Safer saved-sound editing regression on emulator-5554, with a disposable fixture."""
from pathlib import Path
import runpy,sys,json,time,xml.etree.ElementTree as ET,subprocess,re,hashlib
sys.argv=['helpers','helpers']
h=runpy.run_path(str(Path(__file__).with_name('check-v120.py')))
adb,dump,texts,click,tap,point,shot,private=[h[k] for k in ['adb','dump','texts','click','tap','point','shot','private']]
ROOT,PKG,ADB,QA=[h[k] for k in ['ROOT','PKG','ADB','QA']]
def items():
    xml=ET.fromstring(adb('shell','cat',private('shared_prefs/sounds.xml')))
    return json.loads(next(n.text for n in xml if n.get('name')=='items'))
def floating(): return ET.fromstring(adb('shell','cat',private('shared_prefs/floating.xml')))
def enabled(): return next(n for n in floating() if n.get('name')=='enabled').get('value')=='true'
def open_main():
    adb('shell','am','start','-W','-n',PKG+'/.MainActivity');time.sleep(.4)
def rename(tree,name,new):
    click(tree,'Rename '+name,'content-desc');t=dump('v121-rename-dialog')
    edit=next(n for n in t.iter('node') if n.get('class')=='android.widget.EditText')
    a,b,c,d=map(int,re.findall(r'\d+',edit.get('bounds')))
    tap((a+c)//2,(b+d)//2)
    # A tap can collapse the initial select-all range, so clear reliably first.
    adb('shell','input','keyevent','123')
    adb('shell','input','keyevent',*(['67']*65))
    adb('shell','input','text',new.replace(' ','%s'));adb('shell','input','keyevent','4');time.sleep(.3)
    t=dump('v121-rename-entered');click(t,'Rename');return dump('v121-renamed-draft')
adb('root');adb('wait-for-device')
adb('install','-r',str(ROOT/'OuterVoice/dist/OuterVoice-1.2.1.apk'))
adb('shell','pm','grant',PKG,'android.permission.RECORD_AUDIO');adb('shell','logcat','-c')
adb('shell','am','force-stop',PKG);open_main()
t=dump('v121-home')
if enabled():
    click(t,'Floating Buttons settings','content-desc');t=dump('v121-disable-overlay')
    click(t,'Enable floating panel');click(t,'Save Settings');t=dump('v121-home')
assert not any((n.get('content-desc') or '').startswith('Remove ') for n in t.iter('node'))
assert not any(n.get('text')=='\ue872' for n in t.iter('node'))
assert 'Edit List' in texts(t);shot('v121-home')
while any(v['name']=='Edit test' for v in items()):
    # Remove only the disposable fixture from an interrupted earlier test run.
    click(t,'Edit List');t=dump('v121-cleanup')
    adb('shell','input','swipe','600','440','600','170','300');time.sleep(.3);t=dump('v121-cleanup-bottom')
    click(t,'Delete Edit test','content-desc');t=dump('v121-cleanup-confirm');click(t,'Delete');t=dump('v121-cleanup-staged');click(t,'Save Changes');t=dump('v121-cleanup-saved')
original=items(); original_ids=[v['id'] for v in original]
print('PASS no home trash controls; upgrade retains saved sounds',flush=True)
# Create a disposable WAV through the existing Add Sound flow.
click(t,'+ Add Sound');t=dump('v121-add')
edit=next(n for n in t.iter('node') if n.get('class')=='android.widget.EditText')
a,b,c,d=map(int,re.findall(r'\d+',edit.get('bounds')));tap((a+c)//2,(b+d)//2)
adb('shell','input','text','Edit%stest');adb('shell','input','keyevent','4');time.sleep(.3)
t=dump('v121-add-named');rx,ry=point(t,'Record microphone');tap(rx,ry);time.sleep(1);tap(rx,ry);time.sleep(.5)
t=dump('v121-add-ready');click(t,'Add Sound');t=dump('v121-after-add')
fixture=next(v for v in items() if v['id'] not in original_ids);sid=fixture['id'];assert fixture['name']=='Edit test'
raw=subprocess.check_output([str(ADB),'-s','emulator-5554','exec-out','cat',private('files/'+sid+'.wav')]);digest=hashlib.sha256(raw).hexdigest()
click(t,'Edit List');t=dump('v121-editor');shot('v121-editor')
# Scroll to the disposable last row.
adb('shell','input','swipe','600','440','600','170','300');time.sleep(.3);t=dump('v121-editor-bottom')
t=rename(t,'Edit test','Cancelled rename')
assert next(v for v in items() if v['id']==sid)['name']=='Edit test'
click(t,'Move up Cancelled rename','content-desc');t=dump('v121-reordered-draft')
click(t,'Delete Cancelled rename','content-desc');t=dump('v121-delete-confirm');assert 'Delete saved sound?' in texts(t)
click(t,'Delete');t=dump('v121-deleted-draft')
assert any(v['id']==sid for v in items())
assert adb('shell','test','-f',private('files/'+sid+'.wav'))==''
click(t,'Cancel');t=dump('v121-cancelled')
assert [v['id'] for v in items()]==original_ids+[sid]
assert next(v for v in items() if v['id']==sid)['name']=='Edit test'
print('PASS rename/reorder/delete staged; Cancel retains names, order and WAV',flush=True)
click(t,'Edit List');t=dump('v121-edit-again')
adb('shell','input','swipe','600','440','600','170','300');time.sleep(.3);t=dump('v121-edit-bottom')
t=rename(t,'Edit test','Renamed test');click(t,'Move up Renamed test','content-desc');t=dump('v121-save-ready');click(t,'Save Changes');t=dump('v121-saved')
now=items();assert next(v for v in now if v['id']==sid)['name']=='Renamed test';assert [v['id'] for v in now][-2]==sid
raw2=subprocess.check_output([str(ADB),'-s','emulator-5554','exec-out','cat',private('files/'+sid+'.wav')]);assert hashlib.sha256(raw2).hexdigest()==digest
adb('shell','am','force-stop',PKG);open_main();t=dump('v121-restarted');assert items()==now
print('PASS saved rename/order survive restart; sound UUID and audio bytes unchanged',flush=True)
# Configure the fixture in the panel, then verify home-list rename reflects there.
click(t,'Floating Buttons settings','content-desc');t=dump('v121-float-config')
adb('shell','input','swipe','350','360','350','180','300');time.sleep(.3);t=dump('v121-float-scrolled')
click(t,'Show Renamed test in floating panel','content-desc');t=dump('v121-float-selected')
click(t,'Color Light Red','content-desc');t=dump('v121-float-red');click(t,'Icon Warmly Remind','content-desc');t=dump('v121-float-icon')
click(t,'Enable floating panel');click(t,'Save Settings');t=dump('v121-before-edit-selected')
click(t,'Edit List');t=dump('v121-edit-selected')
adb('shell','input','swipe','600','440','600','170','300');time.sleep(.3);t=dump('v121-edit-selected-bottom')
t=rename(t,'Renamed test','Panel renamed');click(t,'Save Changes');time.sleep(.5)
config=json.loads(next(n.text for n in floating() if n.get('name')=='items'))
f=next(v for v in config if v['id']==sid);assert f['selected'] and f['color']==4 and f['icon']==2
adb('shell','am','start','-W','-a','android.settings.SETTINGS');time.sleep(.4);shot('v121-renamed-overlay')
open_main();t=dump('v121-before-delete');click(t,'Edit List');t=dump('v121-final-edit')
adb('shell','input','swipe','600','440','600','170','300');time.sleep(.3);t=dump('v121-final-edit-bottom')
click(t,'Delete Panel renamed','content-desc');t=dump('v121-delete-cancel-dialog');click(t,'Cancel');t=dump('v121-delete-cancelled')
assert any(v['id']==sid for v in items())
click(t,'Delete Panel renamed','content-desc');t=dump('v121-final-delete-dialog');click(t,'Delete');t=dump('v121-final-delete-draft')
assert any(v['id']==sid for v in items());click(t,'Save Changes');time.sleep(.5)
assert items()==original
assert adb('shell','if [ -f '+private('files/'+sid+'.wav')+' ]; then echo exists; else echo removed; fi').strip()=='removed'
adb('shell','am','start','-W','-a','android.settings.SETTINGS');time.sleep(.4);shot('v121-after-delete-overlay')
state=adb('shell','dumpsys','window','windows');assert 'ty=APPLICATION_OVERLAY' in state
log=adb('shell','logcat','-d','-s','AndroidRuntime');assert 'FATAL EXCEPTION' not in log
print('PASS rename keeps floating selection/color/icon; deletion removes WAV and updates overlay; original fixtures retained',flush=True)
