package uy.federico.mispartidos;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class TransmissionInfo {
    final boolean confirmed;
    final String country;
    final String text;

    private TransmissionInfo(boolean confirmed,String country,String text){this.confirmed=confirmed;this.country=country;this.text=text;}

    static TransmissionInfo fromJson(JSONObject value){
        if(value==null||!value.optBoolean("confirmada",false)||!isUruguay(value.optString("pais","Uruguay")))return empty();
        List<String> outlets=new ArrayList<>();
        readStrings(value.optJSONArray("canales"),outlets);readStrings(value.optJSONArray("streaming"),outlets);
        if(outlets.isEmpty())return empty();
        return new TransmissionInfo(true,"Uruguay",join(outlets));
    }

    static TransmissionInfo empty(){return new TransmissionInfo(false,"Uruguay","");}

    private static boolean isUruguay(String value){
        String normalized=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]","").toLowerCase(Locale.ROOT).trim();
        return normalized.equals("uruguay")||normalized.equals("uy")||normalized.equals("uru");
    }
    private static void readStrings(JSONArray values,List<String> out){if(values==null)return;for(int i=0;i<values.length();i++){String value=values.optString(i).trim();if(!value.isEmpty()&&!out.contains(value))out.add(value);}}
    private static String join(List<String> values){StringBuilder out=new StringBuilder();for(String value:values){if(out.length()>0)out.append(" / ");out.append(value);}return out.toString();}
}
