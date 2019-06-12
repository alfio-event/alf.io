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

import alfio.model.EventDescription;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@QueryRepository
public interface EventDescriptionRepository {

    @Query("select * from event_description_text where event_id_fk = :eventId")
    List<EventDescription> findByEventId(@Bind("eventId") int eventId);

    @Query("select * from event_description_text where event_id_fk = :eventId and type = :type")
    List<EventDescription> findByEventIdAndType(@Bind("eventId") int eventId, @Bind("type")EventDescription.EventDescriptionType type);

    @Query("select * from event_description_text where event_id_fk in (:eventIds)")
    List<EventDescription> findByEventIds(@Bind("eventIds") Collection<Integer> eventIds);

    default Map<String, String> findByEventIdAsMap(int eventId) {
        return findByEventId(eventId).stream().collect(Collectors.toMap(EventDescription::getLocale, EventDescription::getDescription));
    }

    default Map<Integer, List<EventDescription>> findByEventIdsAsMap(Collection<Integer> eventIds) {
        return findByEventIds(eventIds).stream().collect(Collectors.groupingBy(EventDescription::getEventId));
    }

    @Query("select description from event_description_text where event_id_fk = :eventId and type = :type and locale = :locale")
    Optional<String> findDescriptionByEventIdTypeAndLocale(@Bind("eventId") int eventId, @Bind("type") EventDescription.EventDescriptionType type, @Bind("locale") String locale);

    @Query("insert into event_description_text(event_id_fk, locale, type, description) values (:eventId, :locale, :type, :description)")
    int insert(@Bind("eventId") int eventId, @Bind("locale") String locale, @Bind("type") EventDescription.EventDescriptionType type, @Bind("description") String description);

    @Query("delete from event_description_text where event_id_fk = :eventId and type = :type")
    int delete(@Bind("eventId") int eventId, @Bind("type") EventDescription.EventDescriptionType type);
}
