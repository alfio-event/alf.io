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

import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@QueryRepository
public interface EventDeleterRepository {

    Logger LOGGER = LoggerFactory.getLogger(EventDeleterRepository.class);

	@Query("delete from waiting_queue where event_id = :eventId")
	int deleteWaitingQueue(@Bind("eventId") int eventId);
	
	@Query("delete from configuration_event where event_id_fk = :eventId")
	int deleteConfigurationEvent(@Bind("eventId") int eventId);

    @Query("delete from configuration_purchase_context where event_id_fk = :eventId")
    int deleteConfigurationPurchaseContext(@Bind("eventId") int eventId);

	@Query("delete from configuration_ticket_category where event_id_fk = :eventId")
	int deleteConfigurationTicketCategory(@Bind("eventId") int eventId);
	
	@Query("delete from email_message where event_id = :eventId")
	int deleteEmailMessage(@Bind("eventId") int eventId);
	
	@Query("delete from purchase_context_field_value fv using purchase_context_field_configuration fc where fv.field_configuration_id_fk = fc.id and fc.event_id_fk = :eventId")
	int deleteFieldValues(@Bind("eventId") int eventId);
	
	@Query("delete from purchase_context_field_description d using purchase_context_field_configuration c where d.field_configuration_id_fk = c.id and c.event_id_fk = :eventId")
	int deleteFieldDescription(@Bind("eventId") int eventId);

    @Query("delete from additional_service_description where additional_service_id_fk in (select id from additional_service where event_id_fk = :eventId)")
    int deleteAdditionalServiceDescriptions(@Bind("eventId") int eventId);

    @Query("delete from additional_service_item where event_id_fk = :eventId")
    int deleteAdditionalServiceItems(@Bind("eventId") int eventId);

    @Query("delete from additional_service where event_id_fk = :eventId")
    int deleteAdditionalServices(@Bind("eventId") int eventId);

	@Query("delete from purchase_context_field_configuration where event_id_fk = :eventId")
	int deleteFieldConfigurations(@Bind("eventId") int eventId);
	
	@Query("delete from event_migration where event_id = :eventId")
	int deleteEventMigration(@Bind("eventId") int eventId);
	
	@Query("delete from sponsor_scan where event_id = :eventId")
	int deleteSponsorScan(@Bind("eventId") int eventId);
	
	@Query("delete from ticket where event_id = :eventId")
	int deleteTicket(@Bind("eventId") int eventId);

	@Query("delete from tickets_reservation where event_id_fk = :eventId")
	int deleteReservation(@Bind("eventId") int eventId);

	@Query("delete from special_price where ticket_category_id in (select id from ticket_category where event_id = :eventId)")
    int deleteSpecialPrice(@Bind("eventId") int eventId);

	@Query("delete from promo_code where event_id_fk = :eventId")
	int deletePromoCode(@Bind("eventId") int eventId);
	
	@Query("delete from ticket_category_text where ticket_category_id_fk in (select id from ticket_category where event_id = :eventId)")
	int deleteTicketCategoryText(@Bind("eventId") int eventId);
	
	@Query("delete from ticket_category where event_id = :eventId")
	int deleteTicketCategory(@Bind("eventId") int eventId);
	
	@Query("delete from event_description_text where event_id_fk  = :eventId")
	int deleteEventDescription(@Bind("eventId") int eventId);
	
	@Query("delete from event where id = :eventId")
	int deleteEvent(@Bind("eventId") int eventId);

    @Query("delete from resource_event where event_id_fk = :eventId")
    int deleteResources(@Bind("eventId") int eventId);

    @Query("delete from scan_audit where event_id_fk = :eventId")
    int deleteScanAudit(@Bind("eventId") int eventId);

    @Query("delete from b_transaction where reservation_id in (select id from tickets_reservation where event_id_fk = :eventId)")
    int deleteTransactions(@Bind("eventId") int eventId);

    @Query("delete from group_link where event_id_fk = :eventId")
    int deleteGroupLinks(@Bind("eventId") int eventId);

    @Query("delete from whitelisted_ticket where group_link_id_fk in(select id from group_link where event_id_fk = :eventId)")
    int deleteWhitelistedTickets(@Bind("eventId") int eventId);

    @Query("delete from billing_document where event_id_fk = :eventId")
    int deleteBillingDocuments(@Bind("eventId") int eventId);

    @Query("delete from poll where event_id_fk = :eventId")
    int deletePolls(@Bind("eventId") int eventId);

    @Query("delete from subscription_event where event_id_fk = :eventId")
    int deleteSubscriptionLinks(@Bind("eventId") int eventId);

    @Deprecated
    @Query("delete from ticket_field_value where ticket_field_configuration_id_fk in (select id from ticket_field_configuration where event_id_fk = :eventId)")
    int deleteLegacyTicketFieldValue(@Bind("eventId") int eventId);

    @Deprecated
    @Query("delete from ticket_field_description where ticket_field_configuration_id_fk in (select id from ticket_field_configuration where event_id_fk = :eventId)")
    int deleteLegacyTicketFieldDescription(@Bind("eventId") int eventId);

    @Deprecated
    @Query("delete from ticket_field_configuration where event_id_fk= :eventId")
    int deleteLegacyTicketFieldConfiguration(@Bind("eventId") int eventId);

    default void deleteAllForEvent(int eventId) {
        deletePolls(eventId);
        deleteWaitingQueue(eventId);
        deleteWhitelistedTickets(eventId);
        deleteGroupLinks(eventId);
        deleteConfigurationEvent(eventId);
        deleteConfigurationPurchaseContext(eventId);
        deleteConfigurationTicketCategory(eventId);
        deleteEmailMessage(eventId);

        // legacy, replaced by purchase_context_* tables
        deleteLegacyTicketFieldValue(eventId);
        deleteLegacyTicketFieldDescription(eventId);
        deleteLegacyTicketFieldConfiguration(eventId);
        //

        int deletedFieldValues = deleteFieldValues(eventId);
        LOGGER.info("deleted {} field values", deletedFieldValues);
        int deletedDescriptions = deleteFieldDescription(eventId);
        LOGGER.info("deleted {} field descriptions", deletedDescriptions);
        deleteAdditionalServiceDescriptions(eventId);
        deleteAdditionalServiceItems(eventId);
        int deletedConfigurations = deleteFieldConfigurations(eventId);
        LOGGER.info("deleted {} field configurations", deletedConfigurations);
        deleteAdditionalServices(eventId);
        deleteEventMigration(eventId);
        deleteSponsorScan(eventId);
        deleteTicket(eventId);
        deleteTransactions(eventId);
        deleteBillingDocuments(eventId);
        deleteReservation(eventId);
        deleteSpecialPrice(eventId);
        deletePromoCode(eventId);
        deleteTicketCategoryText(eventId);
        deleteTicketCategory(eventId);
        deleteEventDescription(eventId);
        deleteResources(eventId);
        deleteScanAudit(eventId);
        deleteSubscriptionLinks(eventId);
        deleteEvent(eventId);
    }

}
