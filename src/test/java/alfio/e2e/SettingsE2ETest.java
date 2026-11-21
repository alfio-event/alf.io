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
package alfio.e2e;

import alfio.model.transaction.PaymentProxy;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the configuration pages migrated to Lit (frontend/admin/src/configuration):
 * settings of an event, validation of the system settings, customized translations.
 */
class SettingsE2ETest extends BaseE2ETest {

    private static final String REPLY_TO = "MAIL_REPLY_TO";

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        // the event is not free of charge, so it needs at least one payment method
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Test
    void changeAndResetEventSetting() {
        adminConsole.login();
        var replyTo = "reply-" + uniqueId() + "@example.com";

        adminConsole.saveEventSetting(slug, REPLY_TO, replyTo);
        assertEquals(replyTo, adminConsole.eventSettingValue(slug, REPLY_TO), "the setting should have been saved for the event");

        // an emptied setting is removed from the event: the value of the organization applies again
        adminConsole.saveEventSetting(slug, REPLY_TO, "");
        assertEquals("", adminConsole.eventSettingValue(slug, REPLY_TO), "the setting should have been removed from the event");
    }

    @Test
    void yesNoSettingShowsTheAppliedValue() {
        adminConsole.login();
        var key = "CHECK_IN_STATS";
        // not set for a new event: the first option shows the value of the organization, which then applies
        var initial = adminConsole.eventYesNoSetting(slug, key);
        assertTrue(initial.equals("|Same as the organization (Yes)") || initial.equals("|Same as the organization (No)"),
            () -> "expected the value of the organization, got: " + initial);

        adminConsole.saveEventYesNoSetting(slug, key, "false");
        assertTrue(adminConsole.eventYesNoSetting(slug, key).startsWith("false|"), "the event should have its own value");

        adminConsole.saveEventYesNoSetting(slug, key, "");
        assertTrue(adminConsole.eventYesNoSetting(slug, key).startsWith("|"), "the value should have been removed from the event");
    }

    @Test
    void choosingAnEventKeepsTheConfigurationPage() {
        adminConsole.login();
        var result = adminConsole.chooseEventInConfigurationPage();
        assertTrue(result.startsWith("#/configuration/organization/"), () -> "expected the configuration page, got: " + result);
        var parts = result.split("\\|");
        assertTrue(parts[0].endsWith("/event/" + parts[1].substring("event-".length())), () -> "the level select should show the event: " + result);
    }

    @Test
    void secretIsWriteOnly() {
        adminConsole.login();
        var key = "REVOLUT_API_KEY";
        var secret = "secret-" + uniqueId();
        var initial = adminConsole.organizationSecret(key, secret);
        // the test must not replace a key that the organization uses
        Assumptions.assumeTrue(initial.contains("removable=false"), () -> "the organization already has a " + key);

        adminConsole.saveOrganizationSecret(key, secret);
        assertEquals("value=|placeholder=Saved. Type a new value to replace it|removable=true|sent=false",
            adminConsole.organizationSecret(key, secret), "a saved secret is never sent back");

        adminConsole.removeOrganizationSecret(key);
        assertTrue(adminConsole.organizationSecret(key, secret).contains("removable=false"), "the secret should have been removed");
    }

    @Test
    void invalidSystemSettingBlocksSave() {
        adminConsole.login();
        var errors = adminConsole.saveInvalidSystemSetting("OPENID_CONFIGURATION_JSON", "{ not json");
        assertTrue(errors.contains("Please enter valid JSON"), () -> "expected a JSON error, got: " + errors);
    }

    @Test
    void customizeAndResetTranslation() {
        adminConsole.login();
        var text = "Customized by the E2E test " + uniqueId();
        var key = adminConsole.customizeFirstEventTranslation(slug, text);
        assertTrue(adminConsole.eventTranslationCustomized(slug, key, text), () -> "translation " + key + " not customized");

        adminConsole.resetEventTranslation(slug, key);
        assertFalse(adminConsole.eventTranslationCustomized(slug, key, text), () -> "translation " + key + " still customized");
    }
}
