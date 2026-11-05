package uy.federico.mispartidos;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Relevancia local para titulares RSS: no realiza llamadas a la red ni a IA.
 * Clasifica de forma conservadora para no perder noticias legítimas.
 */
final class NewsRelevancePolicy {
    private static final Pattern FOOTBALL = Pattern.compile(
            "\\b(?:futbol|football|fc|liga|ligue|premier|serie a|champions|europa league|" +
            "libertadores|sudamericana|clasico|derbi|mundial|eliminatorias|seleccion|" +
            "partido|encuentro|goles?|penales|marcador|victoria|derrota|empate|empato|" +
            "gano|ganaron|vence|vencio|derroto|goleo|golea|goleada|remonta|remonto|clasifico|avanza|avanzo|juega|enfrenta|debut|" +
            "fichaje|ficha|ficho|refuerzo|traspaso|transferencia|mercado de pases|" +
            "contrato|renovacion|lesion|lesionado|sancion|expulsion|suspendido|" +
            "convocados?|convocatoria|entrenador|tecnico|dt|titular|once inicial|" +
            "defensa|defensor|delantero|mediocampista|arquero|golero|portero|" +
            "atacante|mediapunta|capitan|campeonato|campeon|ascenso|descenso)\\b");
    private static final Pattern HIGH_VALUE = Pattern.compile(
            "\\b(?:goles?|penales|marcador|resultado|gano|ganaron|vence|vencio|derroto|" +
            "victoria|derrota|empato|empate|clasifico|avanzo|eliminado|campeon|" +
            "fichaje|ficha|ficho|refuerzo|traspaso|transferencia|renovacion|" +
            "lesion|lesionado|sancion|expulsion|suspendido|convocados?|" +
            "convocatoria|entrenador|tecnico|formacion|alineacion|titulares)\\b");
    private static final Pattern MATCH_NEWS = Pattern.compile(
            "\\b(?:partido|encuentro|clasico|derbi|final|semifinal|cuartos|fecha|" +
            "champions|libertadores|sudamericana|premier|serie a|ligue|copa|" +
            "mundial|eliminatorias|juega|enfrenta|vs|versus|ante)\\b");
    private static final Pattern CLICKBAIT = Pattern.compile(
            "\\b(?:no dan credito|no vas a creer|nadie lo esperaba|se hace viral|" +
            "estalla la polemica|insolito|sorprende a todos|escandalo viral)\\b");
    private static final Pattern PREVIEW = Pattern.compile(
            "\\b(?:donde ver|como ver|a que hora|horario y tv|ver en vivo|" +
            "transmision en vivo|guia de tv|posibles alineaciones|probables alineaciones)\\b");
    private static final Pattern FAN_CONTEXT = Pattern.compile(
            "\\b(?:aficionados?|hinchas?|tribunas?|gradas?|estadio|signal iduna park)\\b");
    private static final Pattern FAN_NON_SPORT = Pattern.compile(
            "\\b(?:movil|celular|smartphone|telefono|usar el telefono|pantallas)\\b");
    private static final Pattern NON_SPORT_EVENT = Pattern.compile(
            "\\b(?:museo|visita guiada|tour del estadio|recorrido turistico|coleccion de ropa|" +
            "tienda oficial|merchandising|campana publicitaria)\\b");
    private static final Pattern SCORE = Pattern.compile("\\b\\d{1,2}\\s*[-:]\\s*\\d{1,2}\\b");

    private NewsRelevancePolicy() { }

    static boolean footballContext(String title) {
        String n=normalize(title);
        return FOOTBALL.matcher(n).find() || SCORE.matcher(n).find();
    }

    static boolean clearlyPeripheral(String title) {
        String n=normalize(title);
        if (n.isEmpty()) return true;
        // Ejemplo real: un evento para que los hinchas no miren el móvil.
        // Nunca ocultar una noticia de un partido, lesión o fichaje por contener "estadio".
        if (FAN_CONTEXT.matcher(n).find() && FAN_NON_SPORT.matcher(n).find()
                && !HIGH_VALUE.matcher(n).find() && !SCORE.matcher(n).find()) return true;
        return NON_SPORT_EVENT.matcher(n).find()
                && !HIGH_VALUE.matcher(n).find() && !SCORE.matcher(n).find()
                && !MATCH_NEWS.matcher(n).find();
    }

    static int rank(String title,String source,long publishedAt,long now) {
        String n=normalize(title);
        int score=0;
        if(HIGH_VALUE.matcher(n).find() || SCORE.matcher(n).find()) score+=9;
        if(MATCH_NEWS.matcher(n).find()) score+=4;
        if(FOOTBALL.matcher(n).find()) score+=3;
        if(CLICKBAIT.matcher(n).find()) score-=5;
        if(PREVIEW.matcher(n).find()) score-=4;
        if(clearlyPeripheral(title)) score-=12;
        if(publishedAt>0 && now>=publishedAt) {
            long days=(now-publishedAt)/(24L*60L*60L*1000L);
            score+= Math.max(0,6-(int)Math.min(6,days));
        }
        return score;
    }

    static boolean italianClubMatch(String club,String title) {
        String n=normalize(title);
        if("Milan".equals(club)){
            boolean named=word(n,"milan");
            return named&&(n.contains("ac milan") || footballContext(title));
        }
        if("Inter".equals(club)){
            if(!word(n,"inter") || n.contains("inter miami") ||
                    n.contains("porto alegre") || n.contains("internacional de porto"))return false;
            return n.contains("inter de milan") || n.contains("inter milan") ||
                    n.contains("internazionale") || footballContext(title);
        }
        return false;
    }

    static boolean englishLiverpoolMatch(String title) {
        String n=normalize(title);
        return word(n,"liverpool") &&
                NewsTeamPolicy.matches("Liverpool",false,title) &&
                (n.contains("anfield") || n.contains("premier") ||
                        n.contains("ingles") || n.contains("england") ||
                        footballContext(title));
    }

    private static boolean word(String text,String value) {
        return (" "+text+" ").contains(" "+value+" ");
    }

    private static String normalize(String value) {
        String lower=value==null?"":value.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower,Normalizer.Form.NFD)
                .replaceAll("\\p{M}","")
                .replaceAll("[^a-z0-9]+"," ")
                .replaceAll("\\s+"," ").trim();
    }
}
