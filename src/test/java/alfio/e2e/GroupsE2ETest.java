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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the groups admin screens migrated to Lit (frontend/admin/src/groups):
 * list, create, add a member, delete.
 */
class GroupsE2ETest extends BaseE2ETest {

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        // the event is not free of charge, so it needs at least one payment method
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Test
    void createListUpdateAndDeleteGroup() {
        adminConsole.login();
        adminConsole.openGroups();

        var name = "VIP group " + uniqueId();
        var member = "member-" + uniqueId() + "@example.com";
        adminConsole.createGroup(name, "Group created by the E2E test", member);
        assertTrue(adminConsole.groupListed(name), () -> "group " + name + " not found in the list");

        // add a second member from the edit page of the group
        var secondMember = "second-" + uniqueId() + "@example.com";
        adminConsole.addMemberToGroup(name, secondMember);
        adminConsole.openGroups();
        assertEquals(2, adminConsole.groupMembersCount(name), "the group should have 2 members");

        // import a third member from a CSV file, through the upload option of the members section
        var csvMember = "csv-" + uniqueId() + "@example.com";
        var csv = "\"email\",\"description\"\r\n\"" + csvMember + "\",\"from csv\"\r\n";
        adminConsole.importGroupMembersCsv(name, csv);
        adminConsole.openGroups();
        assertEquals(3, adminConsole.groupMembersCount(name), "the group should have 3 members after the CSV import");

        // delete the group, confirming the dialog
        adminConsole.openGroups();
        adminConsole.deleteGroup(name);
        adminConsole.openGroups();
        assertFalse(adminConsole.groupListed(name), () -> "group " + name + " still listed after the deletion");
    }
}
