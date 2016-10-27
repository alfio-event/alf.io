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
import alfio.model.result.Result.ResultStatus;
import alfio.repository.*;
import alfio.util.MonetaryUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.ZonedDateTime;
import java.util.*;

import static alfio.util.EventUtil.generateEmptyTickets;
import static alfio.util.OptionalWrapper.optionally;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

@Component
@Log4j2
public class AdminReservationManager {

    private final EventManager eventManager;
    private final TicketReservationManager ticketReservationManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final SpecialPriceRepository specialPriceRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final EventRepository eventRepository;
    private final PlatformTransactionManager transactionManager;

    @Autowired
    public AdminReservationManager(EventManager eventManager,
                                   TicketReservationManager ticketReservationManager,
                                   PlatformTransactionManager transactionManager,
                                   TicketCategoryRepository ticketCategoryRepository,
                                   TicketRepository ticketRepository,
                                   NamedParameterJdbcTemplate jdbc,
                                   SpecialPriceRepository specialPriceRepository,
                                   TicketReservationRepository ticketReservationRepository,
                                   EventRepository eventRepository) {
        this.eventManager = eventManager;
        this.ticketReservationManager = ticketReservationManager;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.transactionManager = transactionManager;
        this.ticketRepository = ticketRepository;
        this.jdbc = jdbc;
        this.specialPriceRepository = specialPriceRepository;
        this.ticketReservationRepository = ticketReservationRepository;
        this.eventRepository = eventRepository;
    }

