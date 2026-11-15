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
 * Covers the event detail page migrated to Lit (frontend/admin/src/event/event-detail):
 * the headline, the statistics graphs, the categories list, and the edit flow, which reuses
 * the event creation form (frontend/admin/src/event/event-create).
 */
class EventDetailE2ETest extends BaseE2ETest {

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        // the event is not free of charge, so it needs at least one payment method
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Test
    void eventDetailShowsGraphsCategoriesAndEditsTheEvent() {
        adminConsole.login();
        adminConsole.verifyEventDetail(slug, "Event Name", CATEGORY_NAME);

        // the edit button opens the event creation form in edit mode; saving lands back on the detail page
        var newName = "Event Name " + uniqueId();
        adminConsole.editEventNameFromDetail(slug, newName);
        adminConsole.verifyEventDetail(slug, newName, CATEGORY_NAME);
    }
}
