package uy.federico.mispartidos;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Stable, language-independent matching for club and national-team favorites. */
final class TeamIdentity {
    private TeamIdentity() {}

    static boolean isFavorite(Match match, Set<String> favorites, Map<String,String> idsByFavorite) {
        if (match == null || favorites == null) return false;
        return isFavorite(match.local(),match.visitante(),match.homeTeamId,match.awayTeamId,favorites,idsByFavorite);
    }

    static boolean isFavorite(String home, String away, String homeId, String awayId,
                              Set<String> favorites, Map<String,String> idsByFavorite) {
        if (favorites == null) return false;
        for (String favorite : favorites) {
            if (favorite == null || favorite.trim().isEmpty()) continue;
            String expectedId = idsByFavorite == null ? null : idsByFavorite.get(favorite);
            if (expectedId != null && !expectedId.isEmpty()) {
                // Stable provider identity wins. Fall back to names only when the fixture lacks a side ID.
                if (expectedId.equals(homeId) || expectedId.equals(awayId)) return true;
                if ((homeId != null && !homeId.isEmpty()) && (awayId != null && !awayId.isEmpty())) continue;
            }
            if (sameName(favorite, home) || sameName(favorite, away)) return true;
        }
        return false;
    }

    static boolean sameName(String first, String second) {
        String a = canonical(first), b = canonical(second);
        return !a.isEmpty() && a.equals(b);
    }

    static String fixtureKey(String fixtureId,long kickoff,String home,String away) {
        if(fixtureId!=null&&!fixtureId.isEmpty())return "fixture|"+fixtureId;
        String a=canonical(home),b=canonical(away),pair=a.compareTo(b)<=0?a+"|"+b:b+"|"+a;
        return (kickoff/60_000L)+"|"+pair;
    }

    static String canonical(String value) {
        if (value == null) return "";
        String key = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", " ").trim()
                .replaceAll("\\b(fc|afc|cf|ca|sc|cd|club|the)\\b", " ").replaceAll("\\s+", " ").trim();
        Map<String,String> aliases = aliases();
        return aliases.getOrDefault(key, key);
    }

    private static Map<String,String> aliases() {
        Map<String,String> a = new HashMap<>();
        alias(a, "atletico de madrid", "atletico madrid", "atletico madrid fc", "atletico", "atleti", "atl madrid", "atletico mad");
        alias(a, "paris saint germain", "paris sg", "psg");
        alias(a, "internazionale", "inter milan", "inter");
        alias(a, "bayern munich", "bayern munchen", "bayern munich", "fc bayern");
        alias(a, "netherlands", "holland", "the netherlands", "paises bajos", "holanda");
        alias(a, "united states", "usa", "us", "estados unidos", "united states of america");
        alias(a, "south korea", "korea republic", "republic of korea", "corea del sur", "corea republica");
        alias(a, "ivory coast", "cote d ivoire", "cote divoire", "costa de marfil");
        alias(a, "czechia", "czech republic", "republica checa");
        alias(a, "turkey", "turkiye", "turquia");
        alias(a, "germany", "alemania"); alias(a, "spain", "espana"); alias(a, "england", "inglaterra");
        alias(a, "france", "francia"); alias(a, "brazil", "brasil"); alias(a, "japan", "japon");
        alias(a, "norway", "noruega"); alias(a, "belgium", "belgica"); alias(a, "greece", "grecia");
        alias(a, "ireland", "republic of ireland", "irlanda");
        return a;
    }

    private static void alias(Map<String,String> map, String canonical, String... variants) {
        map.put(canonical, canonical);
        for (String variant : variants) map.put(variant, canonical);
    }
}
