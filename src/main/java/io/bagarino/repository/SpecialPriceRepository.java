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
package io.bagarino.repository;

import io.bagarino.datamapper.Bind;
import io.bagarino.datamapper.Query;
import io.bagarino.datamapper.QueryRepository;
import io.bagarino.datamapper.QueryType;
import io.bagarino.model.SpecialPrice;

import java.util.List;

@QueryRepository
public interface SpecialPriceRepository {

    @Query("select * from special_price where ticket_category_id = :ticketCategoryId")
    List<SpecialPrice> findAllByCategoryId(@Bind("ticketCategoryId") int ticketCategoryId);

    @Query("select * from special_price where ticket_category_id = :ticketCategoryId and status = 'FREE'")
    List<SpecialPrice> findActiveByCategoryId(@Bind("ticketCategoryId") int ticketCategoryId);

    @Query("select * from special_price where code = :code and status = 'FREE'")
    SpecialPrice getByCode(@Bind("code") String code);

    @Query("select count(*) from special_price where code = :code")
    Integer countByCode(@Bind("code") String code);

    @Query("update special_price set code = :code, status = 'FREE' where id = :id")
    int updateCode(@Bind("code") String code, @Bind("id") int id);

    @Query(type = QueryType.TEMPLATE, value = "insert into special_price (code, price_cts, ticket_category_id, status) " +
            "values(:code, :priceInCents, :ticketCategoryId, :status)")
    String bulkInsert();

    @Query("select * from special_price where status = 'WAITING' for update")
    List<SpecialPrice> findWaitingElements();

}
