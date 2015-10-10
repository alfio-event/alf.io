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

import alfio.model.system.Configuration;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.List;
import java.util.Optional;

@QueryRepository
public interface ConfigurationRepository {

    String INSERT_STATEMENT = "INSERT into configuration(c_key, c_value, description) values(:key, :value, :description)";

    @Query("SELECT id, c_key, c_value, description, 'SYSTEM' as configuration_path_level  FROM configuration")
    List<Configuration> findSystemConfiguration();

    @Query("SELECT id, c_key, c_value, description, 'ORGANIZATION' as configuration_path_level  FROM configuration_organization where organization_id_fk = :organizationId")
    List<Configuration> findOrganizationConfiguration(@Bind("organizationId") int organizationId);

    @Query("SELECT id, c_key, c_value, description, 'EVENT' as configuration_path_level FROM configuration_event where organization_id_fk = :organizationId and event_id_fk = :eventId")
    List<Configuration> findEventConfiguration(@Bind("organizationId") int organizationId, @Bind("eventId") int eventId);

    @Query("SELECT id, c_key, c_value, description, 'TICKET_CATEGORY' as configuration_path_level FROM configuration_ticket_category where organization_id_fk = :organizationId and event_id_fk = :eventId and  ticket_category_id_fk = :ticketCategoryId")
    List<Configuration> findCategoryConfiguration(@Bind("organizationId") int organizationId, @Bind("eventId") int eventId, @Bind("ticketCategoryId") int categoryId);

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

    @Query(SYSTEM_FIND_BY_KEY)
    Optional<Configuration> findOptionalByKey(@Bind("key") String key);

    @Query(SYSTEM_FIND_BY_KEY + " UNION ALL " + ORGANIZATION_FIND_BY_KEY)
    List<Configuration> findByOrganizationAndKey(@Bind("organizationId") int organizationId, @Bind("key") String key);

    @Query(ORGANIZATION_FIND_BY_KEY)
    Optional<Configuration> findByKeyAtOrganizationLevel(@Bind("organizationId") int organizationId, @Bind("key") String key);

    @Query(EVENT_FIND_BY_KEY)
    Optional<Configuration> findByKeyAtEventLevel(@Bind("eventId") int eventId, @Bind("organizationId") int organizationId, @Bind("key") String key);

    @Query(TICKET_CATEGORY_FIND_BY_KEY)
    Optional<Configuration> findByKeyAtCategoryLevel(@Bind("eventId") int eventId, @Bind("organizationId") int organizationId, @Bind("ticketCategoryId") int ticketCategoryId, @Bind("key") String key);

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

    @Query("DELETE FROM configuration_organization where c_key = :key and organization_id_fk = :organizationId")
    void deleteOrganizationLevelByKey(@Bind("key") String key, @Bind("organizationId") int organizationId);

    @Query("DELETE FROM configuration_event where c_key = :key and event_id_fk = :eventId")
    void deleteEventLevelByKey(@Bind("key") String key, @Bind("eventId") int eventId);

    @Query("DELETE FROM configuration_ticket_category where c_key = :key and event_id_fk = :eventId and ticket_category_id_fk = :categoryId")
    void deleteCategoryLevelByKey(@Bind("key") String key, @Bind("eventId") int eventId, @Bind("categoryId") int categoryId);

    @Query(INSERT_STATEMENT)
    int insert(@Bind("key") String key, @Bind("value") String value, @Bind("description") String description);

    @Query("INSERT into configuration_organization(organization_id_fk, c_key, c_value, description) values(:orgId, :key, :value, :description)")
    int insertOrganizationLevel(@Bind("orgId") int orgId, @Bind("key") String key, @Bind("value") String value, @Bind("description") String description);

    @Query("update configuration_organization set c_value = :value where organization_id_fk = :orgId and c_key = :key")
    int updateOrganizationLevel(@Bind("orgId") int orgId, @Bind("key") String key, @Bind("value") String value);

    @Query("update configuration_event set c_value = :value where event_id_fk = :eventId and organization_id_fk = :organizationId and c_key = :key")
    int updateEventLevel(@Bind("eventId") int eventId, @Bind("organizationId") int organizationId, @Bind("key") String key, @Bind("value") String value);

    @Query("update configuration_ticket_category set c_value = :value where event_id_fk = :eventId and organization_id_fk = :organizationId and ticket_category_id_fk = :ticketCategoryId and c_key = :key")
    int updateCategoryLevel(@Bind("eventId") int eventId, @Bind("organizationId") int organizationId, @Bind("ticketCategoryId") int ticketCategoryId, @Bind("key") String key, @Bind("value") String value);

    @Query("INSERT into configuration_event(organization_id_fk, event_id_fk, c_key, c_value, description) values(:orgId, :eventId, :key, :value, :description)")
    int insertEventLevel(@Bind("orgId") int orgId, @Bind("eventId") int eventId, @Bind("key") String key, @Bind("value") String value, @Bind("description") String description);

    @Query("INSERT into configuration_ticket_category(organization_id_fk, event_id_fk, ticket_category_id_fk, c_key, c_value, description) values(:orgId, :eventId, :ticketCategoryId, :key, :value, :description)")
    int insertTicketCategoryLevel(@Bind("orgId") int orgId, @Bind("eventId") int eventId, @Bind("ticketCategoryId") int ticketCategoryId, @Bind("key") String key, @Bind("value") String value, @Bind("description") String description);

    @Query("UPDATE configuration set c_value = :value where c_key = :key")
    int update(@Bind("key") String existingKey, @Bind("value") String newValue);
}
