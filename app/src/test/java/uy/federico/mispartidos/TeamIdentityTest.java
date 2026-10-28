package uy.federico.mispartidos;

import org.junit.Test;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import static org.junit.Assert.*;

public class TeamIdentityTest {
    @Test public void stableIdMatchesTranslatedAndAlternativeClubNames() {
        Map<String,String> ids=Collections.singletonMap("Atlético de Madrid","team-42");
        assertTrue(TeamIdentity.isFavorite("Atletico Madrid","Deportivo de La Coruña","team-42","team-99",
                Collections.singleton("Atlético de Madrid"),ids));
    }

    @Test public void knownIdMismatchCannotBeOverriddenBySameName() {
        assertFalse(TeamIdentity.isFavorite("Arsenal","Everton","wrong-id","e2",
                Collections.singleton("Arsenal"),Collections.singletonMap("Arsenal","arsenal-london")));
    }

    @Test public void fallsBackToNamesOnlyWhenFixtureHasNoStableTeamIds() {
        assertTrue(TeamIdentity.isFavorite("Atlético Madrid","Deportivo La Coruna","","",
                Collections.singleton("Atlético de Madrid"),Collections.singletonMap("Atlético de Madrid","team-42")));
    }

    @Test public void namesMatchAccentAndAtleticoVariantsWhenNoIdExists() {
        assertTrue(TeamIdentity.sameName("Atlético de Madrid","Atl Madrid"));
        assertTrue(TeamIdentity.sameName("Deportivo de La Coruña","Deportivo La Coruna"));
    }

    @Test public void nationalSelectionsMatchTranslationsAndCommonAliases() {
        HashSet<String> favorites=new HashSet<>();favorites.add("Países Bajos");favorites.add("Costa de Marfil");
        assertTrue(TeamIdentity.isFavorite("Holland","Japan","","",favorites,Collections.emptyMap()));
        assertTrue(TeamIdentity.isFavorite("Côte d'Ivoire","Ghana","","",favorites,Collections.emptyMap()));
        assertTrue(TeamIdentity.sameName("United States","Estados Unidos"));
    }

    @Test public void oneFixtureWithTwoFavoriteTeamsStillMatchesAsOneFixture() {
        HashSet<String> favorites=new HashSet<>();favorites.add("Arsenal");favorites.add("Everton");
        Map<String,String> ids=new HashMap<>();ids.put("Arsenal","a1");ids.put("Everton","e1");
        assertTrue(TeamIdentity.isFavorite("Arsenal FC","Everton FC","a1","e1",favorites,ids));
        assertEquals(TeamIdentity.fixtureKey("fixture-7",1_800_000_000_000L,"Arsenal FC","Everton FC"),
                TeamIdentity.fixtureKey("fixture-7",1_800_000_000_000L,"Arsenal","Everton"));
    }

    @Test public void noIdDuplicatesUseCanonicalNamesAndMinute() {
        assertEquals(TeamIdentity.fixtureKey("",1_800_000_000_000L,"Atlético Madrid","Deportivo de La Coruña"),
                TeamIdentity.fixtureKey("",1_800_000_030_000L,"Deportivo La Coruna","Atl Madrid"));
    }
}
