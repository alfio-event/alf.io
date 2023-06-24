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

import alfio.model.support.JSONData;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.system.ConfigurationKeys;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

@QueryRepository
public interface ConfigurationRepository {

    String INSERT_STATEMENT = "INSERT into configuration(c_key, c_value, description) values(:key, :value, :description)";

    String SELECT_FROM_SYSTEM = "SELECT id, c_key, c_value, 'SYSTEM' as configuration_path_level FROM configuration ";
    String SELECT_FROM_ORGANIZATION = "SELECT id, c_key, c_value, 'ORGANIZATION' as configuration_path_level FROM configuration_organization where organization_id_fk = :organizationId ";
    String SELECT_FROM_PURCHASE_CONTEXT = "SELECT id, c_key, c_value, 'PURCHASE_CONTEXT' as configuration_path_level FROM configuration_purchase_context where organization_id_fk = :organizationId ";
    String SELECT_FROM_EVENT = SELECT_FROM_PURCHASE_CONTEXT + " and event_id_fk = :eventId ";
    String SELECT_FROM_SUBSCRIPTION_DESCRIPTOR = SELECT_FROM_PURCHASE_CONTEXT + " and subscription_descriptor_id_fk = :subscriptionDescriptorId ";
    String SELECT_FROM_TICKET_CATEGORY = "SELECT id, c_key, c_value, 'TICKET_CATEGORY' as configuration_path_level FROM configuration_ticket_category where organization_id_fk = :organizationId and event_id_fk = :eventId and ticket_category_id_fk = :ticketCategoryId";

    @Query(SELECT_FROM_SYSTEM)
    List<Configuration> findSystemConfiguration();

    @Query(SELECT_FROM_ORGANIZATION)
    List<Configuration> findOrganizationConfiguration(@Bind("organizationId") int organizationId);

    @Query(SELECT_FROM_EVENT)
    List<Configuration> findEventConfiguration(@Bind("organizationId") int organizationId, @Bind("eventId") int eventId);

    @Query(SELECT_FROM_SUBSCRIPTION_DESCRIPTOR)
    List<Configuration> findSubscriptionDescriptorConfiguration(@Bind("organizationId") int organizationId, @Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId);

    @Query(SELECT_FROM_TICKET_CATEGORY)
    List<Configuration> findCategoryConfiguration(@Bind("organizationId") int organizationId, @Bind("eventId") int eventId, @Bind("ticketCategoryId") int categoryId);

    @Query("SELECT ticket_category_id_fk, c_value FROM configuration_ticket_category where organization_id_fk = :organizationId and event_id_fk = :eventId and c_key = :key")
    List<CategoryAndValue> findAllCategoriesAndValueWith(@Bind("organizationId") int organizationId, @Bind("eventId") int eventId, @Bind("key") String key);

    @Query("select ticket_category_id_fk from configuration_ticket_category where ticket_category_id_fk in (:categories) and c_key = :flagName and c_value = :flagValue")
    List<Integer> getCategoriesWithFlag(@Bind("categories") List<Integer> categoriesIds,
                                        @Bind("flagName") String flagName,
                                        @Bind("flagValue") String flagValue);

    @Query("insert into configuration_purchase_context (c_key, c_value, description, event_id_fk, organization_id_fk)" +
        " select c_key, c_value, description, :targetEventId, :targetOrgId from configuration_purchase_context where event_id_fk = :srcEventId and organization_id_fk = :srcOrgId")
    int copyEventConfiguration(@Bind("targetEventId") int targetEventId,
                               @Bind("targetOrgId") int targetOrgId,
                               @Bind("srcEventId") int srcEventId,
                               @Bind("srcOrgId") int srcOrgId);

    @Query("insert into configuration_ticket_category (c_key, c_value, description, event_id_fk, organization_id_fk, ticket_category_id_fk)" +
            " select c_key, c_value, description, :targetEventId, :targetOrgId, :targetCategoryId from configuration_ticket_category where event_id_fk = :srcEventId and organization_id_fk = :srcOrgId and ticket_category_id_fk = :srcCategoryId")
    int copyCategoryConfiguration(@Bind("targetEventId") int targetEventId,
                                  @Bind("targetOrgId") int targetOrgId,
                                  @Bind("targetCategoryId") int targetCategoryId,
                                  @Bind("srcEventId") int srcEventId,
                                  @Bind("srcOrgId") int srcOrgId,
                                  @Bind("srcCategoryId") int srcCategoryId);

