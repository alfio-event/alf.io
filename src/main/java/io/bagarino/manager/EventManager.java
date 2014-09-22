/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.manager;

import io.bagarino.manager.user.UserManager;
import io.bagarino.model.Event;
import io.bagarino.model.Ticket;
import io.bagarino.model.TicketCategory;
import io.bagarino.model.TicketReservation;
import io.bagarino.model.modification.EventModification;
import io.bagarino.model.transaction.PaymentProxy;
import io.bagarino.repository.EventRepository;
import io.bagarino.repository.TicketCategoryRepository;
import io.bagarino.repository.TicketRepository;
import io.bagarino.repository.join.EventOrganizationRepository;
import io.bagarino.repository.join.EventTicketCategoryRepository;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.joining;

@Component
public class EventManager {

    public static final BigDecimal HUNDRED = new BigDecimal("100.0");
    private final UserManager userManager;
    private final EventOrganizationRepository eventOrganizationRepository;
    private final EventRepository eventRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final EventTicketCategoryRepository eventTicketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final NamedParameterJdbcTemplate jdbc;

    @Autowired
    public EventManager(UserManager userManager,
                        EventOrganizationRepository eventOrganizationRepository,
                        EventRepository eventRepository,
                        TicketCategoryRepository ticketCategoryRepository,
                        EventTicketCategoryRepository eventTicketCategoryRepository,
                        TicketRepository ticketRepository,
                        NamedParameterJdbcTemplate jdbc) {
        this.userManager = userManager;
        this.eventOrganizationRepository = eventOrganizationRepository;
        this.eventRepository = eventRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.eventTicketCategoryRepository = eventTicketCategoryRepository;
        this.ticketRepository = ticketRepository;
        this.jdbc = jdbc;
    }

    public List<Event> getAllEvents(String username) {
        return userManager.findUserOrganizations(username)
                    .parallelStream()
                    .flatMap(o -> eventOrganizationRepository.findByOrganizationId(o.getId()).stream())
                    .map(eo -> eventRepository.findById(eo.getEventId()))
                    .collect(Collectors.toList());
    }

    @Transactional
    public void createEvent(EventModification em) {
        int eventId = insertEvent(em);
        distributeSeats(em, eventId);
        Date creation = new Date();
        Event event = eventRepository.findById(eventId);
        eventOrganizationRepository.create(eventId, em.getOrganizationId());
        final BigDecimal regularPrice = event.getRegularPrice();
        final MapSqlParameterSource[] params = prepareBulkInsertParameters(eventId, creation, event, regularPrice);
        jdbc.batchUpdate(ticketRepository.bulkTicketInitialization(), params);

    }

    private MapSqlParameterSource[] prepareBulkInsertParameters(int eventId,
                                                                Date creation,
                                                                Event event,
                                                                BigDecimal regularPrice) {
        return eventTicketCategoryRepository.findByEventId(event.getId()).stream()
                    .map(etc -> ticketCategoryRepository.getById(etc.getTicketCategoryId()))
                    .flatMap(tc -> {
                        final BigDecimal price = regularPrice.subtract(regularPrice.multiply(tc.getDiscount()).divide(HUNDRED, 2, HALF_UP));
                        return Stream.generate(MapSqlParameterSource::new)
                                .limit(tc.getMaxTickets())
                                .map(ps -> buildParams(eventId, creation, tc, price, ps));
                    }).toArray(MapSqlParameterSource[]::new);
    }

    private MapSqlParameterSource buildParams(int eventId, Date creation, TicketCategory tc, BigDecimal price, MapSqlParameterSource ps) {
        return ps.addValue("uuid", UUID.randomUUID().toString())
                .addValue("creation", creation)
                .addValue("categoryId", tc.getId())
                .addValue("eventId", eventId)
                .addValue("status", Ticket.TicketStatus.FREE.name())
                .addValue("originalPrice", price)
                .addValue("paidPrice", BigDecimal.ZERO);
    }

    private void distributeSeats(EventModification em, int eventId) {
        boolean freeOfCharge = em.isFreeOfCharge();
        em.getTicketCategories().stream().forEach(tc -> {
            final Pair<Integer, Integer> category = ticketCategoryRepository.insert(tc.getInception().toDate(),
                    tc.getExpiration().toDate(), tc.getName(), tc.getDescription(), tc.getSeats(), freeOfCharge ? BigDecimal.ZERO : tc.getDiscount());
            eventTicketCategoryRepository.insert(eventId, category.getValue());
        });
        final List<TicketCategory> ticketCategories = eventTicketCategoryRepository.findByEventId(eventId).stream()
                .map(etc -> ticketCategoryRepository.getById(etc.getTicketCategoryId()))
                .collect(Collectors.toList());
        int notAssignedTickets = em.getSeats() - ticketCategories.stream().mapToInt(TicketCategory::getMaxTickets).sum();

        if(notAssignedTickets != 0) {
            TicketCategory last = ticketCategories.stream()
                                  .sorted(Comparator.comparing(TicketCategory::getExpiration).reversed())
                                  .findFirst().get();
            ticketCategoryRepository.updateSeatsAvailability(last.getId(), last.getMaxTickets() + notAssignedTickets);
        }
    }

    static BigDecimal evaluatePrice(BigDecimal price, BigDecimal vat, boolean vatIncluded, boolean freeOfCharge) {
        if(freeOfCharge) {
            return BigDecimal.ZERO;
        }
        if(!vatIncluded) {
            return price;
        }
        return price.divide(BigDecimal.ONE.add(vat.divide(HUNDRED)), 2, HALF_UP);
    }

    private int insertEvent(EventModification em) {
        String paymentProxies = em.getPaymentProxies()
                .stream()
                .map(PaymentProxy::name)
                .collect(joining(","));
        BigDecimal actualPrice = evaluatePrice(em.getPrice(), em.getVat(), em.isVatIncluded(), em.isFreeOfCharge());
        BigDecimal vat = em.isFreeOfCharge() ? BigDecimal.ZERO : em.getVat();
        return eventRepository.insert(em.getDescription(), em.getShortName(), em.getOrganizationId(), em.getLocation(),
                "", "", em.getStart().toDate(), em.getEnd().toDate(), actualPrice,
                em.getCurrency(), em.getSeats(), em.isVatIncluded(), vat, paymentProxies).getValue();
    }

    @Transactional
    public String createTicketReservation(int eventId, List<TicketReservation> ticketReservations) {
    	
        String transactionId = UUID.randomUUID().toString();
        ticketRepository.createNewTransaction(transactionId, DateUtils.addMinutes(new Date(), 25));//TODO: 25 minutes should be configured
        
        for(TicketReservation ticketReservation : ticketReservations) {
            List<Integer> reservedForUpdate = ticketRepository.selectTicketInCategoryForUpdate(eventId, ticketReservation.getTicketCategoryId(), ticketReservation.getAmount());
            Validate.isTrue(reservedForUpdate.size() == ticketReservation.getAmount().intValue(), "not enough tickets to reserve");
            ticketRepository.reserveTickets(transactionId, reservedForUpdate);
        }

    	return transactionId;
    }
}
