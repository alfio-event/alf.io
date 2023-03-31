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

import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.modification.TicketReservationModification;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.WaitingQueueRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.*;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.EventUtil.determineAvailableSeats;

@Component
public class WaitingQueueManager {

    private static final Logger log = LoggerFactory.getLogger(WaitingQueueManager.class);

    private final WaitingQueueRepository waitingQueueRepository;
    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final ConfigurationManager configurationManager;
    private final EventRepository eventRepository;
    private final ClockProvider clockProvider;

    public WaitingQueueManager(WaitingQueueRepository waitingQueueRepository,
                               TicketRepository ticketRepository,
                               TicketCategoryRepository ticketCategoryRepository,
                               ConfigurationManager configurationManager,
                               EventRepository eventRepository,
                               ClockProvider clockProvider) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.ticketRepository = ticketRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.configurationManager = configurationManager;
        this.eventRepository = eventRepository;
        this.clockProvider = clockProvider;
    }
    Stream<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> distributeSeats(Event event) {
        int eventId = event.getId();
        List<WaitingQueueSubscription> subscriptions = waitingQueueRepository.loadAllWaitingForUpdate(eventId);
        int waitingPeople = subscriptions.size();
        int waitingTickets = ticketRepository.countWaiting(eventId);

        if (waitingPeople == 0 && waitingTickets > 0) {
            ticketRepository.revertToFree(eventId);
        } else if (waitingPeople > 0 && waitingTickets > 0) {
            return distributeAvailableSeatsPeople(event, waitingPeople, waitingTickets);
        } else if(subscriptions.stream().anyMatch(WaitingQueueSubscription::isPreSales) && configurationManager.getFor(ENABLE_PRE_REGISTRATION, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault()) {
            return handlePreReservation(event, waitingPeople);
        }
        return Stream.empty();
    }

    private Stream<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> handlePreReservation(Event event, int waitingPeople) {
        List<TicketCategory> ticketCategories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        // Given that this Job runs more than once in a minute, in order to ensure that all the waiting list subscribers would get a seat *before*
        // all other people, we must process their a little bit before the sale period starts
        Optional<TicketCategory> categoryWithInceptionInFuture = ticketCategories.stream()
                .min(TicketCategory.COMPARATOR)
                .filter(t -> event.now(clockProvider).isBefore(t.getInception(event.getZoneId()).minusMinutes(5)));
        int ticketsNeeded = Math.min(waitingPeople, eventRepository.countExistingTickets(event.getId()));
        if(ticketsNeeded > 0) {
            preReserveIfNeeded(event, ticketsNeeded);
            if(categoryWithInceptionInFuture.isEmpty()) {
                return distributeAvailableSeatsStatus(event, Ticket.TicketStatus.PRE_RESERVED, () -> ticketsNeeded);
            }
        }
        return Stream.empty();
    }

    private void preReserveIfNeeded(Event event, int ticketsNeeded) {
        int eventId = event.getId();
        int alreadyReserved = ticketRepository.countPreReservedTickets(eventId);
        if(alreadyReserved < ticketsNeeded) {
            preReserveTickets(event, ticketsNeeded, eventId, alreadyReserved);
        }
    }

    private void preReserveTickets(Event event, int ticketsNeeded, int eventId, int alreadyReserved) {
        final int toBeGenerated = Math.abs(alreadyReserved - ticketsNeeded);
        EventStatisticView eventStatisticView = eventRepository.findStatisticsFor(eventId);
        Map<Integer, TicketCategoryStatisticView> ticketCategoriesStats = ticketCategoryRepository.findStatisticsForEventIdByCategoryId(eventId);
        List<Pair<Integer, TicketCategoryStatisticView>> collectedTickets = ticketCategoryRepository.findAllTicketCategories(eventId).stream()
                .filter(tc -> !tc.isAccessRestricted())
                .sorted(Comparator.comparing(t -> t.getExpiration(event.getZoneId())))
                .map(tc -> Pair.of(determineAvailableSeats(ticketCategoriesStats.get(tc.getId()), eventStatisticView), ticketCategoriesStats.get(tc.getId())))
                .collect(new PreReservedTicketDistributor(toBeGenerated));
        List<Integer> ids = collectedTickets.stream()
                .flatMap(p -> selectTicketsForPreReservation(eventId, p).stream())
                .collect(Collectors.toList());

        ticketRepository.preReserveTicket(ids);
    }

    private List<Integer> selectTicketsForPreReservation(int eventId, Pair<Integer, TicketCategoryStatisticView> p) {
        TicketCategoryStatisticView category = p.getValue();
        Integer amount = p.getKey();
        if(category.isBounded()) {
            return ticketRepository.selectFreeTicketsForPreReservation(eventId, amount, category.getId());
        }
        return ticketRepository.selectNotAllocatedFreeTicketsForPreReservation(eventId, amount);
    }


    private Stream<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> distributeAvailableSeatsPeople(Event event, int waitingPeople, int waitingTickets) {
        return distributeAvailableSeatsPeople(event, Ticket.TicketStatus.RELEASED, () -> Math.min(waitingPeople, waitingTickets));
    }

    private Stream<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> distributeAvailableSeatsStatus(Event event, Ticket.TicketStatus status, IntSupplier availableSeatSupplier) {
        int availableSeats = availableSeatSupplier.getAsInt();
        int eventId = event.getId();
        log.debug("processing {} subscribers from waiting list", availableSeats);
        List<TicketCategory> unboundedCategories = ticketCategoryRepository.findUnboundedOrderByExpirationDesc(eventId);
        Iterator<Ticket> tickets = ticketRepository.selectWaitingTicketsForUpdate(eventId, status.name(), availableSeats)
            .stream()
            .filter(t -> t.getCategoryId() != null || !unboundedCategories.isEmpty())
            .iterator();
        int expirationTimeout = configurationManager.getFor(WAITING_QUEUE_RESERVATION_TIMEOUT, ConfigurationLevel.event(event)).getValueAsIntOrDefault(4);
        ZonedDateTime expiration = event.now(clockProvider).plusHours(expirationTimeout).with(WorkingDaysAdjusters.defaultWorkingDays());

        if(!tickets.hasNext()) {
            log.warn("Unable to assign tickets, returning an empty stream");
            return Stream.empty();
        }
        return waitingQueueRepository.loadWaiting(eventId, availableSeats).stream()
            .map(wq -> Pair.of(wq, tickets.next()))
            .map(pair -> {
                TicketReservationModification ticketReservation = new TicketReservationModification();
                ticketReservation.setQuantity(1);
                Integer categoryId = Optional.ofNullable(pair.getValue().getCategoryId()).orElseGet(() -> findBestCategory(unboundedCategories, pair.getKey()).orElseThrow(RuntimeException::new).getId());
                ticketReservation.setTicketCategoryId(categoryId);
                return Pair.of(pair.getLeft(), new TicketReservationWithOptionalCodeModification(ticketReservation, Optional.empty()));
            })
            .map(pair -> Triple.of(pair.getKey(), pair.getValue(), expiration));
    }

    private Optional<TicketCategory> findBestCategory(List<TicketCategory> unboundedCategories, WaitingQueueSubscription subscription) {
        Integer selectedCategoryId = subscription.getSelectedCategoryId();
        Optional<TicketCategory> firstMatch = unboundedCategories.stream()
            .filter(tc -> selectedCategoryId == null || selectedCategoryId.equals(tc.getId()))
            .findFirst();
        if(firstMatch.isPresent()) {
            return firstMatch;
        } else {
            return unboundedCategories.stream().findFirst();
        }
    }

    public void fireReservationConfirmed(String reservationId) {
        updateStatus(reservationId, WaitingQueueSubscription.Status.ACQUIRED.toString());
    }

    public void fireReservationExpired(String reservationId) {
        waitingQueueRepository.bulkUpdateExpiredReservations(Collections.singletonList(reservationId));
    }

    public void cleanExpiredReservations(List<String> reservationIds) {
        waitingQueueRepository.bulkUpdateExpiredReservations(reservationIds);
    }

    private void updateStatus(String reservationId, String status) {
        waitingQueueRepository.updateStatusByReservationId(reservationId, status);
    }
}
