/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.model;

import io.bagarino.datamapper.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.Date;

@Getter
public class TicketReservation {

	public enum TicketReservationStatus {
		PENDING, IN_PAYMENT, COMPLETE, STUCK
	}

	private final String id;
	private final Date validity;
	private final TicketReservationStatus status;
	private final String fullName;
	private final String email;
	private final String billingAddress;
    private final ZonedDateTime confirmationTimestamp;
	
	public TicketReservation(@Column("id") String id,
                             @Column("validity") Date validity,
                             @Column("status") TicketReservationStatus status,
                             @Column("full_name") String fullName,
                             @Column("email_address") String email,
                             @Column("billing_address") String billingAddress,
                             @Column("confirmation_ts") ZonedDateTime confirmationTimestamp) {
		this.id = id;
		this.validity = validity;
		this.status = status;
		this.fullName = fullName;
		this.email = email;
		this.billingAddress = billingAddress;
        this.confirmationTimestamp = confirmationTimestamp;
    }

    public boolean isStuck() {
        return status == TicketReservationStatus.STUCK;
    }
}
