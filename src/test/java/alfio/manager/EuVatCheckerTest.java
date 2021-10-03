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

import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.EventAndOrganizationId;
import alfio.model.VatDetail;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.system.ConfigurationKeys;
import ch.digitalfondue.vatchecker.EUVatCheckResponse;
import ch.digitalfondue.vatchecker.EUVatChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static alfio.manager.testSupport.MaybeConfigurationBuilder.existing;
import static alfio.manager.testSupport.MaybeConfigurationBuilder.missing;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class EuVatCheckerTest {

    private EUVatChecker client;
    private ConfigurationManager configurationManager;
    private EventAndOrganizationId eventAndOrganizationId;

    @BeforeEach
    public void init() {
        client = mock(EUVatChecker.class);
        configurationManager = mock(ConfigurationManager.class);
        eventAndOrganizationId = mock(EventAndOrganizationId.class);
        ConfigurationLevel cl = ConfigurationLevel.event(eventAndOrganizationId);
        when(eventAndOrganizationId.getConfigurationLevel()).thenReturn(cl);
        when(configurationManager.getFor(eq(ConfigurationKeys.ENABLE_EU_VAT_DIRECTIVE), any(ConfigurationLevel.class)))
            .thenReturn(existing(ConfigurationKeys.ENABLE_EU_VAT_DIRECTIVE, "true"));
        when(configurationManager.getFor(eq(ConfigurationKeys.ENABLE_VIES_VALIDATION), any(ConfigurationLevel.class)))
            .thenReturn(existing(ConfigurationKeys.ENABLE_VIES_VALIDATION, "true"));
        when(configurationManager.getForSystem(ConfigurationKeys.EU_COUNTRIES_LIST))
            .thenReturn(existing(ConfigurationKeys.EU_COUNTRIES_LIST, "IE"));
        when(configurationManager.getFor(eq(ConfigurationKeys.COUNTRY_OF_BUSINESS), any(ConfigurationLevel.class)))
            .thenReturn(existing(ConfigurationKeys.COUNTRY_OF_BUSINESS, "IT"));
        when(configurationManager.getFor(eq(EnumSet.of(ConfigurationKeys.ENABLE_EU_VAT_DIRECTIVE, ConfigurationKeys.COUNTRY_OF_BUSINESS, ConfigurationKeys.ENABLE_REVERSE_CHARGE_ONLINE, ConfigurationKeys.ENABLE_REVERSE_CHARGE_IN_PERSON)), any()))
            .thenReturn(Map.of(
                ConfigurationKeys.ENABLE_EU_VAT_DIRECTIVE, existing(ConfigurationKeys.ENABLE_EU_VAT_DIRECTIVE, "true"),
                ConfigurationKeys.COUNTRY_OF_BUSINESS, existing(ConfigurationKeys.COUNTRY_OF_BUSINESS, "IT"),
                ConfigurationKeys.ENABLE_REVERSE_CHARGE_ONLINE, missing(ConfigurationKeys.ENABLE_REVERSE_CHARGE_ONLINE),
                ConfigurationKeys.ENABLE_REVERSE_CHARGE_IN_PERSON, missing(ConfigurationKeys.ENABLE_REVERSE_CHARGE_IN_PERSON)
            ));
    }

    @Test
    public void performCheckOK() {
        initResponse(true, "Test Corp.", "Address");
        Optional<VatDetail> result = EuVatChecker.performCheck("1234", "IE", eventAndOrganizationId).apply(configurationManager, client);
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
        Optional<VatDetail> result = EuVatChecker.performCheck("12345", "IE", eventAndOrganizationId).apply(configurationManager, client);
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

    @Test
    public void performCheckRequestFailed() {
        when(client.check(any(String.class), any(String.class))).thenThrow(new IllegalStateException("from test!"));
        assertThrows(IllegalStateException.class, () -> EuVatChecker.performCheck("1234", "IE", eventAndOrganizationId).apply(configurationManager, client));
    }

    @Test
    public void testForeignBusinessVATApplied() {
        when(configurationManager.getFor(eq(ConfigurationKeys.APPLY_VAT_FOREIGN_BUSINESS), any(ConfigurationLevel.class))).thenReturn(existing(ConfigurationKeys.APPLY_VAT_FOREIGN_BUSINESS, "true"));
        Optional<VatDetail> result = EuVatChecker.performCheck("1234", "UK", eventAndOrganizationId).apply(configurationManager, client);
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
        when(configurationManager.getFor(eq(ConfigurationKeys.APPLY_VAT_FOREIGN_BUSINESS), any(ConfigurationLevel.class))).thenReturn(existing(ConfigurationKeys.APPLY_VAT_FOREIGN_BUSINESS, "false"));
        Optional<VatDetail> result = EuVatChecker.performCheck("1234", "UK", eventAndOrganizationId).apply(configurationManager, client);
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
        when(configurationManager.getFor(eq(ConfigurationKeys.ENABLE_VIES_VALIDATION), any(ConfigurationLevel.class))).thenReturn(existing(ConfigurationKeys.ENABLE_VIES_VALIDATION, "true"));
        when(configurationManager.getFor(eq(ConfigurationKeys.APPLY_VAT_FOREIGN_BUSINESS), any(ConfigurationLevel.class))).thenReturn(existing(ConfigurationKeys.APPLY_VAT_FOREIGN_BUSINESS, "false"));
        Optional<VatDetail> result = EuVatChecker.performCheck("1234", "UK", eventAndOrganizationId).apply(configurationManager, client);
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
        when(configurationManager.getFor(eq(ConfigurationKeys.ENABLE_VIES_VALIDATION), any(ConfigurationLevel.class))).thenReturn(existing(ConfigurationKeys.ENABLE_VIES_VALIDATION, "false"));
        Optional<VatDetail> result = EuVatChecker.performCheck("1234", "IE", eventAndOrganizationId).apply(configurationManager, client);
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