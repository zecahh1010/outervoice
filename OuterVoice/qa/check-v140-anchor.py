"""Verify each minimize anchors to Live Speak's visible center on emulator-5554."""
from pathlib import Path
import runpy,sys,json,time
sys.argv=['helpers','helpers']
globals().update({k:v for k,v in runpy.run_path(str(Path(__file__).with_name('check-v140.py'))).items() if not k.startswith('__')})

def center():
    f=frame();p=prefs();items=[i for i in json.loads(p['items']) if i['selected']]
    base=int(p['size']);gap=int(p['spacing']);large=round(base*(1+int(p['mic_enlargement'])/100))
    index=next(n for n,i in enumerate(items) if i['id']=='live-speak')
    x=(f[0]+f[2])/2 if len(items)==1 else f[0]+14+index*(base+gap)+large/2
    return x,f[1]+96+large/2

def expected(c):
    d=int(prefs()['minimized_size']);x=round(max(0,min(1024-d,c[0]-d/2)));y=round(max(0,min(600-d,c[1]-d/2)))
    return x,y,x+d,y+d

def check(name,automatic=False):
    c=center();f=frame();want=expected(c);shot(name+'-expanded')
    if automatic:time.sleep(5.8)
    else:minimize();time.sleep(.35)
    got=frame();assert got==want,(name,f,c,want,got);shot(name+'-minimized')
    print('PASS',name,'Live Speak center',c,'-> bubble',got,flush=True)
    return got

def reopen(b):tap((b[0]+b[2])//2,(b[1]+b[3])//2);time.sleep(.7)

old=adb('shell','settings','get','global','animator_duration_scale').strip()
try:
    adb('shell','settings','put','global','animator_duration_scale','1')
    t=settings()
    if node(t,'Enable floating panel').get('checked')!='true':click(t,'Enable floating panel');t=dump('v140-anchor-enabled')
    for n in list(t.iter('node')):
        if (n.get('content-desc') or '').startswith('Show ') and n.get('checked')!='true':click(t,n.get('content-desc'),'content-desc');t=dump('v140-anchor-all')
    click(t,'Save Settings');time.sleep(.6)
    b=check('v140-anchor-manual');reopen(b)
    # A dragged old bubble must not override the next collapse's Live Speak position.
    minimize();time.sleep(.35);b=frame();cx=(b[0]+b[2])//2;cy=(b[1]+b[3])//2
    adb('shell','input','swipe',cx,cy,cx+90,cy+35,500);time.sleep(.2);moved=frame();assert moved!=b;reopen(moved)
    f=frame();adb('shell','input','swipe',f[0]+44,f[1]+40,f[0]+124,f[1]+85,500);time.sleep(.3);assert frame()!=f
    b=check('v140-anchor-moved-panel');reopen(b)
    t=settings()
    for _ in range(2):click(t,'Move down Live Speak','content-desc');t=dump('v140-anchor-reorder')
    click(t,'Save Settings');time.sleep(.6);b=check('v140-anchor-reordered');reopen(b)
    t=settings()
    for n in list(t.iter('node')):
        if (n.get('content-desc') or '').startswith('Show ') and 'Live Speak' not in n.get('content-desc') and n.get('checked')=='true':click(t,n.get('content-desc'),'content-desc');t=dump('v140-anchor-single')
    click(t,'Size & Minimize');t=dump('v140-anchor-auto')
    if node(t,'Auto minimize').get('checked')!='true':click(t,'Auto minimize');t=dump('v140-anchor-auto-on')
    click(t,'Auto minimize time in seconds','content-desc');adb('shell','input','keyevent','KEYCODE_MOVE_END');adb('shell','input','keyevent',*(['KEYCODE_DEL']*5));adb('shell','input','text','5');adb('shell','input','keyevent','KEYCODE_BACK');t=dump('v140-anchor-auto-ready')
    click(t,'Save Settings');time.sleep(.5);b=check('v140-anchor-single-auto',automatic=True)
    # Direct speaking stays minimized; persisted position survives a process restart.
    hold((b[0]+b[2])//2,(b[1]+b[3])//2,'v140-anchor-direct-hold');assert frame()==b
    adb('shell','am','force-stop',PKG);start();time.sleep(.6);assert frame()==b
    t=settings();click(t,'Size & Minimize');t=dump('v140-anchor-restore-auto');click(t,'Auto minimize');t=dump('v140-anchor-restore-tabs');click(t,'Buttons & Order');t=dump('v140-anchor-restore-order')
    for n in list(t.iter('node')):
        if (n.get('content-desc') or '').startswith('Show ') and n.get('checked')!='true':click(t,n.get('content-desc'),'content-desc');t=dump('v140-anchor-restore-all')
    for _ in range(2):click(t,'Move up Live Speak','content-desc');t=dump('v140-anchor-restore-live')
    click(t,'Save Settings');time.sleep(.6)
    assert 'FATAL EXCEPTION' not in adb('shell','logcat','-d','-s','AndroidRuntime')
    print('PASS direct single-bubble speaking and anchored restart; six-button baseline restored',flush=True)
finally:adb('shell','settings','put','global','animator_duration_scale',old)
