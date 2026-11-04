package uy.federico.mispartidos;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Identidad de clubes/selecciones en titulares de Google News.
 * No confunde palabras parciales ("Inter" != "internacional") ni clubes homónimos.
 * Sin red, Gemini ni Tavily.
 */
final class NewsTeamPolicy {
    private static final Set<String> NATIONAL_NAMES =
            new HashSet<>(Arrays.asList(AppStore.NATIONAL_TEAMS));
    private static final String[] OTHER_COUNTRIES = {
            "uruguay", "argentina", "brasil", "chile", "colombia", "paraguay", "peru",
            "ecuador", "bolivia", "venezuela", "alemania", "espana", "francia",
            "inglaterra", "italia", "portugal", "noruega", "japon", "india",
            "mexico", "estados unidos", "canada", "croacia", "marruecos",
            "paises bajos", "holanda", "belgica", "suiza", "turquia", "australia"
    };

    private NewsTeamPolicy() { }

    static boolean isNationalName(String team) {
        return NATIONAL_NAMES.contains(team);
    }

    static boolean matches(String team, boolean national, String title) {
        String n=normalize(title), name=normalize(team);
        if(n.isEmpty() || name.isEmpty()) return false;
        if(national) return nationalMatch(name,n);
        switch(team) {
            case "Liverpool (Uruguay)":
                return word(n,"liverpool") && hasUruguayanLiverpoolContext(n) &&
                        !hasEnglishLiverpoolContext(n);
            case "Liverpool":
                return word(n,"liverpool") && !hasUruguayanLiverpoolContext(n) &&
                        (hasEnglishLiverpoolContext(n) || NewsRelevancePolicy.footballContext(title));
            case "Inter":
                return (word(n,"inter") || word(n,"internazionale")) &&
                        !any(n,"inter miami","inter de miami","inter turku",
                                "inter bogota","inter de palmira","inter porto alegre",
                                "inter de porto alegre","inter club d escaldes",
                                "internacional de porto alegre") &&
                        (any(n,"inter milan","inter de milan","internazionale",
                                "inter milano","inter de milao","nerazzurri","scudetto")
                                || NewsRelevancePolicy.footballContext(title));
            case "Milan": {
                // "Inter Milan venció..." es del Inter, no del AC Milan.
                String withoutInter=n.replaceAll("\\binter\\s+(?:de\\s+)?milan\\b"," ")
                        .replaceAll("\\binter\\s+milano\\b"," ");
                return word(withoutInter,"milan") &&
                        (any(n,"ac milan","rossoneri","milan fc") ||
                                NewsRelevancePolicy.footballContext(title));
            }
            case "Nacional":
                return word(n,"nacional") &&
                        !any(n,"atletico nacional","nacional de medellin",
                                "nacional potosi","nacional de paraguay",
                                "nacional de asuncion","nacional de colombia",
                                "seleccion nacional") &&
                        any(n,"club nacional","nacional de football","nacional de montevideo",
                                "gran parque central","parque central","tricolor","bolso",
                                "decano","uruguay","montevideo","clasico uruguayo","liga auf",
                                "copa auf","campeonato uruguayo",
                                "penarol","danubio","defensor sporting")
                        || NewsRelevancePolicy.footballContext(title);
            case "River Plate":
                return ((phrase(n,"river plate") && NewsRelevancePolicy.footballContext(title)) ||
                        (word(n,"river") && any(n,"gallardo","millonario",
                                "monumental","copa argentina"))) &&
                        !any(n,"river plate de montevideo","river plate uruguay",
                                "river plate uruguayo","river plate de uruguay",
                                "torneo clausura uruguayo","liga auf");
            case "Racing Club":
                return word(n,"racing") &&
                        !any(n,"racing de montevideo","racing club de montevideo",
                                "racing de sayago","racing uruguay",
                                "racing de ferrol","racing santander",
                                "racing de cordoba") &&
                        (any(n,"racing club","racing de avellaneda","racing avellaneda",
                                "la academia","el cilindro","cilindro de avellaneda",
                                "liga profesional","copa argentina") ||
                                (any(n,"racing","academia") &&
                                        NewsRelevancePolicy.footballContext(title)));
            case "Independiente":
                return word(n,"independiente") &&
                        !any(n,"independiente del valle","independiente medellin",
                                "independiente de medellin","independiente rivadavia",
                                "independiente santa fe") &&
                        (NewsRelevancePolicy.footballContext(title) ||
                                any(n,"avellaneda","rey de copas"));
            case "Internacional":
                return word(n,"internacional") &&
                        !any(n,"amistoso internacional","torneo internacional",
                                "futbol internacional","seleccion internacional") &&
                        any(n,"porto alegre","brasileirao","beira rio","colorado",
                                "inter de porto","gremio","brasil");
            case "Barcelona":
                return (word(n,"barcelona") || word(n,"barca")) &&
                        !any(n,"barcelona sc","barcelona sporting club",
                                "barcelona de guayaquil","barcelona ecuador","barcelona de ecuador",
                                "barcelona ecuatoriano") &&
                        (word(n,"barca") || NewsRelevancePolicy.footballContext(title));
            case "Arsenal":
                return word(n,"arsenal") &&
                        !any(n,"arsenal de sarandi","arsenal sarandi","arsenal argentina") &&
                        NewsRelevancePolicy.footballContext(title);
            case "Atlético de Madrid":
                return any(n,"atletico de madrid","atletico madrid") ||
                        (word(n,"atleti") && NewsRelevancePolicy.footballContext(title));
            case "Manchester City":
                return any(n,"manchester city","man city");
            case "Manchester United":
                return any(n,"manchester united","man united","man utd",
                        "manchester utd");
            case "PSG":
                return word(n,"psg") || any(n,"paris saint germain","paris sg");
            case "Bayern Múnich":
                return any(n,"bayern","bayern munich","bayern munchen","bayern muenchen");
            case "Borussia Dortmund":
                return any(n,"borussia dortmund","dortmund") || word(n,"bvb");
            case "Juventus":
                return word(n,"juventus") ||
                        (word(n,"juve") && NewsRelevancePolicy.footballContext(title));
            case "Monaco":
                return word(n,"monaco") && NewsRelevancePolicy.footballContext(title);
            default:
                return phrase(n,name);
        }
    }

