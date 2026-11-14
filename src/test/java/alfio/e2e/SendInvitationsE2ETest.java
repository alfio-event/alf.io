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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sends the invitation emails for the tokens of the access-restricted category from the admin console
 * (see frontend/admin/src/event/send-invitations), then clears the recipient of a sent code.
 */
class SendInvitationsE2ETest extends BaseE2ETest {

    // typed in the manual form: assignee, email (the language is the event's default)
    private static final String[][] MANUAL_INVITEES = {
        {"Doe John", "john.doe@example.org"},
        {"Roe Jane", "jane.roe@example.org"},
    };
    // with header, like the template: code (blank = automatic), assignee, email, language
    private static final String INVITATIONS_CSV = """
        code,assignee,email,language
        ,Simpson Bart,bart.simpson@example.org,en
        ,Simpson Lisa,lisa.simpson@example.org,en
        """;
    // without header, as the files generated before the template existed: still accepted
    private static final String LEGACY_INVITATIONS_CSV = """
        ,Doe Bob,bob.doe@example.org,en
        """;
    private static final int MANUAL = MANUAL_INVITEES.length;
    private static final int INVITATIONS = 2;
    private static final int TOTAL_SENT = MANUAL + INVITATIONS + 1;

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Override
    protected boolean withRestrictedCategory() {
        return true;
    }

    @Test
    void sendInvitationsAndClearRecipient() {
        adminConsole.login();
        // the template has a header with the column names, like the other CSV imports of the admin
        var template = adminConsole.downloadSendInvitationsTemplate(slug);
        assertTrue(template.startsWith("\uFEFF\"code\",\"assignee\",\"email\",\"language\""),
            "the template must start with the byte order mark and the quoted header, got: " + template);
        assertEquals(1, template.lines().count(), "the template contains only the header, got: " + template);
        var page = adminConsole.openSendInvitations(slug);
        // the invitees can be typed in one by one, picking a free code from the select: the form builds
        // the same CSV and sends it to the backend
        var manual = adminConsole.addInviteesManually(page, List.of(MANUAL_INVITEES));
        page = manual.page();
        // upload the template-based file: the backend assigns the blank codes, the rows are shown for review
        adminConsole.sendInvitations(page, INVITATIONS_CSV, INVITATIONS);
        // a file without header, as generated before the template existed, is still accepted
        adminConsole.sendInvitations(page, LEGACY_INVITATIONS_CSV, 1);
        // the sent invitations are listed in the history, with a code assigned to each
        var codes = adminConsole.sentInvitationCodes(page, TOTAL_SENT);
        assertEquals(TOTAL_SENT, codes.size());
        assertEquals(TOTAL_SENT, codes.stream().filter(code -> !code.isBlank()).distinct().count(),
            "a code has been assigned to each invitation: " + codes);
        assertTrue(codes.contains(manual.pickedCode()),
            "the code picked in the form has been assigned: " + manual.pickedCode() + " not in " + codes);
        // the recipient of a sent code can be cleared, after a confirmation
        adminConsole.clearFirstRecipient(page, TOTAL_SENT);
        var remaining = adminConsole.sentInvitationCodes(page, TOTAL_SENT - 1);
        assertEquals(codes.subList(1, TOTAL_SENT), remaining);
    }

    @Test
    void duplicateCodeIsRejectedOnTheSecondField() {
        adminConsole.login();
        var page = adminConsole.openSendInvitations(slug);
        // two invitees with the same code picked in both code selects, then Review
        page = adminConsole.addInviteesWithSameCode(page);
        // the second field gets the error, the first one stays valid
        var errors = adminConsole.awaitCodeSelectErrors(page, 1);
        assertEquals(Set.of("invitee-code-1"), errors.keySet(), "only the second code field must be invalid: " + errors);
        assertTrue(errors.get("invitee-code-1").contains("already"),
            "unexpected error message: " + errors.get("invitee-code-1"));
        // the form has refused to reach the preview
        assertFalse(adminConsole.previewIsShown(page), "the preview must not be shown when the same code is picked twice");
    }
}
