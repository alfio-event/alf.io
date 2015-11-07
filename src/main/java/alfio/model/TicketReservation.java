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
package alfio.model;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import alfio.model.transaction.PaymentProxy;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

@Getter
public class TicketReservation {

    public enum TicketReservationStatus {
        PENDING, IN_PAYMENT, OFFLINE_PAYMENT, COMPLETE, STUCK
    }

    private final String id;
    private final Date validity;
    private final TicketReservationStatus status;
    private final String fullName;
    private final String email;
    private final String billingAddress;
    private final ZonedDateTime confirmationTimestamp;
    private final ZonedDateTime latestReminder;
    private final PaymentProxy paymentMethod;
    private final Boolean reminderSent;
    private final Integer promoCodeDiscountId;
    private final boolean automatic;
    private final String userLanguage;
    private final boolean directAssignmentRequested;

    public TicketReservation(@Column("id") String id,
                             @Column("validity") Date validity,
                             @Column("status") TicketReservationStatus status,
                             @Column("full_name") String fullName,
                             @Column("email_address") String email,
                             @Column("billing_address") String billingAddress,
                             @Column("confirmation_ts") ZonedDateTime confirmationTimestamp,
                             @Column("latest_reminder_ts") ZonedDateTime latestReminder,
                             @Column("payment_method") PaymentProxy paymentMethod,
                             @Column("offline_payment_reminder_sent") Boolean reminderSent,
                             @Column("promo_code_id_fk") Integer promoCodeDiscountId,
                             @Column("automatic") boolean automatic,
                             @Column("user_language") String userLanguage,
                             @Column("direct_assignment") boolean directAssignmentRequested) {
        this.id = id;
        this.validity = validity;
        this.status = status;
        this.fullName = fullName;
        this.email = email;
        this.billingAddress = billingAddress;
        this.confirmationTimestamp = confirmationTimestamp;
        this.latestReminder = latestReminder;
        this.paymentMethod = paymentMethod;
        this.reminderSent = reminderSent;
        this.promoCodeDiscountId = promoCodeDiscountId;
        this.automatic = automatic;
        this.userLanguage = userLanguage;
        this.directAssignmentRequested = directAssignmentRequested;
    }

    public boolean isStuck() {
        return status == TicketReservationStatus.STUCK;
    }

    public boolean isReminderSent() {
        return Optional.ofNullable(reminderSent).orElse(false);
    }

    public boolean getHasBillingAddress() {
        return StringUtils.isNotBlank(billingAddress);
    }

    public Optional<ZonedDateTime> latestNotificationTimestamp(ZoneId zoneId) {
        return Optional.ofNullable(latestReminder).map(d -> d.withZoneSameInstant(zoneId));
    }
}
