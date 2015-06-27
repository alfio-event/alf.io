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
package alfio.repository.system;

import alfio.datamapper.Bind;
import alfio.datamapper.Query;
import alfio.datamapper.QueryRepository;
import alfio.model.system.Configuration;

import java.util.List;

@QueryRepository
public interface ConfigurationRepository {

    @Query("SELECT id, c_key, c_value, description, 'SYSTEM' as configuration_path_level  FROM configuration")
    List<Configuration> findAll();

    String SYSTEM_FIND_BY_KEY = "SELECT id, c_key, c_value, description, 'SYSTEM' as configuration_path_level FROM configuration " +
            " where c_key = :key";

    String ORGANIZATION_FIND_BY_KEY = "SELECT id, c_key, c_value, description, 'ORGANIZATION' as configuration_path_level FROM configuration_organization " +
            " where c_key = :key and organization_id_fk = :organizationId";

    String EVENT_FIND_BY_KEY = "SELECT id, c_key, c_value, description, 'EVENT' as configuration_path_level FROM configuration_event " +
            " where c_key = :key and organization_id_fk = :organizationId and event_id_fk = :eventId";

    String TICKET_CATEGORY_FIND_BY_KEY = "SELECT id, c_key, c_value, description, 'TICKET_CATEGORY' as configuration_path_level FROM configuration_ticket_category " +
            " where c_key = :key and organization_id_fk = :organizationId and event_id_fk = :eventId and  ticket_category_id_fk = :ticketCategoryId";

    @Query(SYSTEM_FIND_BY_KEY)
    Configuration findByKey(@Bind("key") String key);

    @Query(SYSTEM_FIND_BY_KEY + " UNION ALL " + ORGANIZATION_FIND_BY_KEY)
    List<Configuration> findByOrganizationAndKey(@Bind("organizationId") int organizationId, @Bind("key") String key);

    @Query(SYSTEM_FIND_BY_KEY + " UNION ALL " + ORGANIZATION_FIND_BY_KEY + " UNION ALL " + EVENT_FIND_BY_KEY)
    List<Configuration> findByEventAndKey(@Bind("organizationId") int organizationId, @Bind("eventId") int eventId,
                                          @Bind("key") String key);

    @Query(SYSTEM_FIND_BY_KEY + " UNION ALL " + ORGANIZATION_FIND_BY_KEY + " UNION ALL " + EVENT_FIND_BY_KEY + " UNION ALL " + TICKET_CATEGORY_FIND_BY_KEY)
    List<Configuration> findByTicketCategoryAndKey(@Bind("organizationId") int organizationId,
                                                   @Bind("eventId") int eventId,
                                                   @Bind("ticketCategoryId") int ticketCategoryId,
                                                   @Bind("key") String key);
    
    @Query("DELETE FROM configuration where c_key = :key")
    void deleteByKey(@Bind("key") String key);

    @Query("INSERT into configuration(c_key, c_value, description) values(:key, :value, :description)")
    int insert(@Bind("key") String key, @Bind("value") String value, @Bind("description") String description);

    @Query("UPDATE configuration set c_value = :value where c_key = :key")
    int update(@Bind("key") String existingKey, @Bind("value") String newValue);
}
