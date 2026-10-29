package uy.federico.mispartidos;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Filtros deterministas de titulares para Noticias de fútbol mayor.
 * Se ejecutan tanto en RSS recién obtenido como sobre noticias en caché.
 * No realiza solicitudes de red ni consume cuotas de IA.
 */
final class NewsFilterPolicy {
    private static final Pattern AGE_GROUP = Pattern.compile("\\b(?:sub|u|under)\\s*(?:1[0-9]|2[0-3])\\b");
    private static final Pattern YOUTH = Pattern.compile("\\b(?:juveniles?|inferiores|divisiones menores|categorias menores|formativas|cantera|academia|filial|cadetes?|alevines?|futbol base|youth|academy|primavera|equipo b|equipo reserva|liga de reservas|reserves)\\b");
    private static final Pattern OTHER_SPORT = Pattern.compile("\\b(?:basquet|basquetbol|basket|basketball|baloncesto|voleibol|volleyball|voley|handball|balonmano|rugby|hockey|tenis|tennis|natacion|waterpolo|atletismo|futsal|futbol sala|futbol playa|futbol 5|esports|e sports|boxeo)\\b");
    private static final Pattern BIG_SCORE = Pattern.compile("\\b(?:[4-9][0-9]|[1-9][0-9]{2})\\s+(?:a\\s+)?(?:[4-9][0-9]|[1-9][0-9]{2})\\b");
    private static final Pattern BETTING_PREVIEW = Pattern.compile("\\b(?:pronosticos?|predicciones|cuotas|odds|mejores apuestas|consejos de apuestas|apuestas deportivas)\\b");
    private static final Pattern BETTING_SOURCE = Pattern.compile("\\b(?:apuestas ganadas|bet365|forebet|sports betting|pronosticos de futbol)\\b");
    private static final Pattern WOMEN = Pattern.compile("\\b(?:femenina|femenino|femeninas|femeninos|women|womens|femeni|liga f)\\b");

    private NewsFilterPolicy() { }

    static boolean isOffTopic(String title, String source) {
        String headline = normalize(title);
        String publisher = normalize(source);
        if (headline.isEmpty()) return true;
        if (AGE_GROUP.matcher(headline).find()) return true;
        if (YOUTH.matcher(headline).find()) return true;
        if (OTHER_SPORT.matcher(headline).find()) return true;
        if (WOMEN.matcher(headline).find()) return true;
        // Un 76-101 es incompatible con un marcador de fútbol: identifica
        // artículos de básquet aunque el titular omita el nombre del deporte.
        if (BIG_SCORE.matcher(headline).find()) return true;
        if (BETTING_PREVIEW.matcher(headline).find()) return true;
        return BETTING_SOURCE.matcher(publisher).find();
    }

    private static String normalize(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ").trim();
    }
}
