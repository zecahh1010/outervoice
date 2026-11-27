package com.zecadev.outervoice;

import java.util.Arrays;
import java.util.ArrayDeque;

/** Discovery policy regressions: explicit pairing, permission split and registration budget. */
public final class PairingDiscoveryTest {
    static void check(boolean good,String text){if(!good)throw new AssertionError(text);}
    public static void main(String[] args){
        check(Arrays.equals(SmartButtonPermissions.required(30),new String[]{"android.permission.ACCESS_FINE_LOCATION"}),"older Android uses location permission");
        for(int api:new int[]{31,33,36}){
            check(Arrays.equals(SmartButtonPermissions.required(api),new String[]{"android.permission.ACCESS_FINE_LOCATION","android.permission.BLUETOOTH_SCAN","android.permission.BLUETOOTH_CONNECT"}),"v1.5.0 permissions on Android "+api);
            check(SmartButtonPermissions.locationSwitchRequired(api,28),"v1.5.0 target-28 location compatibility on Android "+api);
        }
        check(SmartButtonPermissions.locationSwitchRequired(30,28),"legacy location gate retained");
        check(!SmartButtonPermissions.locationSwitchRequired(33,33),"future modern-target location split");
        check(Arrays.equals(SmartButtonPermissions.required(33,"shortcut"),new String[]{"android.permission.BLUETOOTH_SCAN","android.permission.BLUETOOTH_CONNECT"}),"upstream Nearby Devices policy");
        check(!SmartButtonPermissions.locationSwitchRequired(33,28,"shortcut"),"upstream mode has no app-level GPS gate on Android 12+");
        RecoveryPolicyTest.Clock c=new RecoveryPolicyTest.Clock();RecoveryPolicyTest.Radio r=new RecoveryPolicyTest.Radio(c);BleScanLoop loop=new BleScanLoop(c,r);
        loop.wanted(true);c.advance(500);int first=r.started;
        loop.binding(true);c.advance(1000);check(loop.scanning&&r.started==first,"pairing preserves a healthy scan registration");
        loop.binding(false);loop.pause(true);loop.recover(10000);loop.pause(false);c.advance(100);
        check(!loop.scanning,"background wake waits for settlement");
        loop.binding(true);c.advance(499);check(!loop.scanning,"pairing retains cleanup gap");c.advance(1);
        check(loop.scanning,"explicit pairing bypasses wake settlement");
        loop.pause(true);loop.binding(true);c.advance(500);check(loop.scanning,"explicit pairing unparks scanner");
        for(int i=0;i<12;i++){loop.failed(2);loop.binding(true);c.advance(600);}
        c.advance(65000);
        for(long t:r.times){int count=0;for(long other:r.times)if(other>=t&&other<t+30000)count++;check(count<=3,"pairing retries respect 3 starts per 30 seconds");}
        loop.wanted(false);int stopped=r.started;c.advance(1800000);check(r.started==stopped,"cancelled session cannot restart from stale pairing timers");
        RecoveryPolicyTest.Clock switchedClock=new RecoveryPolicyTest.Clock();RecoveryPolicyTest.Radio switchedRadio=new RecoveryPolicyTest.Radio(switchedClock);ArrayDeque<Long> budget=new ArrayDeque<>();
        for(int i=0;i<3;i++){BleScanLoop transport=new BleScanLoop(switchedClock,switchedRadio,budget);transport.binding(true);switchedClock.advance(500);check(transport.scanning,"transport opens within shared budget");transport.wanted(false);}
        BleScanLoop fourth=new BleScanLoop(switchedClock,switchedRadio,budget);fourth.binding(true);switchedClock.advance(1000);check(!fourth.scanning&&switchedRadio.started==3,"changing transport cannot reset Android scan-start budget");switchedClock.advance(30000);check(fourth.scanning,"mode switch resumes after shared budget window");fourth.wanted(false);
        System.out.println("PASS discovery permissions, healthy scan preservation, parked/wake pairing, cleanup gap, retry budget and shutdown");
    }
}
