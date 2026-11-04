package uy.federico.mispartidos;

import org.junit.Test;
import static org.junit.Assert.*;

/** Realistic RSS headlines that protect football coverage from club/country homonyms. */
public class NewsTeamPolicyTest {
    private void yes(String team,boolean national,String headline){
        assertTrue(team+": "+headline,NewsTeamPolicy.matches(team,national,headline));
    }
    private void no(String team,boolean national,String headline){
        assertFalse(team+": "+headline,NewsTeamPolicy.matches(team,national,headline));
    }

    @Test public void englishLiverpoolPremier(){yes("Liverpool",false,"Liverpool goleó a Chelsea en la Premier League");}
    @Test public void englishLiverpoolSimpleMatch(){yes("Liverpool",false,"Liverpool ganó 2-0 a Chelsea");}
    @Test public void uruguayanLiverpoolExplicit(){yes("Liverpool (Uruguay)",false,"Liverpool de Montevideo venció a Peñarol");}
    @Test public void uruguayanLiverpoolOpponent(){yes("Liverpool (Uruguay)",false,"Liverpool ganó 2-0 a Danubio");}
    @Test public void englishLiverpoolRejectsUruguay(){no("Liverpool",false,"Liverpool de Montevideo goleó a Peñarol");}
    @Test public void uruguayanLiverpoolRejectsEngland(){no("Liverpool (Uruguay)",false,"Liverpool venció 3-1 al Chelsea en Anfield");}
    @Test public void uruguayanLiverpoolWithNational(){yes("Liverpool (Uruguay)",false,"Liverpool venció a Nacional por el Torneo Clausura");}
    @Test public void liverpoolNeverMatchesUnrelatedTown(){no("Liverpool",false,"Liverpool inaugura un centro cultural");}

    @Test public void interMilanMatch(){yes("Inter",false,"Inter venció al Napoli 2-0 en la Serie A");}
    @Test public void interMilanoExplicit(){yes("Inter",false,"Inter Milan fichó a un delantero");}
    @Test public void internazionaleAsAlias(){yes("Inter",false,"Internazionale venció a la Juventus");}
    @Test public void interNotInterMiami(){no("Inter",false,"Inter Miami venció al Orlando City en la MLS");}
    @Test public void interNotInternacionalPorto(){no("Inter",false,"Internacional de Porto Alegre ganó en Brasil");}
    @Test public void interNotInterTurku(){no("Inter",false,"Inter Turku ganó en la liga finlandesa");}
    @Test public void interNotAnInteriorSubstring(){no("Inter",false,"El interior del país celebró el partido");}
    @Test public void acMilanTransfer(){yes("Milan",false,"AC Milan confirma el fichaje de un delantero");}
    @Test public void milanGoals(){yes("Milan",false,"Milan goleó al Napoli");}
    @Test public void milanNotInterMilan(){no("Milan",false,"Inter Milan ganó ante la Lazio en la Serie A");}
    @Test public void derbyBelongsToBothMilanClubs(){yes("Milan",false,"Inter Milan derrotó al AC Milan en el derbi");}
    @Test public void interDerbyBelongsToBoth(){yes("Inter",false,"Milan cayó ante Inter en la Serie A");}
    @Test public void milanNotFashion(){no("Milan",false,"Milán presenta su semana de la moda");}

    @Test public void nationalUruguayClub(){yes("Nacional",false,"Nacional de Montevideo goleó a Danubio");}
    @Test public void nationalUruguayDerby(){yes("Nacional",false,"Nacional venció a Peñarol en el clásico");}
    @Test public void nationalUruguayLibertadores(){yes("Nacional",false,"Nacional clasificó a la Copa Libertadores");}
    @Test public void nationalClubNotAtleticoNacional(){no("Nacional",false,"Atlético Nacional de Colombia ganó ante Millonarios");}
    @Test public void nationalClubNotNacionalPotosi(){no("Nacional",false,"Nacional Potosí ganó el partido en Bolivia");}
    @Test public void nationalClubNotSelection(){no("Nacional",false,"La selección nacional de Uruguay ganó un amistoso");}

    @Test public void argentineRiverPlate(){yes("River Plate",false,"River Plate goleó a Boca Juniors");}
    @Test public void argentineRiverGallardo(){yes("River Plate",false,"Gallardo prepara a River para la Copa Argentina");}
    @Test public void riverNotMontevideo(){no("River Plate",false,"River Plate de Montevideo ganó el Clausura uruguayo");}
    @Test public void argentineRacing(){yes("Racing Club",false,"Racing de Avellaneda clasificó a semifinales");}
    @Test public void racingAgainstBoca(){yes("Racing Club",false,"Racing venció a Boca Juniors en la Copa Argentina");}
    @Test public void racingNotMontevideo(){no("Racing Club",false,"Racing de Montevideo ganó 1-0 en el fútbol uruguayo");}
    @Test public void racingNotSantander(){no("Racing Club",false,"Racing Santander avanzó en la Copa del Rey");}
    @Test public void independienteNotIndependienteDelValle(){no("Independiente",false,"Independiente del Valle ganó la Copa");}

    @Test public void barcelonaSpainFootball(){yes("Barcelona",false,"Barcelona goleó al Atlético de Madrid");}
    @Test public void barcaAlias(){yes("Barcelona",false,"El Barça fichó a un delantero");}
    @Test public void barcelonaScEcuadorIsDifferent(){no("Barcelona",false,"Barcelona SC derrotó a Emelec en Ecuador");}
    @Test public void barcelonaCityNewsNotFootball(){no("Barcelona",false,"Barcelona inaugura un museo turístico");}
    @Test public void manCityAlias(){yes("Manchester City",false,"Man City fichó a un delantero");}
    @Test public void manUnitedAlias(){yes("Manchester United",false,"Man Utd ganó en la Premier League");}
    @Test public void atleticoShortName(){yes("Atlético de Madrid",false,"Atleti ganó 2-1 a la Real Sociedad");}
    @Test public void psgFullName(){yes("PSG",false,"Paris Saint-Germain goleó al Marsella");}
    @Test public void riverDifferentFromRiverUruguay(){no("River Plate",false,"River Plate Uruguay goleó a Cerro");}

