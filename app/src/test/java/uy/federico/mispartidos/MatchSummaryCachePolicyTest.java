package uy.federico.mispartidos;

import org.junit.Test;
import static org.junit.Assert.*;

public class MatchSummaryCachePolicyTest {
    @Test public void confirmedTransmissionReplacesRicherRecentSummary(){
        assertTrue(MatchSummaryCachePolicy.shouldAccept(true,true,true,false,false,7,12,1,5,true,false));
    }
    @Test public void noInformationDoesNotReplaceSavedSummary(){
        assertFalse(MatchSummaryCachePolicy.shouldAccept(false,true,false,false,false,0,8,0,2,false,false));
    }
    @Test public void liveRefreshKeepsConfirmedSavedTransmission(){
        assertTrue(MatchSummaryCachePolicy.shouldAccept(true,true,true,false,false,10,10,2,2,true,true));
    }
}
