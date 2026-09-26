package uy.federico.mispartidos;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

class MatchSummary {
    static class TeamInfo {String name="",form="",quality="";}
    static class Absence {String team="",player="",reason="";}
    static class Source {String title="",url="",date="",type="";}
    boolean found,fromCache,fallback;
    String context="",updated="",fallbackReason="",broadcastCountry="",broadcastText="";
    final TeamInfo home=new TeamInfo(),away=new TeamInfo();
    final List<String> news=new ArrayList<>();
    final List<Absence> absences=new ArrayList<>();
    final List<Source> sources=new ArrayList<>();
    String rawJson="";

    static MatchSummary fromJson(JSONObject j){
        MatchSummary r=new MatchSummary();r.rawJson=j.toString();r.found=j.optBoolean("encontrado",true);r.context=j.optString("contexto");
        r.updated=j.optString("actualizado");r.fromCache=j.optBoolean("desdeCache");r.fallback=j.optBoolean("respaldoPorError");r.fallbackReason=j.optString("motivoRespaldo");
        JSONObject transmission=j.optJSONObject("transmision");
        if(transmission!=null){
            r.broadcastCountry=transmission.optString("pais","Uruguay");
            List<String> outlets=new ArrayList<>();
            readStrings(transmission.optJSONArray("canales"),outlets);readStrings(transmission.optJSONArray("streaming"),outlets);
            if(outlets.isEmpty()){String value=transmission.optString("texto");if(!value.trim().isEmpty())outlets.add(value.trim());}
            r.broadcastText=android.text.TextUtils.join(" / ",outlets);
        }else{
            r.broadcastCountry=j.optString("paisTransmision","Uruguay");
            r.broadcastText=j.optString("dondeVer",j.optString("transmision",""));
        }
        readTeam(j.optJSONObject("local"),r.home);readTeam(j.optJSONObject("visitante"),r.away);
        JSONArray n=j.optJSONArray("novedades");if(n!=null)for(int i=0;i<n.length();i++){String value=sanitizeTemporalClaims(n.optString(i));if(!value.isEmpty())r.news.add(value);}
        JSONArray b=j.optJSONArray("bajas");if(b!=null)for(int i=0;i<b.length();i++){JSONObject x=b.optJSONObject(i);if(x==null)continue;Absence a=new Absence();a.team=ApiClient.displayTeamName(x.optString("equipo"));a.player=x.optString("jugador");a.reason=x.optString("motivo");r.absences.add(a);}
        JSONArray s=j.optJSONArray("fuentes");if(s!=null)for(int i=0;i<s.length();i++){JSONObject x=s.optJSONObject(i);if(x==null)continue;Source q=new Source();q.title=x.optString("titulo");q.url=x.optString("url");q.date=x.optString("fecha");q.type=x.optString("tipo");if(!q.url.isEmpty())r.sources.add(q);}
        return r;
    }
    private static void readStrings(JSONArray values,List<String> out){if(values==null)return;for(int i=0;i<values.length();i++){String value=values.optString(i).trim();if(!value.isEmpty()&&!out.contains(value))out.add(value);}}
    int informationScore(){int score=context.trim().isEmpty()?0:2;if(!home.form.trim().isEmpty())score+=2;if(!away.form.trim().isEmpty())score+=2;score+=Math.min(2,news.size());score+=Math.min(2,absences.size());score+=Math.min(2,sources.size());return score;}
    private static void readTeam(JSONObject j,TeamInfo out){if(j==null)return;out.name=ApiClient.displayTeamName(j.optString("nombre"));out.form=sanitizeTemporalClaims(j.optString("comoLlega"));out.quality=j.optString("calidadInformacion");}
    private static String sanitizeTemporalClaims(String value){
        String[]sentences=(value==null?"":value).trim().split("(?<=[.!?])\\s+");StringBuilder clean=new StringBuilder();
        for(String sentence:sentences){String s=sentence.toLowerCase(java.util.Locale.ROOT);boolean stale=(s.contains("sorteo")&&(s.contains("próxim")||s.contains("proxim")||s.contains("espera")||s.contains("previsto")||s.contains("participará")||s.contains("participara")))||s.contains("en los próximos días")||s.contains("en los proximos dias");if(!stale){if(clean.length()>0)clean.append(' ');clean.append(sentence.trim());}}
        return clean.toString().trim();
    }
}