    @Getter
    class CategoryAndValue {
        final int ticketCategoryId;
        final String value;

        public CategoryAndValue(@Column("ticket_category_id_fk") int ticketCategoryId, @Column("c_value") String value) {
            this.ticketCategoryId = ticketCategoryId;
            this.value = value;
        }
    }

    default Map<Integer, String> getAllCategoriesAndValueWith(int organizationId, int eventId, ConfigurationKeys key) {
        return findAllCategoriesAndValueWith(organizationId, eventId, key.name()).stream().collect(Collectors.toMap(CategoryAndValue::getTicketCategoryId, CategoryAndValue::getValue));
    }

    String SYSTEM_FIND_BY_KEY = SELECT_FROM_SYSTEM + " where c_key = :key";
    String ORGANIZATION_FIND_BY_KEY = SELECT_FROM_ORGANIZATION + " and c_key = :key ";
    String EVENT_FIND_BY_KEY = SELECT_FROM_EVENT + " and c_key = :key ";
    String SUBSCRIPTION_DESCRIPTOR_FIND_BY_KEY = SELECT_FROM_SUBSCRIPTION_DESCRIPTOR + " and c_key = :key ";
    String TICKET_CATEGORY_FIND_BY_KEY = SELECT_FROM_TICKET_CATEGORY + " and c_key = :key";

    @Query(SYSTEM_FIND_BY_KEY)
    Configuration findByKey(@Bind("key") String key);

    @Query(SYSTEM_FIND_BY_KEY)
    Optional<Configuration> findOptionalByKey(@Bind("key") String key);

    @Query(SYSTEM_FIND_BY_KEY)
    List<Configuration> findByKeyAtSystemLevel(@Bind("key") String key);

    @Query(SYSTEM_FIND_BY_KEY + " UNION ALL " + ORGANIZATION_FIND_BY_KEY)
    List<Configuration> findByOrganizationAndKey(@Bind("organizationId") int organizationId, @Bind("key") String key);

    @Query(ORGANIZATION_FIND_BY_KEY)
    Optional<Configuration> findByKeyAtOrganizationLevel(@Bind("organizationId") int organizationId, @Bind("key") String key);

    @Query(EVENT_FIND_BY_KEY)
    Optional<Configuration> findByKeyAtEventLevel(@Bind("eventId") int eventId, @Bind("organizationId") int organizationId, @Bind("key") String key);

    @Query(SUBSCRIPTION_DESCRIPTOR_FIND_BY_KEY)
    Optional<Configuration> findByKeyAtSubscriptionDescriptorLevel(@Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId,
                                                                   @Bind("organizationId") int organizationId,
                                                                   @Bind("key") String key);

    @Query(TICKET_CATEGORY_FIND_BY_KEY)
    Optional<Configuration> findByKeyAtCategoryLevel(@Bind("eventId") int eventId, @Bind("organizationId") int organizationId, @Bind("ticketCategoryId") int ticketCategoryId, @Bind("key") String key);

    @Query(SYSTEM_FIND_BY_KEY + " UNION ALL " + ORGANIZATION_FIND_BY_KEY + " UNION ALL " + EVENT_FIND_BY_KEY)
    List<Configuration> findByEventAndKey(@Bind("organizationId") int organizationId, @Bind("eventId") int eventId,
                                          @Bind("key") String key);

    @Query(SYSTEM_FIND_BY_KEY + " UNION ALL " + ORGANIZATION_FIND_BY_KEY + " UNION ALL " + SUBSCRIPTION_DESCRIPTOR_FIND_BY_KEY)
    List<Configuration> findBySubscriptionDescriptorAndKey(@Bind("organizationId") int organizationId,
                                                           @Bind("subscriptionDescriptorId") UUID id,
                                                           @Bind("key") String keyAsString);

    @Query(SYSTEM_FIND_BY_KEY + " UNION ALL " + ORGANIZATION_FIND_BY_KEY + " UNION ALL " + EVENT_FIND_BY_KEY + " UNION ALL " + TICKET_CATEGORY_FIND_BY_KEY)
    List<Configuration> findByTicketCategoryAndKey(@Bind("organizationId") int organizationId,
                                                   @Bind("eventId") int eventId,
                                                   @Bind("ticketCategoryId") int ticketCategoryId,
                                                   @Bind("key") String key);


