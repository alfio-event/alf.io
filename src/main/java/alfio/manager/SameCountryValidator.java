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
import alfio.model.Event;
import alfio.model.EventAndOrganizationId;
import alfio.model.PurchaseContext;
import alfio.model.VatDetail;
import ch.digitalfondue.vatchecker.EUVatCheckResponse;
import ch.digitalfondue.vatchecker.EUVatChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.util.function.Predicate;

@RequiredArgsConstructor
@Log4j2
public class SameCountryValidator implements Predicate<String> {

    private final ConfigurationManager configurationManager;
    private final ExtensionManager extensionManager;
    private final PurchaseContext purchaseContext;
    private final String ticketReservationId;
    private final EuVatChecker checker;
    private final EUVatChecker client = new EUVatChecker();

    @Override
    public boolean test(String vatNr) {

        if(StringUtils.isEmpty(vatNr)) {
            log.warn("empty VAT number received for organizationId {}", purchaseContext.getOrganizationId());
        }

        String organizerCountry = EuVatChecker.organizerCountry(configurationManager, purchaseContext);

        if(!EuVatChecker.validationEnabled(configurationManager, purchaseContext)) {
            log.warn("VAT checking is not enabled for organizationId {} or country not defined ({})", purchaseContext.getOrganizationId(), organizerCountry);
            return false;
        }

        EUVatCheckResponse result = EuVatChecker.validateEUVat(vatNr, organizerCountry, client);
        boolean validStrict = result != null && result.isValid();
        boolean valid = validStrict;

        if(!valid && StringUtils.isNotBlank(vatNr)) {
            valid = extensionManager.handleTaxIdValidation(purchaseContext, vatNr, organizerCountry);
        }
        if(valid && StringUtils.isNotEmpty(ticketReservationId)) {
            VatDetail detail = new VatDetail(vatNr, organizerCountry, true, "", "", validStrict ? VatDetail.Type.VIES : VatDetail.Type.FORMAL, false);
            checker.logSuccessfulValidation(detail, ticketReservationId, purchaseContext);
        }
        return valid;
    }
}
