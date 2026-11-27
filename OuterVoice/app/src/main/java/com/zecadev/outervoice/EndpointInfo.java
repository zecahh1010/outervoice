package com.zecadev.outervoice;

import java.util.*;

/** Port identity and honest labels: never infers physical mic position from enumeration order. */
public final class EndpointInfo {
    public final int id,type;public final String address,product;
    public EndpointInfo(int id,int type,String address,String product){this.id=id;this.type=type;this.address=clean(address);this.product=clean(product);}
    private static String clean(String s){return s==null?"":s;}
    public String legacy(){return type+"|"+address+"|"+product;}
    public String key(){return "port-v2|"+id+"|"+legacy();}
    public static EndpointInfo resolve(List<EndpointInfo> ports,String saved,boolean sameBoot){
        if(saved==null||saved.isEmpty())return null;String fingerprint=saved;Integer savedId=null;
        if(saved.startsWith("port-v2|")){int separator=saved.indexOf('|',8);if(separator<0)return null;
            try{savedId=Integer.valueOf(saved.substring(8,separator));}catch(NumberFormatException e){return null;}fingerprint=saved.substring(separator+1);}
        List<EndpointInfo> matches=new ArrayList<>();for(EndpointInfo p:ports)if(p.legacy().equals(fingerprint))matches.add(p);
        if(matches.size()==1)return matches.get(0);
        if(matches.size()>1&&savedId!=null&&sameBoot)for(EndpointInfo p:matches)if(p.id==savedId)return p;
        return null; // Ambiguous legacy names or rebooted duplicate ports must be selected explicitly.
    }
    public static boolean external(String address){return clean(address).toUpperCase(Locale.ROOT).startsWith("BUS12");}
    public boolean microphoneCandidate(){
        String detail=(address+" "+product).toLowerCase(Locale.ROOT);
        return !detail.contains("tuner_record")&&type!=16&&type!=17&&type!=18&&type!=25&&type!=28;
    }
    public static boolean allowedOutput(int type,String address){String a=clean(address).toUpperCase(Locale.ROOT);
        return external(a)||(!a.contains("OUTER")&&!a.startsWith("BUS11")&&(type!=21||a.startsWith("BUS00")));}
    public static String role(int type,boolean input,String address,String product){String detail=(clean(address)+" "+clean(product)).toLowerCase(Locale.ROOT);
        if(input&&detail.contains("tuner_record"))return "收音機錄音輸入";
        if(!input&&external(address))return "車外喇叭";
        switch(type){case 15:return "內置麥克風";case 21:return input?"車機輸入埠":"車內媒體喇叭";
            case 2:return "車內喇叭";case 7:return input?"藍牙通話咪":"藍牙通話輸出";
            case 8:return "藍牙音樂輸出";case 11:case 22:return input?"USB 麥克風":"USB 音訊輸出";
            case 3:return input?"有線耳機咪":"有線耳機";case 4:return "有線耳機";
            case 16:return "收音機訊號";case 17:return "電視調諧訊號";case 18:return "通話音訊";
            case 19:return "AUX 線路音訊";case 20:return "網絡音訊";case 25:return "系統混音輸入";
            case 26:return input?"藍牙 LE 耳機咪":"藍牙 LE 耳機";case 28:return "回聲參考輸入";
            default:return (input?"音訊輸入":"音訊輸出")+"（類型 "+type+"）";}
    }
    public String label(int menuPosition,boolean input,String model){
        String detail=product;String m=clean(model);
        if(!m.isEmpty()&&detail.regionMatches(true,0,m,0,m.length()))detail=detail.substring(m.length());
        detail=detail.replaceFirst("(?i)^(?:pixel|pixal)\\s*3a(?=$|\\s*[-·:])","").replaceFirst("^[\\s\\-·:_]+","").trim();
        if(detail.toLowerCase(Locale.ROOT).startsWith("android sdk built for"))detail="";
        if(!address.isEmpty())detail=address+(detail.isEmpty()||detail.equals(address)?"":" / "+detail);
        return String.format(Locale.US,"%02d｜",menuPosition)+role(type,input,address,product)+" · ID "+id+(detail.isEmpty()?"":" · "+detail);
    }
}