    private static boolean hasUruguayanLiverpoolContext(String n) {
        return any(n,"liverpool de montevideo","liverpool montevideo",
                "liverpool uruguay","liverpool del uruguay","liverpool fc de uruguay","liverpool uruguayo","liverpool futbol club",
                "belvedere","negriazul","liga auf","campeonato uruguayo",
                "torneo clausura","torneo apertura","torneo intermedio",
                "penarol","nacional","danubio","defensor sporting","boston river",
                "cerro largo","montevideo city torque","primera division uruguaya");
    }

    private static boolean hasEnglishLiverpoolContext(String n) {
        return any(n,"liverpool fc england","liverpool ingles","premier league",
                "anfield","liverpool england","liverpool inglaterra",
                "carabao cup","fa cup","arne slot");
    }

    private static boolean nationalMatch(String country,String n) {
        if(!phrase(n,country))return false;
        // "Uruguay Montevideo" es un club; "Brasil de Pelotas" también.
        if(any(n,"uruguay montevideo","brasil de pelotas",
                "argentina juniors","brasiliense"))return false;
        // Exigir contexto de la selección, no solo el nombre del país.
        if(any(n,"seleccion de "+country,"seleccion "+country,
                "seleccion nacional de "+country,"equipo nacional de "+country,
                "nacional de "+country,"national team",
                "copa del mundo","world cup","mundial","copa america","eurocopa",
                "nations league",
                "eliminatorias","clasificatorias","amistoso internacional",
                "fecha fifa","convocados","convocatoria") &&
                NewsRelevancePolicy.footballContext(n)) return true;
        // "Uruguay venció a Argentina": encuentro entre dos selecciones
        // aunque el titular no diga "selección".
        if(!any(n,"vencio","vence","gano","goleo","empato","empate",
                "enfrenta","enfrentara","contra","ante","vs","versus","derroto",
                "cayo","juega","jugara","jugaran","disputan",
                "definen","clasifico","elimino")) return false;
        for(String other:OTHER_COUNTRIES) {
            if(!other.equals(country) && phrase(n,other))return true;
        }
        return false;
    }

    private static boolean any(String n,String... terms) {
        for(String term:terms)if(phrase(n,term))return true;
        return false;
    }

    private static boolean word(String n,String term) {
        return phrase(n,term);
    }

    private static boolean phrase(String n,String phrase) {
        return (" "+n+" ").contains(" "+phrase+" ");
    }

    private static String normalize(String value) {
        String v=value==null?"":value.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(v,Normalizer.Form.NFD)
                .replaceAll("\\p{M}","")
                .replaceAll("[^a-z0-9]+"," ")
                .replaceAll("\\s+"," ").trim();
    }
}
