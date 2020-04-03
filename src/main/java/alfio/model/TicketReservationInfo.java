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

import java.util.Date;

import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class TicketReservationInfo {

    private final String id;
    private final String fullName;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final Integer finalPriceCts;
    private final String currencyCode;
    private final int eventId;
    private final Date validity;

    public TicketReservationInfo(@Column("id") String id,
                                 @Column("full_name") String fullName,
                                 @Column("first_name") String firstName,
                                 @Column("last_name") String lastName,
                                 @Column("email_address") String email,
                                 @Column("final_price_cts") Integer finalPriceCts,
                                 @Column("currency_code") String currencyCode,
                                 @Column("event_id_fk") int eventId,
                                 @Column("validity") Date validity) {
        this.id = id;
        this.fullName = fullName;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.finalPriceCts = finalPriceCts;
        this.currencyCode = currencyCode;
        this.eventId = eventId;
        this.validity = validity;
    }

    public String getFullName() {
        return (firstName != null && lastName != null) ? (firstName + " " + lastName) : fullName;
    }

    public String getTotalAmount() {
        if(finalPriceCts > 0) {
            return MonetaryUtil.formatCents(finalPriceCts, currencyCode);
        }
        return null;
    }
}