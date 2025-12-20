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
package alfio.manager;

import alfio.model.AdditionalServiceItem;
import alfio.model.Event;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.ReservationIdAndEventId;
import alfio.model.system.command.CleanupReservations;
import alfio.repository.*;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class EventReservationManager {

    private static final Logger log = LoggerFactory.getLogger(EventReservationManager.class);
    private final SpecialPriceRepository specialPriceRepository;
    private final GroupManager groupManager;
    private final TicketRepository ticketRepository;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final AdditionalServiceManager additionalServiceManager;
    private final TicketReservationRepository ticketReservationRepository;
    private final EventRepository eventRepository;
    private final ExtensionManager extensionManager;
    private final BillingDocumentRepository billingDocumentRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;


    public EventReservationManager(SpecialPriceRepository specialPriceRepository,
                                   GroupManager groupManager,
                                   TicketRepository ticketRepository,
                                   AdditionalServiceItemRepository additionalServiceItemRepository,
                                   AdditionalServiceManager additionalServiceManager,
                                   TicketReservationRepository ticketReservationRepository,
                                   EventRepository eventRepository,
                                   ExtensionManager extensionManager,
                                   BillingDocumentRepository billingDocumentRepository, NamedParameterJdbcTemplate jdbcTemplate) {
        this.specialPriceRepository = specialPriceRepository;
        this.groupManager = groupManager;
        this.ticketRepository = ticketRepository;
        this.additionalServiceItemRepository = additionalServiceItemRepository;
        this.additionalServiceManager = additionalServiceManager;
        this.ticketReservationRepository = ticketReservationRepository;
        this.eventRepository = eventRepository;
        this.extensionManager = extensionManager;
        this.billingDocumentRepository = billingDocumentRepository;
        this.jdbcTemplate = jdbcTemplate;
    }


    @EventListener(CleanupReservations.class)
    public void cleanupReservations(CleanupReservations cleanupReservations) {

        if (cleanupReservations.purchaseContext() != null && !cleanupReservations.purchaseContext().ofType(PurchaseContextType.event)) {
            return;
        }
        specialPriceRepository.resetToFreeAndCleanupForReservation(cleanupReservations.reservationIds());
        ticketRepository.resetCategoryIdForUnboundedCategories(cleanupReservations.reservationIds());
        if (cleanupReservations.purchaseContext() instanceof Event event) {
            for (String reservationId : cleanupReservations.reservationIds()) {
                cleanupReferences(cleanupReservations.expired(), reservationId, event, cleanupReservations.afterRelease());
            }
            notifyExtensions(event, cleanupReservations.reservationIds(), cleanupReservations);
        } else {
            Map<Integer, List<ReservationIdAndEventId>> reservationIdsByEvent = ticketReservationRepository
                .getReservationIdAndEventId(cleanupReservations.reservationIds())
                .stream()
                .collect(Collectors.groupingBy(ReservationIdAndEventId::getEventId));
            reservationIdsByEvent.forEach((eventId, reservations) -> {
                Event event = eventRepository.findById(eventId);
                List<String> reservationIds = reservations.stream().map(ReservationIdAndEventId::getId).toList();
                for (String reservationId : reservationIds) {
                    cleanupReferences(cleanupReservations.expired(), reservationId, event, cleanupReservations.afterRelease());
                }
                notifyExtensions(event, reservationIds, cleanupReservations);
                billingDocumentRepository.deleteForReservations(reservationIds, eventId);
            });
        }
    }

    private void cleanupReferences(boolean expired, String reservationId, Event event, boolean afterRelease) {
        groupManager.deleteWhitelistedTicketsForReservation(reservationId);
        int deletedItems = additionalServiceItemRepository.deleteAdditionalServiceItemsByReservationId(event.getId(), reservationId);
        int updatedItems = additionalServiceItemRepository.revertAdditionalServiceItemsByReservationId(event.getId(), reservationId);
        log.debug("Deleted {} and updated {} additionalServiceItems for reservation {}", deletedItems, updatedItems, reservationId);
        int updatedAS = additionalServiceManager.updateStatusForReservationId(event.getId(), reservationId, expired ? AdditionalServiceItem.AdditionalServiceItemStatus.EXPIRED : AdditionalServiceItem.AdditionalServiceItemStatus.CANCELLED);

        var batchUpdate = ticketRepository.findTicketIdsInReservation(reservationId).stream()
            .map(id -> new MapSqlParameterSource("ticketId", id)
                .addValue("reservationId", reservationId)
                .addValue("eventId", event.getId())
                .addValue("newUuid", UUID.randomUUID().toString())
                .addValue("newPublicUuid", UUID.randomUUID())
            ).toArray(SqlParameterSource[]::new);

        int updatedTickets = Arrays.stream(jdbcTemplate.batchUpdate(ticketRepository.batchReleaseTickets(), batchUpdate)).sum();
        Validate.isTrue(afterRelease || updatedTickets  + updatedAS > 0, "no items have been updated");
    }

    private void notifyExtensions(Event event, List<String> reservationIds, CleanupReservations cleanupReservations) {
        if (cleanupReservations.expired()) {
            extensionManager.handleReservationsExpired(event, reservationIds);
        } else if(cleanupReservations.creditNoteIssued()) {
            extensionManager.handleReservationsCreditNoteIssuedForEvent(event, reservationIds);
        } else {
            extensionManager.handleReservationsCancelled(event, reservationIds);
        }
    }
}
