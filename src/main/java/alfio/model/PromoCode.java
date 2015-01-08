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

import java.time.ZonedDateTime;

import alfio.datamapper.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class PromoCode {
	
	public enum DiscountType {
		FIXED_AMOUNT, PERCENTAGE
	}

	private final int id;
	private final String promoCode;
	private final int eventId;
	private final ZonedDateTime start;
	private final ZonedDateTime end;
	private final int discountAmount;
	private final DiscountType discountType;
	
	public PromoCode(@Column("id")int id, 
			@Column("promo_code") String promoCode, 
			@Column("event_id_fk") int eventId,
			@Column("start") ZonedDateTime start, 
			@Column("end") ZonedDateTime end, 
			@Column("discount_amount") int discountAmount,
			@Column("discount_type") DiscountType discountType) {
		this.id = id;
		this.promoCode = promoCode;
		this.eventId = eventId;
		this.start = start;
		this.end = end;
		this.discountAmount = discountAmount;
		this.discountType = discountType;
	}
}
