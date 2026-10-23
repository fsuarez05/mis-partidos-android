package uy.federico.mispartidos;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class TransmissionInfoTest {
    @Test public void acceptsConfirmedUruguayTransmission() throws Exception {
        TransmissionInfo info=TransmissionInfo.fromJson(new JSONObject("{\"pais\":\"Uruguay\",\"confirmada\":true,\"canales\":[\"ESPN\"],\"streaming\":[\"Disney+ Premium\"]}"));
        assertTrue(info.confirmed);assertEquals("ESPN / Disney+ Premium",info.text);
    }
    @Test public void rejectsTransmissionConfirmedOnlyForAnotherCountry() throws Exception {
        TransmissionInfo info=TransmissionInfo.fromJson(new JSONObject("{\"pais\":\"Argentina\",\"confirmada\":true,\"canales\":[\"ESPN\"]}"));
        assertFalse(info.confirmed);assertEquals("",info.text);
    }
    @Test public void rejectsIncompleteGeminiTransmission() throws Exception {
        assertFalse(TransmissionInfo.fromJson(new JSONObject("{\"pais\":\"Uruguay\",\"confirmada\":true}")).confirmed);
        assertFalse(TransmissionInfo.fromJson(null).confirmed);
    }
}
