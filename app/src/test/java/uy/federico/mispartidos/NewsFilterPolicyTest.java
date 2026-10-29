package uy.federico.mispartidos;

import org.junit.Test;
import static org.junit.Assert.*;

public class NewsFilterPolicyTest {
    private void excluded(String title,String source){assertTrue(title,NewsFilterPolicy.isOffTopic(title,source));}
    private void included(String title,String source){assertFalse(title,NewsFilterPolicy.isOffTopic(title,source));}

    @Test public void penarolBasketScoreWithoutSportName(){excluded("Malvín 76-101 Peñarol: el Carbonero aplastó de visitante","El País");}
    @Test public void spanishUnder15(){excluded("Colombia se enfrenta a España en semifinales del Mundial Sub-15","PrimerTiempo");}
    @Test public void atleticoUnder16(){excluded("Resultado de Atlético de Madrid Sub16 3 - 1 Barcelona Sub16","365Scores");}
    @Test public void nationalUnder20(){excluded("La selección de Uruguay Sub 20 se prepara para el Mundial","AUF");}
    @Test public void englandU21(){excluded("England U-21 venció a España","BBC");}
    @Test public void youthAcademy(){excluded("Las novedades de la cantera de Barcelona","Sport");}
    @Test public void youthReserveLeague(){excluded("River Plate ganó la Liga de Reservas","Olé");}
    @Test public void basketballNamed(){excluded("Peñarol venció a Aguada en básquetbol","El País");}
    @Test public void rugbyNamed(){excluded("Francia prepara su debut en rugby","L'Équipe");}
    @Test public void volleyballNamed(){excluded("Argentina ganó en voleibol","TyC");}
    @Test public void bettingPreview(){excluded("AC Milan vs Bologna Pronóstico — Serie A","Apuestas Ganadas");}
    @Test public void bettingPublisher(){excluded("AC Milan vs Bologna: Serie A","Apuestas Ganadas");}
    @Test public void femaleFootball(){excluded("Chelsea femenino ganó el clásico","BBC");}
    @Test public void futsal(){excluded("Barcelona se coronó campeón en fútbol sala","Marca");}
    @Test public void seniorPenarolMatch(){included("Peñarol derrotó 2-1 a Nacional en el clásico","Ovación");}
    @Test public void seniorManchesterCityMatch(){included("Manchester City avanzó en los penales ante Brighton","ABC Color");}
    @Test public void seniorTransfer(){included("Chelsea ficha al delantero por 80 millones de euros","Diario AS");}
    @Test public void nationalTeamSenior(){included("España confirmó convocados para las eliminatorias","Marca");}
    @Test public void newsAboutBettingScandal(){included("Sancionaron a un jugador por apuestas ilegales","BBC");}
    @Test public void youthWordAsPartOfSeniorDebut(){included("Jugador debutó con el primer equipo de PSG","L'Équipe");}
}
