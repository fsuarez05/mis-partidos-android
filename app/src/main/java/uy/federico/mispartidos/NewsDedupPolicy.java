package uy.federico.mispartidos;

import java.net.URI;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognizes syndicated reports of the same event without merging unrelated football news. */
final class NewsDedupPolicy {
    private static final long SAME_EVENT_WINDOW_MS = 48L * 60L * 60L * 1000L;
    private static final Set<String> STOP_WORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "para", "como", "pero", "ante", "sobre", "tras", "entre", "desde", "hasta", "esta", "este",
            "estos", "estas", "tiene", "tienen", "tambien", "que", "quien", "quienes", "porque",
            "fue", "son", "del", "las", "los", "una", "uno", "por", "con", "sin", "sus", "mas",
            "ese", "esa", "hoy", "ayer", "manana", "club", "equipo", "partido", "futbol", "liga",
            "video", "mira", "todos", "todas", "esto", "esta", "asi", "segun", "diario", "ultima", "ultimas",
            "noticias", "deja", "nuevo", "ahora", "gran", "sera", "estas", "puede", "toda", "todo",
            "tres", "contra", "luego", "vuelve", "solo", "serie", "copa", "deporte", "2026", "2027"
    )));
    private static final Pattern SCORE = Pattern.compile("\\b\\d{1,2}\\s*[-:]\\s*\\d{1,2}\\b");
    private static final Pattern TRACKING = Pattern.compile("^(?:utm_[a-z0-9_]+|fbclid|gclid|igshid|ref|source)$");
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");
    private static final Pattern CALL_UP = Pattern.compile("\\b(?:convocados?|convocatoria|nomina|citados|lista de jugadores|lista de futbolistas)\\b");
    // Pair of teams/countries in a specific call-up announcement (not just any
    // two articles about one club). Conservative: require the same two sides.
    private static final String[] EVENT_TEAMS = {
        "argentina", "uruguay", "brasil", "chile", "colombia", "paraguay",
        "peru", "ecuador", "bolivia", "francia", "espana", "inglaterra",
        "alemania", "portugal", "italia", "noruega", "japon", "india",
        "penarol", "nacional", "barcelona", "real madrid", "chelsea",
        "liverpool", "manchester city", "manchester united", "juventus",
        "milan", "inter", "psg", "atletico de madrid", "arsenal"
    };
    private static final Pattern OPPONENT = Pattern.compile("\\b(?:ante|contra|versus|vs|frente a|frente al|al)\\s+(?:el\\s+|la\\s+|los\\s+)?([a-z0-9]+)\\b");

    private NewsDedupPolicy() { }

    static boolean sameStory(String titleA, String linkA, long timeA,
                             String titleB, String linkB, long timeB) {
        if (titleA == null || titleB == null || titleA.trim().isEmpty() || titleB.trim().isEmpty()) return false;
        if (Math.abs(timeA - timeB) > SAME_EVENT_WINDOW_MS) return false;
        String urlA = canonicalUrl(linkA), urlB = canonicalUrl(linkB);
        if (!urlA.isEmpty() && urlA.equals(urlB)) return true;

        String a = normalize(titleA), b = normalize(titleB);
        if (a.equals(b)) return true;
        String rivalA = opponent(a), rivalB = opponent(b);
        if (!rivalA.isEmpty() && !rivalB.isEmpty() && !rivalA.equals(rivalB)) return false;
        // Never treat two different reported scores as the same article/event.
        Set<String> scoresA = scores(titleA), scoresB = scores(titleB);
        if (!scoresA.isEmpty() && !scoresB.isEmpty() && !scoresA.equals(scoresB)) return false;
        // Similar headlines can mention the same two countries while announcing
        // different national-team squads. Do not let generic word overlap merge them.
        String squadA = callUpSubject(a), squadB = callUpSubject(b);
        if (!squadA.isEmpty() && !squadB.isEmpty() && !squadA.equals(squadB)) return false;
        if (sameCallUpEvent(a,b,Math.abs(timeA-timeB))) return true;

        Set<String> wordsA = importantWords(a), wordsB = importantWords(b);
        int shorter = Math.min(wordsA.size(), wordsB.size());
        int longer = Math.max(wordsA.size(), wordsB.size());
        if (shorter < 4) return false;
        int shared = 0;
        for (String word : wordsA) if (wordsB.contains(word)) shared++;
        // Require at least four meaningful shared concepts; both containment and
        // coverage prevent merging merely because the same club is named.
        return (shared >= 5 && shared * 1.0 / shorter >= 0.58 && shared * 1.0 / longer >= 0.45)
                || (shared >= 4 && shared * 1.0 / shorter >= 0.75 && shared * 1.0 / longer >= 0.60);
    }

    private static boolean sameCallUpEvent(String a, String b, long elapsedMs) {
        if (!CALL_UP.matcher(a).find() || !CALL_UP.matcher(b).find()) return false;
        String subjectA=callUpSubject(a), subjectB=callUpSubject(b);
        if(subjectA.isEmpty() || !subjectA.equals(subjectB))return false;
        Set<String> participantsA=participants(a), participantsB=participants(b);
        if(participantsA.size()>=2 && participantsA.equals(participantsB))return true;
        // Same national-team squad announcement can be reported with or without
        // naming its opponent. Use a narrow six-hour window and require subset
        // participants; never merge articles naming conflicting opponents.
        Set<String> smaller=participantsA.size()<=participantsB.size()?participantsA:participantsB;
        Set<String> larger=participantsA.size()<=participantsB.size()?participantsB:participantsA;
        return elapsedMs<=6L*60L*60L*1000L && smaller.size()==1 && larger.size()==2
                && larger.containsAll(smaller) && isNationalParticipant(smaller.iterator().next());
    }

    private static String callUpSubject(String title) {
        String padded=" "+title+" ";
        for(String name:EVENT_TEAMS) {
            if(!isNationalParticipant(name))continue;
            if(padded.contains(" seleccion de "+name+" ") ||
                    padded.contains(" seleccion "+name+" ") ||
                    padded.contains(" convocados por "+name+" ") ||
                    padded.contains(" convocatoria de "+name+" ") ||
                    padded.contains(" "+name+" revelo ") ||
                    padded.contains(" "+name+" presento ") ||
                    padded.contains(" "+name+" confirmo ") ||
                    padded.contains(" "+name+" anuncia ") ||
                    padded.contains(" "+name+" convoca "))return name;
        }
        return "";
    }

    private static boolean isNationalParticipant(String name) {
        return "argentina".equals(name) || "uruguay".equals(name) || "brasil".equals(name)
                || "chile".equals(name) || "colombia".equals(name) || "paraguay".equals(name)
                || "peru".equals(name) || "ecuador".equals(name) || "bolivia".equals(name)
                || "francia".equals(name) || "espana".equals(name) || "inglaterra".equals(name)
                || "alemania".equals(name) || "portugal".equals(name) || "italia".equals(name)
                || "noruega".equals(name) || "japon".equals(name) || "india".equals(name);
    }

    private static Set<String> participants(String title) {
        Set<String> result=new HashSet<>();
        String padded=" "+title+" ";
        for (String name:EVENT_TEAMS) {
            if (padded.contains(" "+name+" ")) result.add(name);
        }
        return result;
    }

    private static String opponent(String normalized) {
        Matcher matcher = OPPONENT.matcher(normalized);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static Set<String> importantWords(String title) {
        Set<String> words = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(title);
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() < 4 || STOP_WORDS.contains(word)) continue;
            word = canonicalWord(word);
            if (!STOP_WORDS.contains(word)) words.add(word);
        }
        return words;
    }

    private static String canonicalWord(String word) {
        if (word.equals("muere") || word.equals("murio") || word.equals("fallece")
                || word.equals("fallecio") || word.equals("fallecimiento") || word.equals("luto")) return "fallec";
        if (word.equals("seleccionador") || word.equals("entrenador") || word.equals("tecnico") || word.equals("director")) return "entrenador";
        if (word.startsWith("avanzo") || word.startsWith("avanza") || word.startsWith("avanzan")) return "avanz";
        if (word.equals("confirmo") || word.equals("confirma") || word.equals("confirmaron") || word.equals("confirmado")) return "confirm";
        if (word.equals("victoria") || word.equals("vencio") || word.equals("derroto") || word.equals("gano")) return "victoria";
        return word;
    }

    private static Set<String> scores(String title) {
        Set<String> results = new HashSet<>();
        Matcher matcher = SCORE.matcher(title);
        while (matcher.find()) results.add(matcher.group().replaceAll("\\s+", "").replace(':', '-'));
        return results;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String canonicalUrl(String link) {
        if (link == null || link.trim().isEmpty()) return "";
        try {
            URI uri = new URI(link.trim());
            String host = uri.getHost();
            if (host == null) return "";
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            String query = uri.getRawQuery();
            StringBuilder kept = new StringBuilder();
            if (query != null) for (String piece : query.split("&")) {
                String key = piece.split("=", 2)[0].toLowerCase(Locale.ROOT);
                if (TRACKING.matcher(key).matches()) continue;
                if (kept.length() > 0) kept.append('&');
                kept.append(piece);
            }
            return scheme + "://" + host.toLowerCase(Locale.ROOT) + path + (kept.length() > 0 ? "?" + kept : "");
        } catch (Exception ignored) {
            return "";
        }
    }
}
