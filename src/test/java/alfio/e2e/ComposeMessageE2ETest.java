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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the editor of the custom message (frontend/admin/src/event/compose-message): with HTML e-mails (the default)
 * the message is written in a WYSIWYG editor, where the Mustache variables are shown as chips.
 */
class ComposeMessageE2ETest extends BaseE2ETest {

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Test
    void variablesAreShownAsChipsAndReplacedInThePreview() {
        adminConsole.login();
        var composer = adminConsole.composeMessage(slug, "Subject " + uniqueId(), "Hello {{fullName}}, welcome to {{eventName}}");

        assertEquals(List.of("{{fullName}}", "{{eventName}}"), adminConsole.messageVariableChips(composer, "en"));

        var preview = adminConsole.previewMessage(composer);
        assertTrue(preview.contains("Hello John Doe, welcome to"), () -> "the variables should be replaced in the preview: " + preview);
    }
}
