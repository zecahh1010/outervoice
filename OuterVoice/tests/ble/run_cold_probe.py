"""Verify that a real BLE PendingIntent revives the process without opening an Activity."""
from pathlib import Path
import subprocess,time,urllib.request

root=Path(__file__).resolve().parents[3];adb=root/'.build-tools/platform-tools/adb.exe'
def run(*args):return subprocess.run([str(adb),'-s','emulator-5556',*args],capture_output=True,text=True,timeout=20)
def control(path):
    with urllib.request.urlopen('http://127.0.0.1:8878'+path,timeout=8) as response:return response.read()
report=[]
def check(good,text):
    if not good:raise AssertionError(text)
    report.append('PASS '+text);print(report[-1],flush=True)

control('/stop');run('logcat','-c')
setup=run('shell','am','instrument','-w','com.zecadev.outervoice.probe/.ColdProbe')
check('result=PASS' in setup.stdout,'cold test has an active Android PendingIntent scan and saved virtual button')
try:
    # Instrumentation completion may terminate its target process. Start the real app
    # once, leave it in the background, then kill that normal (non-instrumented) process.
    run('shell','am','start','-n','com.zecadev.outervoice/.MainActivity')
    time.sleep(2)
    run('shell','input','keyevent','KEYCODE_HOME')
    pid=run('shell','pidof','com.zecadev.outervoice').stdout.strip();assert pid.isdigit(),pid
    killed=run('shell','su','0','kill','-9',pid)
    check(killed.returncode==0,'killed only the test app process in emulator-5556')
    time.sleep(1)
    check(not run('shell','pidof','com.zecadev.outervoice').stdout.strip(),'app remains absent before virtual button transmission')
    control('/start?gesture=0')
    deadline=time.monotonic()+12;new='';services=''
    while time.monotonic()<deadline:
        new=run('shell','pidof','com.zecadev.outervoice').stdout.strip()
        services=run('shell','dumpsys','activity','services','com.zecadev.outervoice').stdout
        if new and new!=pid and 'SmartButtonsService' in services:break
        time.sleep(.25)
    check(new.isdigit() and new!=pid,'real BLE system broadcast revives a new app process without launching UI')
    check('SmartButtonsService' in services,'bound single press starts foreground action service after cold revival')
    log=run('logcat','-d','-s','ActivityManager:I','ActivityTaskManager:I').stdout
    check('for broadcast' in log and 'ButtonScanReceiver' in log,'Android attributes process startup to the manifest scan receiver')
finally:
    control('/stop')
    restored=run('shell','am','instrument','-w','-e','restore','true','com.zecadev.outervoice.probe/.ColdProbe')
    check('result=PASS' in restored.stdout,'original emulator button preferences restored')
out=root/'OuterVoice/qa/v163-native/cold-radio.txt'
out.write_text('\n'.join(report)+'\nBOUNDARY: isolated Android emulator and virtual radio; not Zeekr/VLite firmware.\n',encoding='utf-8')
