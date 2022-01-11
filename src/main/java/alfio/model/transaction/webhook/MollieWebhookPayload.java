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
package alfio.model.transaction.webhook;

import alfio.model.PurchaseContext;
import alfio.model.transaction.TransactionWebhookPayload;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MollieWebhookPayload implements TransactionWebhookPayload {

    private final String paymentId;
    private final PurchaseContext.PurchaseContextType purchaseContextType;
    private final String purchaseContextIdentifier;
    private final String reservationId;

    @Override
    public String getPayload() {
        return getPaymentId();
    }

    @Override
    public String getType() {
        return null;
    }

    @Override
    public String getReservationId() {
        return reservationId;
    }

    @Override
    public Status getStatus() {
        return null;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public PurchaseContext.PurchaseContextType getPurchaseContextType() {
        return purchaseContextType;
    }

    public String getPurchaseContextIdentifier() {
        return purchaseContextIdentifier;
    }
}
