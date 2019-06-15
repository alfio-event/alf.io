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

import java.util.*;
import java.util.stream.Collectors;

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

    @Query("select * from ticket_category_text where ticket_category_id_fk in (:ticketCategoryIds)")
    List<TicketCategoryDescription> findByTicketCategoryIds(@Bind("ticketCategoryIds") Collection<Integer> ticketCategoryIds);


    default Map<String, String> descriptionForTicketCategory(int ticketCategory) {
        return findByTicketCategoryId(ticketCategory).stream().collect(Collectors.toMap(TicketCategoryDescription::getLocale, TicketCategoryDescription::getDescription));
    }

    default Map<Integer, Map<String, String>> descriptionsByTicketCategory(Collection<Integer> ticketCategoryIds) {
        if(ticketCategoryIds.isEmpty()) {
            return Collections.emptyMap();
        }


        Map<Integer, Map<String, String>> res = new HashMap<>();
        findByTicketCategoryIds(ticketCategoryIds).forEach(t -> {
            if (!res.containsKey(t.getTicketCategoryId())) {
                res.put(t.getTicketCategoryId(), new HashMap<>());
            }
            res.get(t.getTicketCategoryId()).put(t.getLocale(), t.getDescription());
        });
        return res;
    }
}
