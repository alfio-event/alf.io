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

import java.util.Date;

import lombok.Getter;

//TODO: add in the admin tool the possibility to handle reservation that are stuck in the IN_PAYMENT status.
//      They can be caused if the database goes down just after the payment has been ACK by stripes.
@Getter
public class TicketReservation {

	public enum TicketReservationStatus {
		PENDING, IN_PAYMENT, COMPLETE
	}

	private final String id;
	private final Date validity;
	private final TicketReservationStatus status;

	public TicketReservation(@Column("id") String id, @Column("validity") Date validity,
			@Column("status") TicketReservationStatus status) {
		this.id = id;
		this.validity = validity;
		this.status = status;
	}
}