    public Result<Pair<TicketReservation, List<Ticket>>> createReservation(AdminReservationModification input, String eventName, String username) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(definition);
        try {
            Result<Pair<TicketReservation, List<Ticket>>> result = transactionalCreateReservation(input, eventName, username);
            if(result.isSuccess()) {
                transactionManager.commit(status);
            } else {
                transactionManager.rollback(status);
            }
            return result;
        } catch(Exception e) {
            transactionManager.rollback(status);
            return Result.error(singletonList(ErrorCode.custom("", e.getMessage())));
        }
    }

    private Result<Pair<TicketReservation, List<Ticket>>> transactionalCreateReservation(AdminReservationModification input, String eventName, String username) {
        return eventRepository.findOptionalByShortNameForUpdate(eventName)
            .flatMap(e -> optionally(() -> {
                eventManager.checkOwnership(e, username, e.getOrganizationId());
                return e;
            })).map(event -> processReservation(input, username, event))
            .orElseGet(() -> Result.error(singletonList(ErrorCode.EventError.NOT_FOUND)));
    }

    private Result<Pair<TicketReservation, List<Ticket>>> processReservation(AdminReservationModification input, String username, Event event) {
        return input.getTicketsInfo().stream()
            .map(ti -> checkCategoryCapacity(ti, event, input, username).map(c -> Pair.of(ti, c)))
            .map(t -> createReservation(t, event, input))
            .reduce((r1, r2) -> {
                boolean successful = r1.isSuccess() && r2.isSuccess();
                ResultStatus global = r1.isSuccess() ? r2.getStatus() : r1.getStatus();
                List<ErrorCode> errors = new ArrayList<>();
                if(!successful) {
                    errors.addAll(r1.getErrors());
                    errors.addAll(r2.getErrors());
                }
                return new Result<>(global, joinData(r1, r2), errors);
            }).orElseGet(() -> Result.error(singletonList(ErrorCode.custom("", "something went wrong..."))));
    }

    private Pair<TicketReservation, List<Ticket>> joinData(Result<Pair<TicketReservation, List<Ticket>>> t1, Result<Pair<TicketReservation, List<Ticket>>> t2) {
        if(!t1.isSuccess() || !t2.isSuccess()) {
            return null;
        }
        List<Ticket> tickets = new ArrayList<>();
        Pair<TicketReservation, List<Ticket>> data1 = t1.getData();
        tickets.addAll(data1.getRight());
        Pair<TicketReservation, List<Ticket>> data2 = t2.getData();
        tickets.addAll(data2.getRight());
        return Pair.of(data1.getLeft(), tickets);
    }

    private Result<Pair<TicketReservation, List<Ticket>>> createReservation(Result<Pair<TicketsInfo, TicketCategory>> input, Event event, AdminReservationModification arm) {
        return input.flatMap(t -> {
            TicketCategory category = t.getRight();
            TicketsInfo ticketsInfo = t.getLeft();
            String reservationId = UUID.randomUUID().toString();
            String specialPriceSessionId = UUID.randomUUID().toString();
            Date validity = Date.from(arm.getExpiration().toZonedDateTime(event.getZoneId()).toInstant());
            ticketReservationRepository.createNewReservation(reservationId, validity, null, arm.getLanguage());
            int categoryId = category.getId();
            List<Attendee> attendees = ticketsInfo.getAttendees();
            List<Integer> reservedForUpdate = ticketReservationManager.reserveTickets(event.getId(), categoryId, attendees.size(), singletonList(Ticket.TicketStatus.FREE));
            if(reservedForUpdate.size() != attendees.size()) {
                return Result.error(ErrorCode.CategoryError.NOT_ENOUGH_SEATS);
            }
            ticketRepository.reserveTickets(reservationId, reservedForUpdate, categoryId, arm.getLanguage(), category.getSrcPriceCts());
            Ticket ticket = ticketRepository.findById(reservedForUpdate.get(0), categoryId);
            TicketPriceContainer priceContainer = TicketPriceContainer.from(ticket, event, null);
            ticketRepository.updateTicketPrice(reservedForUpdate, categoryId, event.getId(), category.getSrcPriceCts(), MonetaryUtil.unitToCents(priceContainer.getFinalPrice()), MonetaryUtil.unitToCents(priceContainer.getVAT()), MonetaryUtil.unitToCents(priceContainer.getAppliedDiscount()));
            if(category.isAccessRestricted()) {
                specialPriceRepository.findActiveNotAssignedByCategoryId(categoryId)
                    .stream()
                    .limit(attendees.size())
                    .forEach(c -> specialPriceRepository.updateStatus(c.getId(), SpecialPrice.Status.PENDING.toString(), specialPriceSessionId));
            }
            assignTickets(attendees, reservedForUpdate);
            List<Ticket> tickets = reservedForUpdate.stream().map(id -> ticketRepository.findById(id, categoryId)).collect(toList());
            return Result.success(Pair.of(ticketReservationRepository.findReservationById(reservationId), tickets));
        });
    }

    private void assignTickets(List<Attendee> attendees, List<Integer> reservedForUpdate) {
        for(int i=0; i<reservedForUpdate.size(); i++) {
            Attendee attendee = attendees.get(i);
            if(!attendee.isEmpty()) {
                ticketRepository.updateTicketOwnerById(reservedForUpdate.get(i), attendee.getEmailAddress(), null, attendee.getFirstName(), attendee.getLastName());
            }
        }
    }

    private Result<TicketCategory> checkCategoryCapacity(TicketsInfo ti, Event event, AdminReservationModification reservation, String username) {
        return ti.getCategory().isExisting() ? checkExistingCategory(ti, event, username) : createCategory(ti, event, reservation, username);
    }

    private Result<TicketCategory> createCategory(TicketsInfo ti, Event event, AdminReservationModification reservation, String username) {
        AdminReservationModification.Category category = ti.getCategory();
        List<Attendee> attendees = ti.getAttendees();
        DateTimeModification inception = DateTimeModification.fromZonedDateTime(ZonedDateTime.now(event.getZoneId()));

        int tickets = attendees.size();
        TicketCategoryModification tcm = new TicketCategoryModification(category.getExistingCategoryId(), category.getName(), tickets,
            inception, reservation.getExpiration(), Collections.emptyMap(), category.getPrice(), true, "", true);
        int availableSeats = getAvailableSeats(event);
        int missingTickets = Math.max(tickets - availableSeats, 0);
        Event modified = increaseSeatsIfNeeded(ti, event, missingTickets, event);
        return eventManager.insertCategory(modified, tcm, username).map(id -> ticketCategoryRepository.getById(id, event.getId()));
    }

    private Event increaseSeatsIfNeeded(TicketsInfo ti, Event event, int missingTickets, Event modified) {
        if(missingTickets > 0 && ti.isAddSeatsIfNotAvailable()) {
            createMissingTickets(event, missingTickets);
            //update seats and reload event
            log.debug("adding {} extra seats to the event", missingTickets);
            eventRepository.updateAvailableSeats(event.getId(), event.getAvailableSeats() + missingTickets);
            modified = eventRepository.findById(event.getId());
        }
        return modified;
    }

    private int getAvailableSeats(Event event) {
        int allocation = ticketCategoryRepository.getTicketAllocation(event.getId());
        return event.getAvailableSeats() - allocation;
    }

    private Result<TicketCategory> checkExistingCategory(TicketsInfo ti, Event event, String username) {
        AdminReservationModification.Category category = ti.getCategory();
        List<Attendee> attendees = ti.getAttendees();
        int tickets = attendees.size();
        int eventId = event.getId();
        TicketCategory existing = ticketCategoryRepository.getById(category.getExistingCategoryId(), eventId);
        int existingCategoryId = existing.getId();
        int freeTicketsInCategory = ticketRepository.countFreeTickets(eventId, existingCategoryId);
        int availableSeats = getAvailableSeats(event);
        int missingTickets = Math.max(tickets - (freeTicketsInCategory + availableSeats), 0);
        Event modified = increaseSeatsIfNeeded(ti, event, missingTickets, event);
        if(freeTicketsInCategory < tickets && existing.isBounded()) {
            int maxTickets = existing.getMaxTickets() + (tickets - freeTicketsInCategory);

            TicketCategoryModification tcm = new TicketCategoryModification(existingCategoryId, existing.getName(), maxTickets,
                DateTimeModification.fromZonedDateTime(existing.getInception(modified.getZoneId())), DateTimeModification.fromZonedDateTime(existing.getExpiration(event.getZoneId())),
                Collections.emptyMap(), existing.getPrice(), existing.isAccessRestricted(), "", true);
            return eventManager.updateCategory(existingCategoryId, modified, tcm, username);
        }
        return Result.success(existing);
    }

    private void createMissingTickets(Event event, int tickets) {
        final MapSqlParameterSource[] params = generateEmptyTickets(event, Date.from(ZonedDateTime.now(event.getZoneId()).toInstant()), tickets).toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(ticketRepository.bulkTicketInitialization(), params);
    }


}
