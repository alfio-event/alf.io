package alfio.manager;

import alfio.manager.system.ConfigurationManager;
import alfio.model.VatDetail;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import com.squareup.okhttp.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EuVatCheckerTest {

    private static final String OK_RESPONSE = "{\"isValid\": true,\"name\": \"Test Corp.\",\"address\": \"Address\"}";
    private static final String KO_RESPONSE = "{\"isValid\": false,\"name\": \"------\",\"address\": \"------\"}";

    @Mock
    private OkHttpClient client;
    @Mock
    private Call call;
    @Mock
    private ConfigurationManager configurationManager;

    @Before
    public void init() throws IOException {
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(1, ConfigurationKeys.ENABLE_EU_VAT_DIRECTIVE)), anyBoolean())).thenReturn(true);
        when(configurationManager.getStringConfigValue(eq(Configuration.from(1, ConfigurationKeys.COUNTRY_OF_BUSINESS)), anyString())).thenReturn("IT");
        when(configurationManager.getStringConfigValue(eq(Configuration.getSystemConfiguration(ConfigurationKeys.EU_VAT_API_ADDRESS)), anyString())).thenReturn("http://localhost:8080");
        when(client.newCall(any())).thenReturn(call);
    }

    @Test
    public void performCheckOK() throws IOException {
        initResponse(200, OK_RESPONSE);
        Optional<VatDetail> result = EuVatChecker.performCheck("1234", "IE", 1).apply(configurationManager, client);
        assertTrue(result.isPresent());
        VatDetail vatDetail = result.get();
        assertTrue(vatDetail.isValid());
        assertEquals("1234", vatDetail.getVatNr());
        assertEquals("IE", vatDetail.getCountry());
        assertTrue(vatDetail.isVatExempt());
        assertEquals("Test Corp.", vatDetail.getName());
        assertEquals("Address", vatDetail.getAddress());
    }

    @Test
    public void performCheckKO() throws IOException {
        initResponse(200, KO_RESPONSE);
        Optional<VatDetail> result = EuVatChecker.performCheck("1234", "IE", 1).apply(configurationManager, client);
        assertTrue(result.isPresent());
        VatDetail vatDetail = result.get();
        assertFalse(vatDetail.isValid());
        assertEquals("1234", vatDetail.getVatNr());
        assertEquals("IE", vatDetail.getCountry());
        assertFalse(vatDetail.isVatExempt());
        assertEquals("------", vatDetail.getName());
        assertEquals("------", vatDetail.getAddress());
    }

    @Test
    public void performCheckRequestFailed() throws IOException {
        initResponse(404, "");
        Optional<VatDetail> result = EuVatChecker.performCheck("1234", "IE", 1).apply(configurationManager, client);
        assertFalse(result.isPresent());
    }

    private void initResponse(int status, String body) throws IOException {
        Request request = new Request.Builder().url("http://localhost:8080").get().build();
        Response response = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .body(ResponseBody.create(MediaType.parse("application/json"), body))
            .code(status)
            .build();
        when(call.execute()).thenReturn(response);
    }
}