    @Query("("+SELECT_FROM_SYSTEM+" where c_key in (:keys)) UNION ALL " +
        "("+SELECT_FROM_ORGANIZATION+" and c_key in (:keys))")
    List<ConfigurationKeyValuePathLevel> findByOrganizationAndKeys(@Bind("organizationId") int organizationId, @Bind("keys") Collection<String> keys);

    @Query("("+SELECT_FROM_SYSTEM+" where c_key in (:keys)) UNION ALL " +
        "("+SELECT_FROM_ORGANIZATION+" and c_key in (:keys)) UNION ALL " +
        "("+SELECT_FROM_EVENT+" and c_key in (:keys))")
    List<ConfigurationKeyValuePathLevel> findByEventAndKeys(@Bind("organizationId") int organizationId, @Bind("eventId") int eventId, @Bind("keys") Collection<String> keys);

    @Query("("+SELECT_FROM_SYSTEM+" where c_key in (:keys)) UNION ALL " +
        "("+SELECT_FROM_ORGANIZATION+" and c_key in (:keys)) UNION ALL " +
        "("+SELECT_FROM_SUBSCRIPTION_DESCRIPTOR+" and c_key in (:keys))")
    List<ConfigurationKeyValuePathLevel> findBySubscriptionDescriptorAndKeys(@Bind("organizationId") int organizationId, @Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId, @Bind("keys") Collection<String> keys);

    @Query("("+SELECT_FROM_SYSTEM+" where c_key in (:keys)) UNION ALL " +
        "("+SELECT_FROM_ORGANIZATION+" and c_key in (:keys)) UNION ALL " +
        "("+SELECT_FROM_EVENT+" and c_key in (:keys)) UNION ALL" +
        "("+SELECT_FROM_TICKET_CATEGORY+" and c_key in (:keys))")
    List<ConfigurationKeyValuePathLevel> findByTicketCategoryAndKeys(@Bind("organizationId") int organizationId, @Bind("eventId") int eventId, @Bind("ticketCategoryId") int ticketCategoryId, @Bind("keys") Collection<String> keys);

    @Query(SELECT_FROM_SYSTEM+" where c_key in (:keys)")
    List<ConfigurationKeyValuePathLevel> findByKeysAtSystemLevel(@Bind("keys") Collection<String> keys);
    
    @Query("DELETE FROM configuration where c_key = :key")
    void deleteByKey(@Bind("key") String key);

    @Query("DELETE FROM configuration_organization where c_key = :key and organization_id_fk = :organizationId")
    void deleteOrganizationLevelByKey(@Bind("key") String key, @Bind("organizationId") int organizationId);

    @Query("DELETE FROM configuration_purchase_context where c_key = :key and event_id_fk = :eventId")
    void deleteEventLevelByKey(@Bind("key") String key, @Bind("eventId") int eventId);

    @Query("DELETE FROM configuration_purchase_context where c_key = :key and subscription_descriptor_id_fk = :subscriptionDescriptorId")
    void deleteSubscriptionDescriptorLevelByKey(@Bind("key") String key, @Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId);

    @Query("DELETE FROM configuration_ticket_category where c_key = :key and event_id_fk = :eventId and ticket_category_id_fk = :categoryId")
    void deleteCategoryLevelByKey(@Bind("key") String key, @Bind("eventId") int eventId, @Bind("categoryId") int categoryId);

    @Query(INSERT_STATEMENT)
    int insert(@Bind("key") String key, @Bind("value") String value, @Bind("description") String description);

    @Query("INSERT into configuration_organization(organization_id_fk, c_key, c_value, description) values(:orgId, :key, :value, :description)")
    int insertOrganizationLevel(@Bind("orgId") int orgId, @Bind("key") String key, @Bind("value") String value, @Bind("description") String description);

    @Query("update configuration_organization set c_value = :value where organization_id_fk = :orgId and c_key = :key")
    int updateOrganizationLevel(@Bind("orgId") int orgId, @Bind("key") String key, @Bind("value") String value);

