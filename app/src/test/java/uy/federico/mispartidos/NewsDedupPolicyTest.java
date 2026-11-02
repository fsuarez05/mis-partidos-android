package uy.federico.mispartidos;

import org.junit.Test;
import static org.junit.Assert.*;

public class NewsDedupPolicyTest {
    private static final long NOW = 1_800_000_000_000L;
    private boolean same(String a,String b) {
        return NewsDedupPolicy.sameStory(a,"",NOW,b,"",NOW+60_000L);
    }

    @Test public void exactlySameTitleIsDuplicate(){assertTrue(same("Peñarol ganó el clásico 2-1","Peñarol ganó el clásico 2-1"));}
    @Test public void differentAccentsAndPunctuationAreDuplicate(){assertTrue(same("PSG ganó 3-0 en París: resumen y mejores jugadas","PSG gano 3-0 en Paris - resumen y mejores jugadas"));}
    @Test public void identicalArticleAcrossTrackingLinksIsDuplicate(){assertTrue(NewsDedupPolicy.sameStory("Milan prepara el derbi","https://example.com/deporte/noticia?utm_source=google",NOW,"Milan se prepara para enfrentar al Inter","https://example.com/deporte/noticia?utm_medium=rss",NOW));}
    @Test public void distinctArticlesDifferentUrlsAreNotAutomaticallyDuplicate(){assertFalse(same("Milan prepara el derbi","Peñarol se entrena el jueves"));}
    @Test public void sameEventTwoHeadlinesAreDuplicate(){assertTrue(same("Manchester City avanzó en los penales ante Brighton y clasificó a cuartos de final","Manchester City clasificó a cuartos de final tras avanzar por penales ante Brighton"));}
    @Test public void argentineNationalTeamBereavementDuplicate(){assertTrue(same("Luto en la selección de Argentina: muere Eulalia Marzetti, madre del seleccionador Lionel Scaloni","Lionel Scaloni está de luto: la selección Argentina confirmó el fallecimiento de la madre de su DT"));}
    @Test public void differentOpponentsMustNotMerge(){assertFalse(same("Manchester City venció al Brighton en la Copa de la Liga inglesa","Manchester City venció al Arsenal en la Copa de la Liga inglesa"));}
    @Test public void differentRivalsAfterAnteMustNotMerge(){assertFalse(same("Peñarol prepara partido ante Nacional con tres novedades","Peñarol prepara partido ante Danubio con tres novedades"));}
    @Test public void sameDerbyDifferentScoresMustNotMerge(){assertFalse(same("Peñarol derrota 2-1 a Nacional en el clásico de Uruguay","Peñarol derrota 3-1 a Nacional en el clásico de Uruguay"));}
    @Test public void differentScoringNotationIsNotDuplicate(){assertFalse(same("Liverpool ganó 2:1 ante Chelsea en la Premier League","Liverpool ganó 1:0 ante Chelsea en la Premier League"));}
    @Test public void transferAndInjuryMustNotMerge(){assertFalse(same("Chelsea confirma su fichaje del extremo argentino por 80 millones","Chelsea confirma la lesión del extremo argentino por dos meses"));}
    @Test public void differentEventsSameTeamMustNotMerge(){assertFalse(same("Barcelona prepara el clásico con Real Madrid el domingo","Barcelona confirma el regreso de un defensor para la Champions League"));}
    @Test public void oldIdenticalTitleNotDuplicate(){assertFalse(NewsDedupPolicy.sameStory("Peñarol vuelve a la Copa Libertadores","https://example.com/a",NOW,"Peñarol vuelve a la Copa Libertadores","https://example.com/a",NOW+49L*60L*60L*1000L));}
    @Test public void within48HoursMayBeDuplicate(){assertTrue(NewsDedupPolicy.sameStory("Peñarol vuelve a la Copa Libertadores","https://example.com/a",NOW,"Peñarol vuelve a la Copa Libertadores","https://example.com/a",NOW+47L*60L*60L*1000L));}
    @Test public void nullTitleNotDuplicate(){assertFalse(NewsDedupPolicy.sameStory(null,"https://example.com/a",NOW,"Peñarol ganó la final","https://example.com/a",NOW));}
    @Test public void emptyTitleNotDuplicate(){assertFalse(same(" ","PSG fichó un defensor"));}
    @Test public void unrelatedRivalReportsNotDuplicates(){assertFalse(same("Real Madrid anuncia su alineación para recibir al Atlético","Barcelona prepara su debut en la Champions con titulares"));}
    @Test public void breakingNewsWithOneSharedClubNotDuplicate(){assertFalse(same("Liverpool confirmó la llegada de un nuevo defensor","Liverpool enfrenta a Chelsea por la Premier League"));}
    @Test public void googleNewsSameUrlDifferentTeamsIsDuplicate(){assertTrue(NewsDedupPolicy.sameStory("Chelsea vs. Liverpool: previa del clásico","https://news.google.com/rss/articles/abc",NOW,"Liverpool visita Chelsea en la Premier League","https://news.google.com/rss/articles/abc",NOW));}
    @Test public void trackingQueryDoesNotHideDifferentArticlePaths(){assertFalse(NewsDedupPolicy.sameStory("Peñarol prepara partido ante Nacional","https://example.com/nota/123?utm_campaign=1",NOW,"Peñarol prepara partido ante Danubio","https://example.com/nota/456?utm_campaign=1",NOW));}
}
