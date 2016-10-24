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

import alfio.model.*;
import alfio.model.decorator.TicketPriceContainer;
import alfio.model.modification.AdminReservationModification;
import alfio.model.modification.AdminReservationModification.Attendee;
import alfio.model.modification.AdminReservationModification.TicketsInfo;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.repository.SpecialPriceRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.TicketReservationRepository;
import alfio.util.MonetaryUtil;
import alfio.util.OptionalWrapper;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static alfio.util.EventUtil.generateEmptyTickets;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

@Component
@Transactional
public class CustomReservationManager {

    private final EventManager eventManager;
    private final TicketReservationManager ticketReservationManager;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final SpecialPriceRepository specialPriceRepository;
    private final TicketReservationRepository ticketReservationRepository;

    @Autowired
    public CustomReservationManager(EventManager eventManager,
                                    TicketReservationManager ticketReservationManager,
                                    PlatformTransactionManager transactionManager,
                                    TicketCategoryRepository ticketCategoryRepository,
                                    TicketRepository ticketRepository,
                                    NamedParameterJdbcTemplate jdbc,
                                    SpecialPriceRepository specialPriceRepository,
                                    TicketReservationRepository ticketReservationRepository) {
        this.eventManager = eventManager;
        this.ticketReservationManager = ticketReservationManager;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager, new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.ticketRepository = ticketRepository;
        this.jdbc = jdbc;
        this.specialPriceRepository = specialPriceRepository;
        this.ticketReservationRepository = ticketReservationRepository;
    }

    public Result<Pair<TicketReservation, List<Ticket>>> createReservation(AdminReservationModification input, String eventName, String username) {
        Result<Triple<TicketReservation, List<Ticket>, Integer>> result = requiresNewTransactionTemplate.execute((state) -> internalCreate(input, eventName, username));
        result.ifSuccess((triple) -> {/* TODO update event seats if needed */});
        return result.map(triple -> Pair.of(triple.getLeft(), triple.getMiddle()));
    }

    private Result<Triple<TicketReservation, List<Ticket>, Integer>> internalCreate(AdminReservationModification input, String eventName, String username) {
        return OptionalWrapper.optionally(() -> eventManager.getSingleEvent(eventName, username))
            .map(e -> {
                //TODO create reservation, return tickets difference
                input.getTicketsInfo().stream()
                    .map(ti -> checkCategoryCapacity(ti, e, input, username).map(p -> Triple.of(ti, p.getLeft(), p.getRight())))
                    .map(t -> createReservation(t, e, input));

                return Result.success(Triple.of((TicketReservation) null, Collections.<Ticket>emptyList(), 1));
            })
            .orElseGet(() -> Result.error(Collections.singletonList(ErrorCode.EventError.NOT_FOUND)));

    }

    private Result<Triple<TicketReservation, List<Ticket>, Integer>> createReservation(Result<Triple<TicketsInfo, TicketCategory, Integer>> input, Event event, AdminReservationModification arm) {
        return input.map(t -> {
            TicketCategory category = t.getMiddle();
            TicketsInfo ticketsInfo = t.getLeft();
            String reservationId = UUID.randomUUID().toString();
            String specialPriceSessionId = UUID.randomUUID().toString();
            Date validity = Date.from(arm.getExpiration().toZonedDateTime(event.getZoneId()).toInstant());
            ticketReservationRepository.createNewReservation(reservationId, validity, null, arm.getLanguage());
            int categoryId = category.getId();
            List<Integer> reservedForUpdate = ticketReservationManager.reserveTickets(event.getId(), categoryId, ticketsInfo.getAttendees().size(), singletonList(Ticket.TicketStatus.FREE));
            Ticket ticket = ticketRepository.findById(reservedForUpdate.get(0), categoryId);
            TicketPriceContainer priceContainer = TicketPriceContainer.from(ticket, event, null);
            ticketRepository.updateTicketPrice(reservedForUpdate, categoryId, event.getId(), category.getSrcPriceCts(), MonetaryUtil.unitToCents(priceContainer.getFinalPrice()), MonetaryUtil.unitToCents(priceContainer.getVAT()), MonetaryUtil.unitToCents(priceContainer.getAppliedDiscount()));
            if(category.isAccessRestricted()) {
                specialPriceRepository.findActiveNotAssignedByCategoryId(categoryId)
                    .stream()
                    .limit(ticketsInfo.getAttendees().size())
                    .forEach(c -> specialPriceRepository.updateStatus(c.getId(), SpecialPrice.Status.PENDING.toString(), specialPriceSessionId));
            }
            List<Ticket> tickets = reservedForUpdate.stream().map(id -> ticketRepository.findById(id, categoryId)).collect(toList());
            return Triple.of(ticketReservationRepository.findReservationById(reservationId), tickets, t.getRight());
        });
    }

