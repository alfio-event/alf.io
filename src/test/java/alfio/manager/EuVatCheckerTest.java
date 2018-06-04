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
import ch.digitalfondue.vatchecker.EUVatCheckResponse;
import ch.digitalfondue.vatchecker.EUVatChecker;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EuVatCheckerTest {

    @Mock
    private EUVatChecker client;
    @Mock
    private ConfigurationManager configurationManager;

    @Before
    public void init() {
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(1, ConfigurationKeys.ENABLE_EU_VAT_DIRECTIVE)), anyBoolean())).thenReturn(true);
        when(configurationManager.getRequiredValue(Configuration.getSystemConfiguration(ConfigurationKeys.EU_COUNTRIES_LIST))).thenReturn("IE");
        when(configurationManager.getStringConfigValue(eq(Configuration.from(1, ConfigurationKeys.COUNTRY_OF_BUSINESS)), anyString())).thenReturn("IT");

    }

    @Test
    public void performCheckOK() {
        initResponse(true, "Test Corp.", "Address");
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
    public void performCheckKO() {
        initResponse(false, "------", "------");
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
    public void performCheckRequestFailed() {
        when(client.check(any(String.class), any(String.class))).thenThrow(new IllegalStateException("from test!"));
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
    }

    private void initResponse(boolean isValid, String name, String address) {
        EUVatCheckResponse resp = mock(EUVatCheckResponse.class);
        when(resp.isValid()).thenReturn(isValid);
        when(resp.getName()).thenReturn(name);
        when(resp.getAddress()).thenReturn(address);
        when(client.check(any(String.class), any(String.class))).thenReturn(resp);
    }
}