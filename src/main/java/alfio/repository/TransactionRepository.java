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
package alfio.repository;

import alfio.model.transaction.Transaction;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.ZonedDateTime;

@QueryRepository
public interface TransactionRepository {

    @Query("insert into b_transaction(gtw_tx_id, reservation_id, t_timestamp, price_cts, currency, description, payment_proxy) " +
            "values(:transactionId, :reservationId, :timestamp, :priceInCents, :currency, :description, :paymentProxy)")
    int insert(@Bind("transactionId") String transactionId,
               @Bind("reservationId") String reservationId,
               @Bind("timestamp") ZonedDateTime timestamp,
               @Bind("priceInCents") int priceInCents,
               @Bind("currency") String currency,
               @Bind("description") String description,
               @Bind("paymentProxy") String paymentProxy);

    @Query("select * from b_transaction where reservation_id = :reservationId")
    Transaction loadByReservationId(@Bind("reservationId") String reservationId);
}
