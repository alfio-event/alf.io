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

import alfio.model.PromoCodeDiscount;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.ZonedDateTime;
import java.util.List;

@QueryRepository
public interface PromoCodeDiscountRepository {

    @Query("select * from promo_code where event_id_fk = :eventId order by promo_code asc")
    List<PromoCodeDiscount> findAllInEvent(@Bind("eventId") int eventId);

    @Query("delete from promo_code where id = :id")
    int deletePromoCode(@Bind("id") int id);
    
    @Query("select * from promo_code where id = :id")
    PromoCodeDiscount findById(@Bind("id") int id);

    @Query("insert into promo_code(promo_code, event_id_fk, valid_from, valid_to, discount_amount, discount_type, categories) "
            + " values (:promoCode, :eventId, :start, :end, :discountAmount, :discountType, :categories)")
    int addPromoCode(@Bind("promoCode") String promoCode,
            @Bind("eventId") int eventId, @Bind("start") ZonedDateTime start,
            @Bind("end") ZonedDateTime end,
            @Bind("discountAmount") int discountAmount,
            @Bind("discountType") String discountType,
            @Bind("categories") String categories);

    @Query("select * from promo_code where event_id_fk = :eventId and promo_code = :promoCode")
    PromoCodeDiscount findPromoCodeInEvent(@Bind("eventId") int eventId, @Bind("promoCode") String promoCode);

    @Query("select count(*) from promo_code where event_id_fk = :eventId")
    Integer countByEventId(@Bind("eventId") int eventId);

    @Query("select count(*) from promo_code inner join tickets_reservation on promo_code_id_fk = promo_code.id where event_id_fk = :eventId and promo_code = :promoCode")
    Integer countAppliedPromoCode(@Bind("eventId") int eventId, @Bind("promoCode") String promoCode);

    @Query("update promo_code set valid_to = :end where event_id_fk = :eventId and promo_code = :promoCode")
    int updateEnd(@Bind("eventId") int eventId, @Bind("promoCode") String promoCode, @Bind("end") ZonedDateTime end);

    @Query("update promo_code set valid_from = :start, valid_to = :end where event_id_fk = :eventId and promo_code = :promoCode")
    int update(@Bind("eventId") int eventId, @Bind("promoCode") String promoCodeName, @Bind("start") ZonedDateTime start, @Bind("end") ZonedDateTime end);
}
