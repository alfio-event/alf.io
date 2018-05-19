/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager;

import alfio.manager.system.ConfigurationManager;
import alfio.model.VatDetail;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import okhttp3.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

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
    public void init() {
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(1, ConfigurationKeys.ENABLE_EU_VAT_DIRECTIVE)), anyBoolean())).thenReturn(true);
        when(configurationManager.getRequiredValue(Configuration.getSystemConfiguration(ConfigurationKeys.EU_COUNTRIES_LIST))).thenReturn("IE");
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

    @Test
    public void testForeignBusinessVATApplied() {
        when(configurationManager.getBooleanConfigValue(Configuration.from(1, ConfigurationKeys.APPLY_VAT_FOREIGN_BUSINESS), true)).thenReturn(true);
        Optional<VatDetail> result = EuVatChecker.performCheck("1234", "UK", 1).apply(configurationManager, client);
        assertTrue(result.isPresent());
        VatDetail vatDetail = result.get();
        assertTrue(vatDetail.isValid());
        assertFalse(vatDetail.isVatExempt());
        assertEquals("1234", vatDetail.getVatNr());
        assertEquals("UK", vatDetail.getCountry());
        verify(client, never()).newCall(any());
    }

    @Test
    public void testForeignBusinessVATNotApplied() {
        when(configurationManager.getBooleanConfigValue(Configuration.from(1, ConfigurationKeys.APPLY_VAT_FOREIGN_BUSINESS), true)).thenReturn(false);
        Optional<VatDetail> result = EuVatChecker.performCheck("1234", "UK", 1).apply(configurationManager, client);
        assertTrue(result.isPresent());
        VatDetail vatDetail = result.get();
        assertTrue(vatDetail.isValid());
        assertTrue(vatDetail.isVatExempt());
        assertEquals("1234", vatDetail.getVatNr());
        assertEquals("UK", vatDetail.getCountry());
        verify(client, never()).newCall(any());
    }

    private void initResponse(int status, String body) throws IOException {
        Request request = new Request.Builder().url("http://localhost:8080").get().build();
        Response response = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .body(ResponseBody.create(MediaType.parse("application/json"), body))
            .code(status)
            .message("" + status)
            .build();
        when(call.execute()).thenReturn(response);
    }
}