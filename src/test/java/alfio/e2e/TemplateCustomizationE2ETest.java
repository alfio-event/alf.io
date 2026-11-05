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
 * The overridable templates of an event are customized from the "customize templates" page:
 * the test saves an override of the confirmation e-mail for the English locale, checks that
 * it survives a reload of the page, and deletes it again.
 */
class TemplateCustomizationE2ETest extends BaseE2ETest {

    // a text template, so that its preview is rendered inline instead of downloaded
    private static final String TEMPLATE = "CONFIRMATION_EMAIL";
    private static final String LOCALE = "en";

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        // the event is not free of charge, so it needs at least one payment method
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Test
    void customizeEventTemplate() {
        var marker = "E2E-TEMPLATE-" + uniqueId();
        adminConsole.login();
        adminConsole.openTemplateEdit(slug, TEMPLATE);
        adminConsole.editTemplateAndSave(TEMPLATE, LOCALE, marker);
        // the override must survive a reload of the page
        adminConsole.openTemplateEdit(slug, TEMPLATE);
        adminConsole.verifyTemplateContent(LOCALE, marker);
        // delete the override: the default template is used again
        adminConsole.deleteTemplateOverride(LOCALE);
    }
}
