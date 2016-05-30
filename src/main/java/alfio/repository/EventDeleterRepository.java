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

@QueryRepository
public interface EventDeleterRepository {

	@Query("delete from waiting_queue where event_id = :eventId")
	int deleteWaitingQueue(@Bind("eventId") int eventId);
	
	@Query("delete from plugin_log where event_id = :eventId")
	int deletePluginLog(@Bind("eventId") int eventId);
	
	@Query("delete from plugin_configuration where event_id = :eventId")
	int deletePluginConfiguration(@Bind("eventId") int eventId);
	
	@Query("delete from configuration_event where event_id_fk = :eventId")
	int deleteConfigurationEvent(@Bind("eventId") int eventId);

	@Query("delete from configuration_ticket_category where event_id_fk = :eventId")
	int deleteConfigurationTicketCategory(@Bind("eventId") int eventId);
	
	@Query("delete from email_message where event_id = :eventId")
	int deleteEmailMessage(@Bind("eventId") int eventId);
	
	@Query("delete from ticket_field_value where ticket_field_configuration_id_fk in (select id from ticket_field_configuration where event_id_fk = :eventId and context = 'ATTENDEE')")
	int deleteTicketFieldValue(@Bind("eventId") int eventId);
	
	@Query("delete from ticket_field_description where ticket_field_configuration_id_fk in (select id from ticket_field_configuration where event_id_fk = :eventId)")
	int deleteFieldDescription(@Bind("eventId") int eventId);

    @Query("delete from additional_service_field_value where ticket_field_configuration_id_fk in (select id from ticket_field_configuration where event_id_fk = :eventId and context = 'ADDITIONAL_SERVICE')")
    int deleteAdditionalServiceFieldValue(@Bind("eventId") int eventId);

    @Query("delete from additional_service_description where additional_service_id_fk in (select id from additional_service where event_id_fk = :eventId)")
    int deleteAdditionalServiceDescriptions(@Bind("eventId") int eventId);

    @Query("delete from additional_service_item where additional_service_id_fk in (select id from additional_service where event_id_fk = :eventId)")
    int deleteAdditionalServiceItems(@Bind("eventId") int eventId);

    @Query("delete from additional_service where event_id_fk = :eventId")
    int deleteAdditionalServices(@Bind("eventId") int eventId);

	@Query("delete from ticket_field_configuration where event_id_fk= :eventId")
	int deleteTicketFieldConfiguration(@Bind("eventId") int eventId);
	
	@Query("delete from event_migration where event_id = :eventId")
	int deleteEventMigration(@Bind("eventId") int eventId);
	
	@Query("delete from sponsor_scan where event_id = :eventId")
	int deleteSponsorScan(@Bind("eventId") int eventId);
	
	@Query("delete from ticket where event_id = :eventId")
	int deleteTicket(@Bind("eventId") int eventId);
	
	//tickets_reservation will remain in the system though
	@Query("update tickets_reservation set promo_code_id_fk = null where promo_code_id_fk in (select id from promo_code where event_id_fk = :eventId)")
	int resetTicketReservation(@Bind("eventId") int eventId);
	
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
}