    @Test public void uruguayNationalTeamWin(){yes("Uruguay",true,"Uruguay venció 2-1 a Argentina");}
    @Test public void uruguayNationalEliminatorias(){yes("Uruguay",true,"Uruguay confirmó convocados para las Eliminatorias");}
    @Test public void uruguayNationalWorldCup(){yes("Uruguay",true,"Uruguay ganó el Mundial de fútbol");}
    @Test public void uruguayClubNotNationalTeam(){no("Uruguay",true,"Uruguay Montevideo venció a Danubio por el Ascenso");}
    @Test public void argentinaNationalWorldCup(){yes("Argentina",true,"La selección Argentina confirmó el plantel para el Mundial");}
    @Test public void argentinaCountryNotNational(){no("Argentina",true,"En Argentina, Boca Juniors anunció un fichaje");}
    @Test public void brasilNationalVsUruguay(){yes("Brasil",true,"Brasil enfrentará a Uruguay por Eliminatorias");}
    @Test public void brasilClubNotNational(){no("Brasil",true,"Brasil de Pelotas venció a Internacional en la Serie B");}
    @Test public void franceNationalVsNorway(){yes("Francia",true,"Francia venció a Noruega");}
    @Test public void franceCountryNotPSG(){no("Francia",true,"En Francia PSG anunció su fichaje en la Ligue 1");}
    @Test public void norwayNationalTeam(){yes("Noruega",true,"La selección de Noruega anunció convocados");}
    @Test public void japanNationalTeam(){yes("Japón",true,"Japón ganó ante Brasil");}
    @Test public void boliviaNationalTeam(){yes("Bolivia",true,"Bolivia enfrentará a Uruguay");}
    @Test public void indiaNationalWorldCup(){yes("India",true,"India prepara su convocatoria para el Mundial");}
    @Test public void indiaSubstringNotAName(){no("India",true,"El técnico indica que habrá más cambios");}
    @Test public void nationalNameRegistry(){assertTrue(NewsTeamPolicy.isNationalName("Uruguay"));assertFalse(NewsTeamPolicy.isNationalName("Nacional"));}
    @Test public void cacheInterMilanOnlyInter(){
        assertEquals("Inter",String.join("|",NewsTeamPolicy.verifiedTeams(
                "Inter Milan venció al Torino en Serie A","Inter | Milan")));
    }
    @Test public void cacheDerbyRetainsBothMilanClubs(){
        assertEquals("Inter|Milan",String.join("|",NewsTeamPolicy.verifiedTeams(
                "Inter Milan derrotó 2-0 al AC Milan","Inter | Milan")));
    }
    @Test public void cacheEnglishLiverpoolNotUruguayan(){
        assertEquals("Liverpool",String.join("|",NewsTeamPolicy.verifiedTeams(
                "Liverpool venció a Chelsea en Anfield","Liverpool (Uruguay) | Liverpool")));
    }
    @Test public void cacheUruguayanLiverpoolNotEnglish(){
        assertEquals("Liverpool (Uruguay)",String.join("|",NewsTeamPolicy.verifiedTeams(
                "Liverpool de Montevideo venció a Danubio","Liverpool (Uruguay) | Liverpool")));
    }
    @Test public void cacheUruguayArgentinaInternationalMatch(){
        assertEquals("Uruguay|Argentina",String.join("|",NewsTeamPolicy.verifiedTeams(
                "Uruguay ganó a Argentina en las Eliminatorias","Uruguay | Argentina")));
    }
    @Test public void cacheNationalClubNotUruguaySelection(){
        assertEquals("Nacional",String.join("|",NewsTeamPolicy.verifiedTeams(
                "Nacional venció a Peñarol por el Clausura","Nacional | Uruguay")));
    }
    @Test public void cacheSelectionNotNationalClub(){
        assertEquals("Uruguay",String.join("|",NewsTeamPolicy.verifiedTeams(
                "La selección de Uruguay anuncia convocados al Mundial","Nacional | Uruguay")));
    }
    @Test public void cachePeñarolNacionalDerbyBoth(){
        assertEquals("Peñarol|Nacional",String.join("|",NewsTeamPolicy.verifiedTeams(
                "Peñarol ganó el clásico ante Nacional","Peñarol | Nacional")));
    }
    @Test public void cacheBarcelonaSportingEcuadorExcluded(){
        assertTrue(NewsTeamPolicy.verifiedTeams(
                "Barcelona SC goleó al Emelec en Ecuador","Barcelona").isEmpty());
    }
    @Test public void cacheEmptyTeams(){
        assertTrue(NewsTeamPolicy.verifiedTeams("Peñarol ganó 3-1",null).isEmpty());
    }
    @Test public void cacheDuplicateTeamLabelsDeduplicated(){
        assertEquals(1,NewsTeamPolicy.verifiedTeams("Peñarol ganó 3-1","Peñarol | Peñarol").size());
    }
    @Test public void cacheUnrelatedArticleCannotKeepFavoriteLabels(){
        assertTrue(NewsTeamPolicy.verifiedTeams(
                "Juventus confirma fichaje de delantero","PSG | Uruguay | Manchester City").isEmpty());
    }
    @Test public void emptyHeadlineRejected(){no("Liverpool",false,"");}
}
