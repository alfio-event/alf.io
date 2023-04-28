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

import alfio.model.support.JSONData;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.Transaction;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@QueryRepository
public interface TransactionRepository {

    String SELECT_BY_RESERVATION_ID = "select * from b_transaction where reservation_id = :reservationId";
    String SELECT_VALID_BY_RESERVATION_ID = SELECT_BY_RESERVATION_ID + " and status not in ('INVALID', 'OFFLINE_DISABLE_MATCH')";
    String UPDATE_TRANSACTION_BY_ID = "update b_transaction set gtw_tx_id = :gatewayTransactionId, gtw_payment_id = :paymentId, " +
        "t_timestamp = :timestamp, plat_fee = :platformFee, gtw_fee = :gatewayFee, status = :status, metadata = to_json(:metadata::json) where id = :transactionId";

    @Query("insert into b_transaction(gtw_tx_id, gtw_payment_id, reservation_id, t_timestamp, price_cts, currency, description, payment_proxy, plat_fee, gtw_fee, status, metadata) " +
            "values(:transactionId, :paymentId, :reservationId, :timestamp, :priceInCents, :currency, :description, :paymentProxy, :platformFee, :gatewayFee, :status, to_json(:metadata::json))")
    int insert(@Bind("transactionId") String transactionId,
               @Bind("paymentId") String paymentId,
               @Bind("reservationId") String reservationId,
               @Bind("timestamp") ZonedDateTime timestamp,
               @Bind("priceInCents") int priceInCents,
               @Bind("currency") String currency,
               @Bind("description") String description,
               @Bind("paymentProxy") String paymentProxy,
               @Bind("platformFee") long platformFee,
               @Bind("gatewayFee") long gatewayFee,
               @Bind("status") Transaction.Status status,
               @Bind("metadata") @JSONData Map<String, String> metadata);

    @Query(UPDATE_TRANSACTION_BY_ID)
    int update(@Bind("transactionId") int id,
               @Bind("gatewayTransactionId") String gatewayTransactionId,
               @Bind("paymentId") String gatewayPaymentId,
               @Bind("timestamp") ZonedDateTime timestamp,
               @Bind("platformFee") long platformFee,
               @Bind("gatewayFee") long gatewayFee,
               @Bind("status") Transaction.Status status,
               @Bind("metadata") @JSONData Map<String, String> metadata);

    @Query(UPDATE_TRANSACTION_BY_ID + " and status = :expectedStatus")
    int updateIfStatus(@Bind("transactionId") int id,
                       @Bind("gatewayTransactionId") String gatewayTransactionId,
                       @Bind("paymentId") String gatewayPaymentId,
                       @Bind("timestamp") ZonedDateTime timestamp,
                       @Bind("platformFee") long platformFee,
                       @Bind("gatewayFee") long gatewayFee,
                       @Bind("status") Transaction.Status status,
                       @Bind("metadata") @JSONData Map<String, String> metadata,
                       @Bind("expectedStatus") Transaction.Status expectedCurrentStatus);

    @Query(SELECT_VALID_BY_RESERVATION_ID + " order by t_timestamp desc limit 1 for update")
    Optional<Transaction> lockLatestForUpdate(@Bind("reservationId") String reservationId);

    @Query("select id from b_transaction where id = :id for update")
    Integer lockByIdForUpdate(@Bind("id") Integer id);

    @Query("update b_transaction set status = :status where reservation_id = :reservationId")
    int updateStatusForReservation(@Bind("reservationId") String reservationId, @Bind("status") Transaction.Status status);

    @Query("update b_transaction set status = 'OFFLINE_DISABLE_MATCH' where id = :id and status = 'OFFLINE_PENDING_REVIEW'")
    int discardMatchingPayment(@Bind("id") int transactionId);

    @Query(SELECT_VALID_BY_RESERVATION_ID)
    Transaction loadByReservationId(@Bind("reservationId") String reservationId);

    @Query("delete from b_transaction where reservation_id in (:reservationIds)")
    int deleteForReservations(@Bind("reservationIds") List<String> reservationIds);

    @Query("update b_transaction set status = 'INVALID' where reservation_id = :reservationId and status <> 'COMPLETE' and " +
        " (:paymentProxy is null or (:paymentProxy is not null and payment_proxy = :paymentProxy)) ")
    int invalidateForReservation(@Bind("reservationId") String reservationId, @Bind("paymentProxy") String paymentProxy);

    @Query("delete from b_transaction where reservation_id in (:reservationIds) and status = :status")
    int deleteForReservationsWithStatus(@Bind("reservationIds") List<String> reservationIds, @Bind("status") Transaction.Status status);

    @Query(SELECT_VALID_BY_RESERVATION_ID)
    Optional<Transaction> loadOptionalByReservationId(@Bind("reservationId") String reservationId);

    @Query(SELECT_VALID_BY_RESERVATION_ID + " and status = :status")
    Optional<Transaction> loadOptionalByReservationIdAndStatus(@Bind("reservationId") String reservationId, @Bind("status") Transaction.Status status);

    @Query(SELECT_VALID_BY_RESERVATION_ID + " and status = :status for update")
    Optional<Transaction> loadOptionalByReservationIdAndStatusForUpdate(@Bind("reservationId") String reservationId, @Bind("status") Transaction.Status status);

    @Query(SELECT_VALID_BY_RESERVATION_ID + " and status = :status and payment_proxy = :paymentProxy")
    Optional<Transaction> loadOptionalByStatusAndPaymentProxyForUpdate(@Bind("reservationId") String reservationId,
                                                                       @Bind("status") Transaction.Status status,
                                                                       @Bind("paymentProxy")PaymentProxy paymentProxy);

    @Query("select * from b_transaction where id = :id and status = :status")
    Optional<Transaction> loadOptionalByIdAndStatus(@Bind("id") int id, @Bind("status") Transaction.Status status);

    @Query("update b_transaction set plat_fee = :platformFee, gtw_fee = :gatewayFee where gtw_tx_id = :transactionId and reservation_id = :reservationId")
    int updateFees(@Bind("transactionId") String transactionId,
                   @Bind("reservationId") String reservationId,
                   @Bind("platformFee") long platformFee,
                   @Bind("gatewayFee") long gatewayFee);

    @Query("update b_transaction set status = 'INVALID' where id = :id")
    int invalidateById(@Bind("id") int id);

    @Query("update b_transaction set metadata = :metadata::jsonb, t_timestamp = :timestamp where id = :id")
    int updateDetailsById(@Bind("id") int id,
                          @Bind("metadata") @JSONData Map<String, String> metadata,
                          @Bind("timestamp") ZonedDateTime timestamp);
}