    private Result<Pair<TicketCategory, Integer>> checkCategoryCapacity(TicketsInfo ti, Event event, AdminReservationModification reservation, String username) {
        return ti.getCategory().isExisting() ? checkExistingCategory(ti, event, reservation, username) : createCategory(ti, event, reservation, username);
    }

    private Result<Pair<TicketCategory, Integer>> createCategory(TicketsInfo ti, Event event, AdminReservationModification reservation, String username) {
        AdminReservationModification.Category category = ti.getCategory();
        List<Attendee> attendees = ti.getAttendees();
        DateTimeModification inception = DateTimeModification.fromZonedDateTime(ZonedDateTime.now(event.getZoneId()));

        int tickets = attendees.size();
        TicketCategoryModification tcm = new TicketCategoryModification(category.getExistingCategoryId(), category.getName(), tickets,
            inception, reservation.getExpiration(), Collections.emptyMap(), category.getPrice(), true, "", true);
        int availableSeats = getAvailableSeats(event);
        int missingTickets = Math.max(tickets - availableSeats, 0);
        if(missingTickets > 0) {
            createMissingTickets(event, missingTickets);
        }
        return eventManager.insertCategory(event, tcm, username).map(id -> Pair.of(ticketCategoryRepository.getById(id, event.getId()), missingTickets));
    }

    private int getAvailableSeats(Event event) {
        int allocation = ticketCategoryRepository.getTicketAllocation(event.getId());
        return event.getAvailableSeats() - allocation;
    }

    private Result<Pair<TicketCategory, Integer>> checkExistingCategory(TicketsInfo ti, Event event, AdminReservationModification reservation, String username) {
        AdminReservationModification.Category category = ti.getCategory();
        List<Attendee> attendees = ti.getAttendees();
        int tickets = attendees.size();
        int eventId = event.getId();
        TicketCategory existing = ticketCategoryRepository.getById(category.getExistingCategoryId(), eventId);
        int existingCategoryId = existing.getId();
        int freeTicketsInCategory = ticketRepository.countFreeTickets(eventId, existingCategoryId);
        int availableSeats = getAvailableSeats(event);
        int missingTickets = Math.max(tickets - (freeTicketsInCategory + availableSeats), 0);
        if(missingTickets > 0) {
            createMissingTickets(event, missingTickets);
        }
        if(freeTicketsInCategory < tickets && existing.isBounded()) {
            int maxTickets = existing.getMaxTickets() + (tickets - freeTicketsInCategory);

            TicketCategoryModification tcm = new TicketCategoryModification(existingCategoryId, existing.getName(), maxTickets,
                DateTimeModification.fromZonedDateTime(existing.getInception(event.getZoneId())), DateTimeModification.fromZonedDateTime(existing.getExpiration(event.getZoneId())),
                Collections.emptyMap(), existing.getPrice(), existing.isAccessRestricted(), "", true);
            return eventManager.updateCategory(existingCategoryId, event, tcm, username).map(c -> Pair.of(c, missingTickets));
        }
        return Result.success(Pair.of(existing, missingTickets));
    }

    private void createMissingTickets(Event event, int tickets) {
        final MapSqlParameterSource[] params = generateEmptyTickets(event, Date.from(ZonedDateTime.now(event.getZoneId()).toInstant()), tickets).toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(ticketRepository.bulkTicketInitialization(), params);
    }


}
