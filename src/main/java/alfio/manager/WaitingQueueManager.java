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
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
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
@Log4j2
@AllArgsConstructor
public class WaitingQueueManager {

    private final WaitingQueueRepository waitingQueueRepository;
    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final ConfigurationManager configurationManager;
    private final EventStatisticsManager eventStatisticsManager;
    private final NotificationManager notificationManager;
    private final TemplateManager templateManager;
    private final MessageSourceManager messageSourceManager;
    private final OrganizationRepository organizationRepository;
    private final EventRepository eventRepository;
    private final ExtensionManager extensionManager;
    private final ClockProvider clockProvider;

    public boolean subscribe(Event event, CustomerName customerName, String email, Integer selectedCategoryId, Locale userLanguage) {
        try {
            if(configurationManager.getFor(STOP_WAITING_QUEUE_SUBSCRIPTIONS, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault()) {
                log.info("waiting list subscription denied for event {} ({})", event.getShortName(), event.getId());
                return false;
            }
            WaitingQueueSubscription.Type subscriptionType = getSubscriptionType(event);
            validateSubscriptionType(event, subscriptionType);
            validateSelectedCategoryId(event.getId(), selectedCategoryId);
            AffectedRowCountAndKey<Integer> key = waitingQueueRepository.insert(event.getId(), customerName.getFullName(), customerName.getFirstName(), customerName.getLastName(), email, event.now(clockProvider), userLanguage.getLanguage(), subscriptionType, selectedCategoryId);
            notifySubscription(event, customerName, email, userLanguage, subscriptionType);
            extensionManager.handleWaitingQueueSubscription(waitingQueueRepository.loadById(key.getKey()));
            return true;
        } catch(DuplicateKeyException e) {
            return true;//why are you subscribing twice?
        } catch (Exception e) {
            log.error("error during subscription", e);
            return false;
        }
    }

    private void validateSelectedCategoryId(int eventId, Integer selectedCategoryId) {
        Optional.ofNullable(selectedCategoryId).ifPresent(id -> Validate.isTrue(ticketCategoryRepository.findUnboundedOrderByExpirationDesc(eventId).stream().anyMatch(c -> id.equals(c.getId()))));
    }

    private void notifySubscription(Event event, CustomerName name, String email, Locale userLanguage, WaitingQueueSubscription.Type subscriptionType) {
        var messageSource = messageSourceManager.getMessageSourceFor(event);
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        Map<String, Object> model = TemplateResource.buildModelForWaitingQueueJoined(organization, event, name);
        notificationManager.sendSimpleEmail(event, null, email, messageSource.getMessage("email-waiting-queue.subscribed.subject", new Object[]{event.getDisplayName()}, userLanguage),
                () -> templateManager.renderTemplate(event, TemplateResource.WAITING_QUEUE_JOINED, model, userLanguage));
        if(configurationManager.getFor(ENABLE_WAITING_QUEUE_NOTIFICATION, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault()) {
            String adminTemplate = messageSource.getMessage("email-waiting-queue.subscribed.admin.text",
                    new Object[] {subscriptionType, event.getDisplayName()}, Locale.ENGLISH);
            notificationManager.sendSimpleEmail(event, null, organization.getEmail(), messageSource.getMessage("email-waiting-queue.subscribed.admin.subject",
                            new Object[]{event.getDisplayName()}, Locale.ENGLISH),
                    () -> RenderedTemplate.plaintext(templateManager.renderString(event, adminTemplate, model, Locale.ENGLISH, TemplateManager.TemplateOutput.TEXT), model));
        }

    }

    private WaitingQueueSubscription.Type getSubscriptionType(Event event) {
        ZonedDateTime now = event.now(clockProvider);
        return ticketCategoryRepository.findAllTicketCategories(event.getId()).stream()
                .filter(tc -> !tc.isAccessRestricted())
                .filter(tc -> now.isAfter(tc.getInception(event.getZoneId())))
                .findFirst()
                .map(tc -> WaitingQueueSubscription.Type.SOLD_OUT)
                .orElse(WaitingQueueSubscription.Type.PRE_SALES);
    }

    private void validateSubscriptionType(EventAndOrganizationId event, WaitingQueueSubscription.Type type) {
        if(type == WaitingQueueSubscription.Type.PRE_SALES) {
            Validate.isTrue(configurationManager.getFor(ENABLE_PRE_REGISTRATION, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault(), "PRE_SALES Waiting list is not active");
        } else {
            Validate.isTrue(eventStatisticsManager.noSeatsAvailable().test(event), "SOLD_OUT Waiting list is not active");
        }
    }

    public List<WaitingQueueSubscription> loadAllSubscriptionsForEvent(int eventId) {
        return waitingQueueRepository.loadAllWaiting(eventId);
    }

    public Optional<WaitingQueueSubscription> updateSubscriptionStatus(int id, WaitingQueueSubscription.Status newStatus, WaitingQueueSubscription.Status currentStatus) {
        return Optional.of(waitingQueueRepository.updateStatus(id, newStatus, currentStatus))
            .filter(i -> i > 0)
            .map(i -> waitingQueueRepository.loadById(id));
    }

    public int countSubscribers(int eventId) {
        return waitingQueueRepository.countWaitingPeople(eventId);
    }

    Stream<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> distributeSeats(Event event) {
        int eventId = event.getId();
        List<WaitingQueueSubscription> subscriptions = waitingQueueRepository.loadAllWaitingForUpdate(eventId);
        int waitingPeople = subscriptions.size();
        int waitingTickets = ticketRepository.countWaiting(eventId);

        if (waitingPeople == 0 && waitingTickets > 0) {
            ticketRepository.revertToFree(eventId);
        } else if (waitingPeople > 0 && waitingTickets > 0) {
            return distributeAvailableSeats(event, waitingPeople, waitingTickets);
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
                return distributeAvailableSeats(event, Ticket.TicketStatus.PRE_RESERVED, () -> ticketsNeeded);
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


    private Stream<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> distributeAvailableSeats(Event event, int waitingPeople, int waitingTickets) {
        return distributeAvailableSeats(event, Ticket.TicketStatus.RELEASED, () -> Math.min(waitingPeople, waitingTickets));
    }

    private Stream<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> distributeAvailableSeats(Event event, Ticket.TicketStatus status, IntSupplier availableSeatSupplier) {
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
