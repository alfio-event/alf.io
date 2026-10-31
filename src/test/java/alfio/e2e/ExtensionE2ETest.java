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
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

/**
 * The extension scripts can be managed from the "Extension" menu of the admin console:
 * the test creates an extension, edits it, checks the logs page and deletes it.
 */
class ExtensionE2ETest extends BaseE2ETest {

    // the script must define getScriptMetadata() with a display name and at least one event
    private static final String SCRIPT = """
        function getScriptMetadata() {
            return {
                displayName: 'E2E extension',
                async: false,
                events: ['RESERVATION_CONFIRMED']
            };
        }

        function executeScript(scriptEvent) {
            return null;
        }
        """;

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        // the event is not free of charge, so it needs at least one payment method
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Test
    void manageExtension() {
        var name = "e2e" + uniqueId();
        adminConsole.login();
        try {
            adminConsole.createExtension(name, SCRIPT);
            adminConsole.verifyExtension(name, true);
            // the organizer edits the extension: the script must survive the round trip, and the extension becomes disabled
            adminConsole.editExtension(name, false, "E2E extension");
            adminConsole.verifyExtension(name, false);
            adminConsole.verifyExtensionLogs();
            adminConsole.deleteExtension(name);
        } finally {
            // best effort, e.g. when the test fails midway: don't hide the original failure
            try {
                adminConsole.deleteExtensionIfPresent(name);
            } catch (Exception cleanupError) {
                // ignore: the extension is unique to this run (name contains the event slug)
            }
        }
    }
}