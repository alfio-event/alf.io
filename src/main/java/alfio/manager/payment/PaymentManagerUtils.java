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

import alfio.model.transaction.PaymentProxy;
import alfio.repository.TransactionRepository;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Validate;

@UtilityClass
@Log4j2
class PaymentManagerUtils {

    static void invalidateExistingTransactions(String reservationId, TransactionRepository transactionRepository) {
        invalidateExistingTransactions(reservationId, transactionRepository, null);
    }

    static void invalidateExistingTransactions(String reservationId, TransactionRepository transactionRepository, PaymentProxy paymentProxy) {
        // temporary, until we allow multiple transactions for a reservation
        int invalidated = transactionRepository.invalidateForReservation(reservationId, paymentProxy != null ? paymentProxy.name() : null);
        if(invalidated > 0) {
            log.debug("invalidated {} existing transactions", invalidated);
        }
        // assert that there is no active transaction left
        Validate.isTrue(transactionRepository.loadOptionalByReservationId(reservationId).isEmpty(), "There is already a transaction registered for reservation %s", reservationId);
    }
}
