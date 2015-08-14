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

import alfio.model.TicketCategoryDescription;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.List;
import java.util.Optional;

@QueryRepository
public interface TicketCategoryDescriptionRepository {

    @Query("select * from ticket_category_text where ticket_category_id_fk = :ticketCategoryId")
    List<TicketCategoryDescription> findByTicketCategoryId(@Bind("ticketCategoryId") int ticketCategoryId);

    @Query("select description from ticket_category_text where ticket_category_id_fk = :ticketCategoryId and locale = :locale")
    Optional<String> findByTicketCategoryIdAndLocale(@Bind("ticketCategoryId") int ticketCategoryId, @Bind("locale") String locale);

    @Query("insert into ticket_category_text(ticket_category_id_fk, locale, description) values (:ticketCategoryId, :locale, :description)")
    int insert(@Bind("ticketCategoryId") int ticketCategoryId, @Bind("locale") String locale, @Bind("description") String description);

    @Query("delete from ticket_category_text where ticket_category_id_fk = :ticketCategoryId")
    int delete(@Bind("ticketCategoryId") int ticketCategoryId);
}