    @Query("update configuration_purchase_context set c_value = :value where event_id_fk = :eventId and organization_id_fk = :organizationId and c_key = :key")
    int updateEventLevel(@Bind("eventId") int eventId, @Bind("organizationId") int organizationId, @Bind("key") String key, @Bind("value") String value);

    @Query("update configuration_purchase_context set c_value = :value where subscription_descriptor_id_fk = :subscriptionDescriptorId and organization_id_fk = :organizationId and c_key = :key")
    int updateSubscriptionDescriptorLevel(@Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId,
                                          @Bind("organizationId") int organizationId,
                                          @Bind("key") String key,
                                          @Bind("value") String value);

    @Query("update configuration_ticket_category set c_value = :value where event_id_fk = :eventId and organization_id_fk = :organizationId and ticket_category_id_fk = :ticketCategoryId and c_key = :key")
    int updateCategoryLevel(@Bind("eventId") int eventId, @Bind("organizationId") int organizationId, @Bind("ticketCategoryId") int ticketCategoryId, @Bind("key") String key, @Bind("value") String value);

    @Query("INSERT into configuration_purchase_context(organization_id_fk, event_id_fk, c_key, c_value, description) values(:orgId, :eventId, :key, :value, :description)")
    int insertEventLevel(@Bind("orgId") int orgId, @Bind("eventId") int eventId, @Bind("key") String key, @Bind("value") String value, @Bind("description") String description);

    @Query("INSERT into configuration_purchase_context(organization_id_fk, subscription_descriptor_id_fk, c_key, c_value, description) values(:orgId, :subscriptionDescriptorId, :key, :value, :description)")
    int insertSubscriptionDescriptorLevel(@Bind("orgId") int orgId,
                         @Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId,
                         @Bind("key") String key,
                         @Bind("value") String value,
                         @Bind("description") String description);

    @Query("INSERT into configuration_ticket_category(organization_id_fk, event_id_fk, ticket_category_id_fk, c_key, c_value, description) values(:orgId, :eventId, :ticketCategoryId, :key, :value, :description)")
    int insertTicketCategoryLevel(@Bind("orgId") int orgId, @Bind("eventId") int eventId, @Bind("ticketCategoryId") int ticketCategoryId, @Bind("key") String key, @Bind("value") String value, @Bind("description") String description);

    @Query("UPDATE configuration set c_value = :value where c_key = :key")
    int update(@Bind("key") String existingKey, @Bind("value") String newValue);

    @Query("SELECT organization_id_fk FROM configuration_organization where c_key = :key and c_value = :value")
    Optional<Integer> findOrganizationIdByKeyAndValue(@Bind("key") String key, @Bind("value") String value);


    @Query("select c_value::jsonb from configuration where c_key = 'TRANSLATION_OVERRIDE' union all select '{}'::jsonb limit 1")
    @JSONData Map<String, Map<String, String>> getSystemOverrideMessages();

    @Query("select coalesce(jsonb_recursive_merge(a.c_value, b.c_value), '{}'::jsonb) from "+
        "(select c_value::jsonb from configuration where c_key = 'TRANSLATION_OVERRIDE' union all select '{}'::jsonb limit 1) a, "+
        "(select c_value::jsonb from configuration_organization where organization_id_fk = :orgId and c_key = 'TRANSLATION_OVERRIDE' union all select '{}'::jsonb limit 1) b")
    @JSONData Map<String, Map<String, String>> getOrganizationOverrideMessages(@Bind("orgId") int orgId);

    @Query("select coalesce(jsonb_recursive_merge(jsonb_recursive_merge(a.c_value, b.c_value), c.c_value), '{}'::jsonb) from "+
        "(select c_value::jsonb from configuration where c_key = 'TRANSLATION_OVERRIDE' union all select '{}'::jsonb limit 1) a, "+
        "(select c_value::jsonb from configuration_organization where organization_id_fk = :orgId and c_key = 'TRANSLATION_OVERRIDE' union all select '{}'::jsonb limit 1) b,"+
        "(select c_value::jsonb from configuration_purchase_context where organization_id_fk = :orgId and event_id_fk = :eventId and c_key = 'TRANSLATION_OVERRIDE' union all select '{}'::jsonb limit 1) c")
    @JSONData Map<String, Map<String, String>> getEventOverrideMessages(@Bind("orgId") int orgId, @Bind("eventId") int eventId);
}
