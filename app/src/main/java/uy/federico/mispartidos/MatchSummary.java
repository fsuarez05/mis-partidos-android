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
    String context="",updated="",fallbackReason="";
    final TeamInfo home=new TeamInfo(),away=new TeamInfo();
    final List<String> news=new ArrayList<>();
    final List<Absence> absences=new ArrayList<>();
    final List<Source> sources=new ArrayList<>();
    String rawJson="";

    static MatchSummary fromJson(JSONObject j){
        MatchSummary r=new MatchSummary();r.rawJson=j.toString();r.found=j.optBoolean("encontrado",true);r.context=j.optString("contexto");
        r.updated=j.optString("actualizado");r.fromCache=j.optBoolean("desdeCache");r.fallback=j.optBoolean("respaldoPorError");r.fallbackReason=j.optString("motivoRespaldo");
        readTeam(j.optJSONObject("local"),r.home);readTeam(j.optJSONObject("visitante"),r.away);
        JSONArray n=j.optJSONArray("novedades");if(n!=null)for(int i=0;i<n.length();i++){String value=n.optString(i).trim();if(!value.isEmpty())r.news.add(value);}
        JSONArray b=j.optJSONArray("bajas");if(b!=null)for(int i=0;i<b.length();i++){JSONObject x=b.optJSONObject(i);if(x==null)continue;Absence a=new Absence();a.team=x.optString("equipo");a.player=x.optString("jugador");a.reason=x.optString("motivo");r.absences.add(a);}
        JSONArray s=j.optJSONArray("fuentes");if(s!=null)for(int i=0;i<s.length();i++){JSONObject x=s.optJSONObject(i);if(x==null)continue;Source q=new Source();q.title=x.optString("titulo");q.url=x.optString("url");q.date=x.optString("fecha");q.type=x.optString("tipo");if(!q.url.isEmpty())r.sources.add(q);}
        return r;
    }
    int informationScore(){int score=context.trim().isEmpty()?0:2;if(!home.form.trim().isEmpty())score+=2;if(!away.form.trim().isEmpty())score+=2;score+=Math.min(2,news.size());score+=Math.min(2,absences.size());score+=Math.min(2,sources.size());return score;}
    private static void readTeam(JSONObject j,TeamInfo out){if(j==null)return;out.name=j.optString("nombre");out.form=j.optString("comoLlega");out.quality=j.optString("calidadInformacion");}
}
