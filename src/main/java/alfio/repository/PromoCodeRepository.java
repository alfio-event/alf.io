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

import java.time.ZonedDateTime;
import java.util.List;

import alfio.datamapper.Bind;
import alfio.datamapper.Query;
import alfio.datamapper.QueryRepository;
import alfio.model.PromoCode;

@QueryRepository
public interface PromoCodeRepository {

	@Query("select * from promo_code where event_id_fk = :eventId order by promo_code asc")
	List<PromoCode> findAllInEvent(@Bind("eventId") int eventId);

	@Query("delete from promo_code where id = :id")
	int deletePromoCode(@Bind("id") int id);

	@Query("insert into promo_code(promo_code, event_id_fk, start, end, discount_amount, discount_type) "
			+ " values (:promoCode, :eventId, :start, :end, :discountAmount, :discountType)")
	int addPromoCode(@Bind("promoCode") String promoCode,
			@Bind("eventId") int eventId, @Bind("start") ZonedDateTime start,
			@Bind("end") ZonedDateTime end,
			@Bind("discountAmount") int discountAmount,
			@Bind("discountType") String discountType);
}
