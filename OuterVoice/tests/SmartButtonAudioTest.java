package com.zecadev.outervoice;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Protocol regression vectors from the upstream car findings; no Android/BLE mocks. */
public final class SmartButtonAudioTest {
    private static void check(boolean good, String label) { if (!good) throw new AssertionError(label); }
    private static byte[] advert(int counter, int gesture) {
        return new byte[]{2,1,6,8,2,(byte)255,(byte)counter,(byte)gesture,50,1,2,1};
    }
    public static void main(String[] args) {
        SmartButtonAd.Advert single = SmartButtonAd.parse(advert(0,0),Accessory.BUTTON);
        check(single.gesture==SmartButtonAd.SINGLE && single.counter==0 && single.battery==50,"single advert");
        check(SmartButtonAd.parse(advert(1,2),Accessory.BUTTON).gesture==SmartButtonAd.DOUBLE,"double advert");
        check(SmartButtonAd.parse(advert(16,1),Accessory.BUTTON).gesture==SmartButtonAd.LONG,"pairing long press");
        check(SmartButtonAd.parse(advert(17,99),Accessory.BUTTON).gesture==0,"unknown gesture");
        byte[] malformed=advert(1,0);malformed[5]=0;
        check(SmartButtonAd.parse(malformed,Accessory.BUTTON)==null,"reject wrong packet type");
        check(SmartButtonAd.parse(new byte[5],Accessory.BUTTON)==null,"reject short packet");
        check(!SmartButtonAd.isNew(254,-1) && !SmartButtonAd.isNew(1,1),"ignore invalid and repeat counters");
        check(SmartButtonAd.isNew(0,17) && SmartButtonAd.isNew(16,1),"counter wrap and cycle");
        check(SmartButtonAd.samePress(1,true,100,1,false,800),"deduplicate GATT plus advert");
        check(!SmartButtonAd.samePress(1,true,100,1,true,800),"two presses on same channel are distinct");
        check(!SmartButtonAd.samePress(1,true,100,2,false,800),"single and double stay distinct");
        check(!SmartButtonAd.samePress(1,true,100,1,false,1700),"cross-channel dedup expires");
        check(SmartButtonAd.parseNotification("1".getBytes(StandardCharsets.US_ASCII)).gesture==1,"GATT single");
        check(SmartButtonAd.parseNotification("2".getBytes(StandardCharsets.US_ASCII)).gesture==2,"GATT double");
        check(SmartButtonAd.parseNotification("zk_v_1_2_1".getBytes(StandardCharsets.US_ASCII)).gesture==0,"firmware query is not a press");
        check(SmartButtonAd.parseNotification("psn=CSB10Z1A123".getBytes(StandardCharsets.US_ASCII)).gesture==0,"serial query is not a press");
        check(Arrays.equals(SmartButtonAd.askVersion(),new byte[]{(byte)165,(byte)165,3,6,1,84}),"query checksum");
        check(Math.abs(ExteriorGain.linear(6)-1.9952623)<.00001,"6dB is amplitude x2");
        check(Math.abs(ExteriorGain.linear(24)-15.8489319)<.00001,"24dB amplitude");
        check(ExteriorGain.linear(-1)==1 && ExteriorGain.linear(25)==ExteriorGain.linear(24),"gain bounds");
        check(ExteriorGain.sample(-32768,1)==-32768 && ExteriorGain.sample(32767,1)==32767,"0dB bit exact endpoints");
        short prev=0;
        for(int sample=0;sample<=32767;sample++) {
            short out=ExteriorGain.sample(sample,ExteriorGain.linear(24));
            check(out>=prev && out>=0,"positive PCM never wraps");prev=out;
            check(ExteriorGain.sample(-sample,ExteriorGain.linear(24))<=0,"negative PCM never flips sign");
        }
        byte[] pcm={0,4,0,(byte)252};ExteriorGain.apply(pcm,4,ExteriorGain.linear(6));
        check((short)((pcm[0]&255)|(pcm[1]<<8))>2000,"little endian positive amplification");
        check((short)((pcm[2]&255)|(pcm[3]<<8))< -2000,"little endian negative amplification");
        System.out.println("PASS BLE advert/GATT vectors, pairing gestures, counter cycle, deduplication, queries and 0..24 dB PCM gain/limiter");
    }
}
