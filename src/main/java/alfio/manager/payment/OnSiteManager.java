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
package alfio.manager.payment;

import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProvider;
import alfio.repository.TransactionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static alfio.manager.TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID;
import static alfio.model.system.ConfigurationKeys.ON_SITE_ENABLED;

@Component
@Log4j2
@AllArgsConstructor
@Transactional
public class OnSiteManager implements PaymentProvider {

    private final ConfigurationManager configurationManager;
    private final TransactionRepository transactionRepository;

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context) {
        return paymentMethod == PaymentMethod.ON_SITE && configurationManager.getBooleanConfigValue(context.narrow(ON_SITE_ENABLED), false);
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        PaymentManagerUtils.invalidateExistingTransactions(spec.getReservationId(), transactionRepository);
        return PaymentResult.successful(NOT_YET_PAID_TRANSACTION_ID);
    }

}
