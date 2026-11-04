package uy.federico.mispartidos;

import org.junit.Test;
import static org.junit.Assert.*;

public class NewsRelevancePolicyTest {
    private static final long NOW=1_800_000_000_000L;
    private int rank(String title){return NewsRelevancePolicy.rank(title,"Medio",NOW-3600_000L,NOW);}

    @Test public void penalShootoutRanksAboveStadiumPhones(){assertTrue(rank("Manchester City avanzó por penales ante Brighton")>rank("Adiós a mirar el móvil en el Signal Iduna Park: una iniciativa del Dortmund"));}
    @Test public void transferRanksAboveFansStory(){assertTrue(rank("Chelsea confirma fichaje de delantero")>rank("Los hinchas visitan las gradas del estadio"));}
    @Test public void matchResultHighRelevance(){assertTrue(rank("Peñarol ganó 2-1 a Nacional en el clásico")>rank("Borussia Dortmund presenta una iniciativa para aficionados"));}
    @Test public void injuryHighRelevance(){assertTrue(rank("Luis Suárez sufrió una lesión muscular")>rank("Nueva campaña publicitaria de un club"));}
    @Test public void penaltyArticleNotPeripheral(){assertFalse(NewsRelevancePolicy.clearlyPeripheral("Peñarol venció en los penales en su estadio"));}
    @Test public void stadiumPhoneArticleIsPeripheral(){assertTrue(NewsRelevancePolicy.clearlyPeripheral("Adiós a mirar el móvil en el Signal Iduna Park: una iniciativa del Borussia Dortmund para sus aficionados"));}
    @Test public void museumTourPeripheral(){assertTrue(NewsRelevancePolicy.clearlyPeripheral("El club organiza una visita guiada a su museo"));}
    @Test public void injuriesReportedAtStadiumAllowed(){assertFalse(NewsRelevancePolicy.clearlyPeripheral("Peñarol confirma una lesión tras partido disputado en el estadio"));}
    @Test public void transfersWithClubShopAllowed(){assertFalse(NewsRelevancePolicy.clearlyPeripheral("Barcelona anuncia fichaje de delantero en la tienda oficial"));}
    @Test public void sportContextGoal(){assertTrue(NewsRelevancePolicy.footballContext("PSG gana 3-0 con dos goles de Dembélé"));}
    @Test public void sportContextNationalTeam(){assertTrue(NewsRelevancePolicy.footballContext("Argentina confirmó convocados para las eliminatorias"));}
    @Test public void trivialStadiumInitiativeNotFootball(){assertFalse(NewsRelevancePolicy.footballContext("Adiós al móvil en Signal Iduna Park"));}
    @Test public void italianMilanTransferAllowed(){assertTrue(NewsRelevancePolicy.italianClubMatch("Milan","Milan confirma el fichaje de un arquero"));}
    @Test public void milanCityUnrelatedRejected(){assertFalse(NewsRelevancePolicy.italianClubMatch("Milan","La ciudad de Milán organiza un festival de arte"));}
    @Test public void interMiamiNotInterMilan(){assertFalse(NewsRelevancePolicy.italianClubMatch("Inter","Inter Miami enfrenta al Orlando City por la MLS"));}
    @Test public void interMilanFootballAllowed(){assertTrue(NewsRelevancePolicy.italianClubMatch("Inter","Inter vence 2-0 al Napoli en la Serie A"));}
    @Test public void interCityNotRelevant(){assertFalse(NewsRelevancePolicy.italianClubMatch("Inter","En la ciudad de Inter se realizó una exposición"));}
    @Test public void liverpoolPremierAllowed(){assertTrue(NewsRelevancePolicy.englishLiverpoolMatch("Liverpool venció al Chelsea en la Premier League"));}
    @Test public void liverpoolUruguayNotEnglish(){assertFalse(NewsRelevancePolicy.englishLiverpoolMatch("Liverpool de Montevideo goleó en el Clausura uruguayo"));}
    @Test public void newestStoryScoresHigherThanWeekOldOnTie(){assertTrue(NewsRelevancePolicy.rank("Peñarol prepara el partido ante Nacional","Medio",NOW-60_000,NOW)>NewsRelevancePolicy.rank("Peñarol prepara el partido ante Nacional","Medio",NOW-7L*24L*60L*60L*1000L,NOW));}
    @Test public void clickbaitGetsPenalty(){assertTrue(rank("Chelsea fichó al delantero por 80 millones")>rank("Chelsea, no vas a creer lo que pasó con el delantero"));}
    @Test public void tvOnlyPreviewGetsPenalty(){assertTrue(rank("Peñarol ganó por penales ante Liverpool")>rank("Peñarol: dónde ver en vivo y a qué hora juega"));}
    @Test public void unknownPublisherDoesNotCrash(){assertTrue(NewsRelevancePolicy.rank("PSG gana en la Champions League",null,NOW,NOW)>0);}
    @Test public void emptyHeadlinePeripheral(){assertTrue(NewsRelevancePolicy.clearlyPeripheral(""));}
}
