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

import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EuVatCheckerTest {

    private EUVatChecker client;
    private ConfigurationManager configurationManager;

    @Before
    public void init() {
        client = mock(EUVatChecker.class);
        configurationManager = mock(ConfigurationManager.class);
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(1, ConfigurationKeys.ENABLE_EU_VAT_DIRECTIVE)), anyBoolean())).thenReturn(true);
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(1, ConfigurationKeys.ENABLE_VIES_VALIDATION)), anyBoolean())).thenReturn(true);
        when(configurationManager.getRequiredValue(Configuration.getSystemConfiguration(ConfigurationKeys.EU_COUNTRIES_LIST))).thenReturn("IE");
        when(configurationManager.getStringConfigValue(eq(Configuration.from(1, ConfigurationKeys.COUNTRY_OF_BUSINESS)), isNull())).thenReturn("IT");
    }

    @Test
    public void performCheckOK() {
        initResponse(true, "Test Corp.", "Address");
        Optional<VatDetail> result = EuVatChecker.performCheck("1234", "IE", 1).apply(configurationManager, client);
        assertTrue(result.isPresent());
        VatDetail vatDetail = result.get();
        assertTrue(vatDetail.isValid());
        assertEquals(VatDetail.Type.VIES, vatDetail.getType());
        assertEquals("1234", vatDetail.getVatNr());
        assertEquals("IE", vatDetail.getCountry());
        assertTrue(vatDetail.isVatExempt());
        assertEquals("Test Corp.", vatDetail.getName());
        assertEquals("Address", vatDetail.getAddress());
    }

    @Test
    public void performCheckKO() {
        initResponse(false, "------", "------");
        Optional<VatDetail> result = EuVatChecker.performCheck("12345", "IE", 1).apply(configurationManager, client);
        assertTrue(result.isPresent());
        VatDetail vatDetail = result.get();
        assertFalse(vatDetail.isValid());
        assertEquals(VatDetail.Type.VIES, vatDetail.getType());
        assertEquals("12345", vatDetail.getVatNr());
        assertEquals("IE", vatDetail.getCountry());
        assertFalse(vatDetail.isVatExempt());
        assertEquals("------", vatDetail.getName());
        assertEquals("------", vatDetail.getAddress());
    }

    @Test(expected = IllegalStateException.class)
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
        assertEquals(VatDetail.Type.EXTRA_EU, vatDetail.getType());
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
        assertEquals(VatDetail.Type.EXTRA_EU, vatDetail.getType());
        assertTrue(vatDetail.isVatExempt());
        assertEquals("1234", vatDetail.getVatNr());
        assertEquals("UK", vatDetail.getCountry());
    }

    @Test
    public void testForeignBusinessVATNotAppliedValidationDisabled() {
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(1, ConfigurationKeys.ENABLE_VIES_VALIDATION)), anyBoolean())).thenReturn(false);
        when(configurationManager.getBooleanConfigValue(Configuration.from(1, ConfigurationKeys.APPLY_VAT_FOREIGN_BUSINESS), true)).thenReturn(false);
        Optional<VatDetail> result = EuVatChecker.performCheck("1234", "UK", 1).apply(configurationManager, client);
        assertTrue(result.isPresent());
        VatDetail vatDetail = result.get();
        assertEquals(VatDetail.Type.EXTRA_EU, vatDetail.getType());
        assertTrue(vatDetail.isValid());
        assertTrue(vatDetail.isVatExempt());
        assertEquals("1234", vatDetail.getVatNr());
        assertEquals("UK", vatDetail.getCountry());
    }

    @Test
    public void testEUBusinessVATNotAppliedValidationDisabled() {
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(1, ConfigurationKeys.ENABLE_VIES_VALIDATION)), anyBoolean())).thenReturn(false);
        Optional<VatDetail> result = EuVatChecker.performCheck("1234", "IE", 1).apply(configurationManager, client);
        assertTrue(result.isPresent());
        VatDetail vatDetail = result.get();
        assertTrue(vatDetail.isValid());
        assertTrue(vatDetail.isVatExempt());
        assertEquals(VatDetail.Type.SKIPPED, vatDetail.getType());
        assertEquals("1234", vatDetail.getVatNr());
        assertEquals("IE", vatDetail.getCountry());
    }

    private void initResponse(boolean isValid, String name, String address) {
        EUVatCheckResponse resp = mock(EUVatCheckResponse.class);
        when(resp.isValid()).thenReturn(isValid);
        when(resp.getName()).thenReturn(name);
        when(resp.getAddress()).thenReturn(address);
        when(client.check(any(String.class), any(String.class))).thenReturn(resp);
    }
}