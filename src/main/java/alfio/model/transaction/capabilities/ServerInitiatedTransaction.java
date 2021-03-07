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
package alfio.model.transaction.capabilities;

import alfio.manager.payment.PaymentSpecification;
import alfio.model.PurchaseContext;
import alfio.model.transaction.Capability;
import alfio.model.transaction.Transaction;
import alfio.model.transaction.TransactionInitializationToken;

import java.util.List;
import java.util.Map;

public interface ServerInitiatedTransaction extends Capability {

    TransactionInitializationToken initTransaction(PaymentSpecification paymentSpecification, Map<String, List<String>> params);

    TransactionInitializationToken errorToken(String errorMessage, boolean reservationStatusChanged);

    boolean discardTransaction(Transaction transaction, PurchaseContext purchaseContext);
}
