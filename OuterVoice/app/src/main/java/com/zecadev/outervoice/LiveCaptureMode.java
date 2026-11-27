package com.zecadev.outervoice;

/** Capture policy independent of Android runtime, including the API 29 source guard. */
public final class LiveCaptureMode {
    public static final String COMPAT="compat",PERFORMANCE="performance",STANDARD="standard";
    // Values defined by MediaRecorder.AudioSource; these are requests, not proof of OEM processing.
    public static final int MIC=1,VOICE_RECOGNITION=6,UNPROCESSED=9,VOICE_PERFORMANCE=10;
    private LiveCaptureMode(){}
    public static String normalize(String mode){return STANDARD.equals(mode)?STANDARD:PERFORMANCE.equals(mode)?PERFORMANCE:COMPAT;}
    public static String label(String mode){return STANDARD.equals(mode)?"標準麥克風":PERFORMANCE.equals(mode)?"低延遲傳聲":"原始／相容";}
    public static int source(String mode,int api,boolean rawAvailable){
        if(STANDARD.equals(normalize(mode)))return MIC;
        if(PERFORMANCE.equals(normalize(mode))){
            if(api<29)throw new IllegalArgumentException("低延遲傳聲模式需要 Android 10 或以上，請選原始／相容收音");
            return VOICE_PERFORMANCE;
        }
        return rawAvailable?UNPROCESSED:VOICE_RECOGNITION;
    }
    public static String sourceName(int source){switch(source){
        case MIC:return "MIC (1)";
        case VOICE_PERFORMANCE:return "VOICE_PERFORMANCE (10)";
        case UNPROCESSED:return "UNPROCESSED (9)";
        case VOICE_RECOGNITION:return "VOICE_RECOGNITION (6)";
        default:return "source "+source;
    }}
}
