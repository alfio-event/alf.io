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
package alfio.model.transaction;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
public class Transaction {

    private final int id;
    private final String transactionId;
    private final String reservationId;
    private final ZonedDateTime timestamp;
    private final int priceInCents;
    private final String currency;
    private final String description;
    private final PaymentProxy paymentProxy;


    public Transaction(@Column("id") int id,
                       @Column("gtw_tx_id") String transactionId,
                       @Column("reservation_id") String reservationId,
                       @Column("t_timestamp") ZonedDateTime timestamp,
                       @Column("price_cts") int priceInCents,
                       @Column("currency") String currency,
                       @Column("description") String description,
                       @Column("payment_proxy") String paymentProxy) {
        this.id = id;
        this.transactionId = transactionId;
        this.reservationId = reservationId;
        this.timestamp = timestamp;
        this.priceInCents = priceInCents;
        this.currency = currency;
        this.description = description;
        this.paymentProxy = PaymentProxy.valueOf(paymentProxy);
    }
}
