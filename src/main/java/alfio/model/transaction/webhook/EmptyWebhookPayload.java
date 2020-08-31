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

import alfio.model.transaction.TransactionWebhookPayload;
import lombok.AllArgsConstructor;

/**
 * Special {@link alfio.model.transaction.TransactionWebhookPayload} for providers that don't send a payload
 * along with the Webhook
 */
@AllArgsConstructor
public class EmptyWebhookPayload implements TransactionWebhookPayload {

    private final String reservationId;
    private final Status status;

    @Override
    public String getPayload() {
        return "empty";
    }

    @Override
    public String getType() {
        return "empty";
    }

    @Override
    public String getReservationId() {
        return reservationId;
    }

    @Override
    public Status getStatus() {
        return status;
    }

}
