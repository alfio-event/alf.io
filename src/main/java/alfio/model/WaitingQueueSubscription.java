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

import alfio.util.LocaleUtil;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;
import lombok.ToString;

import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;

@Getter
@ToString
public class WaitingQueueSubscription {

    public enum Status {
        WAITING, PENDING, ACQUIRED, EXPIRED, CANCELLED
    }

    public enum Type {
        PRE_SALES, SOLD_OUT
    }

    private final int id;
    private final ZonedDateTime creation;
    private final int eventId;
    private final Status status;
    private final String fullName;
    private final String firstName;
    private final String lastName;
    private final String emailAddress;
    private final String reservationId;
    private final String userLanguage;
    private final Integer selectedCategoryId;
    private final Type subscriptionType;

    public WaitingQueueSubscription(@Column("id") int id,
                                    @Column("creation") ZonedDateTime creation,
                                    @Column("event_id") int eventId,
                                    @Column("status") String status,
                                    @Column("full_name") String fullName,
                                    @Column("first_name") String firstName,
                                    @Column("last_name") String lastName,
                                    @Column("email_address") String emailAddress,
                                    @Column("ticket_reservation_id") String reservationId,
                                    @Column("user_language") String userLanguage,
                                    @Column("selected_category_id") Integer selectedCategoryId,
                                    @Column("subscription_type") Type subscriptionType) {
        this.id = id;
        this.creation = creation;
        this.eventId = eventId;
        this.userLanguage = userLanguage;
        this.selectedCategoryId = selectedCategoryId;
        this.subscriptionType = subscriptionType;
        this.status = Status.valueOf(status);
        this.fullName = fullName;
        this.firstName = firstName;
        this.lastName = lastName;
        this.reservationId = reservationId;
        this.emailAddress = emailAddress;
    }

    public Locale getLocale() {
        return LocaleUtil.forLanguageTag(userLanguage);
    }

    public boolean isPreSales() {
        return Optional.ofNullable(subscriptionType).map(s -> s == Type.PRE_SALES).orElse(false);
    }
}
