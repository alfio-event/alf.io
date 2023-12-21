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
import alfio.model.PromoCodeUsageResult;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@QueryRepository
public interface PromoCodeDiscountRepository {

    @Query("select * from promo_code where event_id_fk = :eventId order by promo_code asc")
    List<PromoCodeDiscount> findAllInEvent(@Bind("eventId") int eventId);

    @Query("select * from promo_code where organization_id_fk = :organizationId and event_id_fk is null order by promo_code asc")
    List<PromoCodeDiscount> findAllInOrganization(@Bind("organizationId") int organizationId);

    @Query("delete from promo_code where id = :id")
    int deletePromoCode(@Bind("id") int id);
    
    @Query("select * from promo_code where id = :id")
    PromoCodeDiscount findById(@Bind("id") int id);

    @Query("select * from promo_code where id = :id")
    Optional<PromoCodeDiscount> findOptionalById(@Bind("id") int id);

    @Query("select exists (select 1 from promo_code where id = :id and organization_id_fk = :orgId and (:eventId is null or event_id_fk = :eventId)")
    Boolean checkPromoCodeExists(@Bind("id") int id, @Bind("orgId") int organizationId, @Bind("eventId") Integer eventId);

    @Query("insert into promo_code(promo_code, event_id_fk, organization_id_fk, valid_from, valid_to, discount_amount, discount_type, categories, max_usage, description, email_reference, code_type, hidden_category_id, currency_code) "
        + " values (:promoCode, :eventId, :organizationId, :start, :end, :discountAmount, :discountType, :categories, :maxUsage, :description, :emailReference, :codeType, :hiddenCategoryId, :currencyCode)")
    int addPromoCode(@Bind("promoCode") String promoCode,
                     @Bind("eventId") Integer eventId,
                     @Bind("organizationId") int organizationId,
                     @Bind("start") ZonedDateTime start,
                     @Bind("end") ZonedDateTime end,
                     @Bind("discountAmount") int discountAmount,
                     @Bind("discountType") PromoCodeDiscount.DiscountType discountType,
                     @Bind("categories") String categories,
                     @Bind("maxUsage") Integer maxUsage,
                     @Bind("description") String description,
                     @Bind("emailReference") String emailReference,
                     @Bind("codeType") PromoCodeDiscount.CodeType codeType,
                     @Bind("hiddenCategoryId") Integer hiddenCategoryId,
                     @Bind("currencyCode") String currencyCode);

    @Query("insert into promo_code(promo_code, event_id_fk, organization_id_fk, valid_from, valid_to, discount_amount, discount_type, categories, max_usage, description, email_reference, code_type, hidden_category_id) "
        + " values (:promoCode, :eventId, :organizationId, :start, :end, :discountAmount, :discountType, :categories, :maxUsage, :description, :emailReference, :codeType, :hiddenCategoryId) "
        + " ON CONFLICT (promo_code, event_id_fk) where event_id_fk is not null do nothing"
    )
    int addPromoCodeIfNotExists(@Bind("promoCode") String promoCode,
                                @Bind("eventId") Integer eventId,
                                @Bind("organizationId") int organizationId,
                                @Bind("start") ZonedDateTime start,
                                @Bind("end") ZonedDateTime end,
                                @Bind("discountAmount") int discountAmount,
                                @Bind("discountType") PromoCodeDiscount.DiscountType discountType,
                                @Bind("categories") String categories,
                                @Bind("maxUsage") Integer maxUsage,
                                @Bind("description") String description,
                                @Bind("emailReference") String emailReference,
                                @Bind("codeType") PromoCodeDiscount.CodeType codeType,
                                @Bind("hiddenCategoryId") Integer hiddenCategoryId);

    @Query("select * from promo_code where promo_code = :promoCode and ("
        +" event_id_fk = :eventId or "
        +" (event_id_fk is null and organization_id_fk = (select org_id from event where id = :eventId))) "
        +" order by event_id_fk is null limit 1")
    Optional<PromoCodeDiscount> findPromoCodeInEventOrOrganization(@Bind("eventId") int eventId, @Bind("promoCode") String promoCode);

    @Query("select * from promo_code where promo_code = :promoCode and code_type <> 'DYNAMIC' and ("
        +" event_id_fk = :eventId or "
        +" (event_id_fk is null and organization_id_fk = (select org_id from event where id = :eventId))) "
        +" order by event_id_fk is null limit 1")
    Optional<PromoCodeDiscount> findPublicPromoCodeInEventOrOrganization(@Bind("eventId") int eventId, @Bind("promoCode") String promoCode);

    @Query("select count(*) from promo_code where event_id_fk = :eventId or (event_id_fk is null and organization_id_fk = :organizationId)")
    Integer countByEventAndOrganizationId(@Bind("eventId") int eventId, @Bind("organizationId") int organizationId);

    @Query("select count(b.id) from tickets_reservation a, ticket b" +
        " where (:currentId is null or a.id <> :currentId) and a.status in ('OFFLINE_PAYMENT', 'DEFERRED_OFFLINE_PAYMENT', 'COMPLETE', 'STUCK') and a.promo_code_id_fk = :id" +
        " and b.tickets_reservation_id = a.id and (:categoriesJson is null or b.category_id in (:categories))")
    Integer countConfirmedPromoCode(@Bind("id") int id, @Bind("categories") Collection<Integer> categories, @Bind("currentId") String currentReservationId, @Bind("categoriesJson") String categoriesJson);

    @Query("update promo_code set valid_to = :end where id = :id")
    int updateEventPromoCodeEnd(@Bind("id") int id, @Bind("end") ZonedDateTime end);

    @Query("update promo_code set valid_from = :start, valid_to = :end, max_usage = :maxUsage, categories = :categories, description = :description, email_reference = :emailReference, hidden_category_id = :hiddenCategoryId where id = :id")
    int updateEventPromoCode(@Bind("id") int id,
                             @Bind("start") ZonedDateTime start,
                             @Bind("end") ZonedDateTime end,
                             @Bind("maxUsage") Integer maxUsage,
                             @Bind("categories") String categories,
                             @Bind("description") String description,
                             @Bind("emailReference") String emailReference,
                             @Bind("hiddenCategoryId") Integer hiddenCategoryId);

    @Query("select id from promo_code where code_type = 'ACCESS' and id = :id for update")
    Integer lockAccessCodeForUpdate(@Bind("id") int id);

    @Query("select * from promocode_usage_details where promo_code = :promoCode" +
        " and (:eventId is null or :eventId = event_id)")
    List<PromoCodeUsageResult> findDetailedUsage(@Bind("promoCode") String promoCode,
                                                 @Bind("eventId") Integer eventId);
}
