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

import alfio.model.support.JSONData;
import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

@Getter
public class Transaction {

    public static final String NOTES_KEY = "transactionNotes";
    public enum Status {
        PENDING,
        OFFLINE_MATCHING_PAYMENT_FOUND,
        OFFLINE_PENDING_REVIEW,
        OFFLINE_DISABLE_MATCH,
        COMPLETE,
        FAILED,
        CANCELLED,
        INVALID
    }

    private final int id;
    private final String transactionId;
    private final String paymentId;
    private final String reservationId;
    private final ZonedDateTime timestamp;
    private final int priceInCents;
    private final String currency;
    private final String description;
    private final PaymentProxy paymentProxy;
    private final long platformFee;
    private final long gatewayFee;
    private final Status status;
    private final Map<String, String> metadata;


    public Transaction(@Column("id") int id,
                       @Column("gtw_tx_id") String transactionId,
                       @Column("gtw_payment_id") String paymentId,
                       @Column("reservation_id") String reservationId,
                       @Column("t_timestamp") ZonedDateTime timestamp,
                       @Column("price_cts") int priceInCents,
                       @Column("currency") String currency,
                       @Column("description") String description,
                       @Column("payment_proxy") String paymentProxy,
                       @Column("plat_fee") long platformFee,
                       @Column("gtw_fee") long gatewayFee,
                       @Column("status") Status status,
                       @Column("metadata") @JSONData Map<String, String> metadata) {
        this.id = id;
        this.transactionId = transactionId;
        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.timestamp = timestamp;
        this.priceInCents = priceInCents;
        this.currency = currency;
        this.description = description;
        this.paymentProxy = PaymentProxy.valueOf(paymentProxy);
        this.platformFee = platformFee;
        this.gatewayFee = gatewayFee;
        this.status = status;
        this.metadata = Optional.ofNullable(metadata).orElse(Map.of());
    }

    public boolean isComplete() {
        return status == Status.COMPLETE;
    }

    public boolean isPotentialMatch() {
        return status == Status.OFFLINE_MATCHING_PAYMENT_FOUND
            || status == Status.OFFLINE_PENDING_REVIEW;
    }

    public String getFormattedAmount() {
        return MonetaryUtil.formatCents(priceInCents, currency);
    }

    public String getNotes() {
        return metadata == null ? null : metadata.get(NOTES_KEY);
    }

    public boolean isTimestampEditable() {
        return paymentProxy == PaymentProxy.OFFLINE || paymentProxy == PaymentProxy.ON_SITE;
    }
}
