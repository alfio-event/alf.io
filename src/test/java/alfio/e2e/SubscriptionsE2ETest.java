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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the subscriptions admin screens migrated to Lit (frontend/admin/src/subscriptions):
 * list, create, edit and publish.
 * <p>
 * Needs Stripe in test mode: subscriptions can only be paid with an online payment provider
 * (bank transfer and on-site payment are not selectable), see requiredPaymentProxies().
 */
class SubscriptionsE2ETest extends BaseE2ETest {

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        return EnumSet.of(PaymentProxy.STRIPE);
    }

    @Test
    void createListAndPublishSubscription() {
        adminConsole.login();
        adminConsole.openSubscriptions();

        var title = "Season pass " + uniqueId();
        var subscriptionId = adminConsole.createSubscription(title);
        // back on the list, the new subscription is there
        adminConsole.openSubscriptions();
        assertTrue(adminConsole.subscriptionListed(title), () -> "subscription " + title + " not found in the list");

        // rename it from the edit page (the row of the list links there)
        var newTitle = "Renamed pass " + uniqueId();
        adminConsole.renameSubscription(title, subscriptionId, newTitle);
        // still on the edit page: publish it
        adminConsole.publishSubscription(subscriptionId);

        adminConsole.openSubscriptions();
        assertTrue(adminConsole.subscriptionListed(newTitle), () -> "subscription " + newTitle + " not found in the list after the update");
    }
}
