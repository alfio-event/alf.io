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
package alfio.controller.api.admin;

import alfio.manager.AdminReservationManager;
import alfio.manager.EventManager;
import alfio.manager.TicketReservationManager;
import alfio.model.*;
import alfio.model.modification.AdminReservationModification;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.repository.EventRepository;
import alfio.util.MonetaryUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequestMapping("/admin/api/reservation")
@RestController
public class AdminReservationApiController {
    private final AdminReservationManager adminReservationManager;
    private final EventManager eventManager;
    private final EventRepository eventRepository;
    private final TicketReservationManager ticketReservationManager;

    @Autowired
    public AdminReservationApiController(AdminReservationManager adminReservationManager,
                                         EventManager eventManager,
                                         EventRepository eventRepository,
                                         TicketReservationManager ticketReservationManager) {
        this.adminReservationManager = adminReservationManager;
        this.eventManager = eventManager;
        this.eventRepository = eventRepository;
        this.ticketReservationManager = ticketReservationManager;
    }

    @RequestMapping(value = "/event/{eventName}/new", method = RequestMethod.POST)
    public Result<String> createNew(@PathVariable("eventName") String eventName, @RequestBody AdminReservationModification reservation, Principal principal) {
        return adminReservationManager.createReservation(reservation, eventName, principal.getName()).map(r -> r.getLeft().getId());
    }

    @RequestMapping(value = "/event/{eventName}/reservations/list", method = RequestMethod.GET)
    public List<TicketReservation> findAll(@PathVariable("eventName") String eventName, Principal principal) {
        Event event = eventRepository.findByShortName(eventName);
        eventManager.checkOwnership(event, principal.getName(), event.getOrganizationId());
        return ticketReservationManager.findAllReservationsInEvent(event.getId());
    }

    @RequestMapping(value = "/event/{eventName}/{reservationId}/confirm", method = RequestMethod.PUT)
    public Result<TicketReservationDescriptor> confirmReservation(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        return adminReservationManager.confirmReservation(eventName, reservationId, principal.getName())
            .map(triple -> toReservationDescriptor(reservationId, triple));
    }

    @RequestMapping(value = "/event/{eventName}/{reservationId}", method = RequestMethod.POST)
    public Result<Boolean> updateReservation(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId,
                                             @RequestBody AdminReservationModification arm, Principal principal) {
        return adminReservationManager.updateReservation(eventName, reservationId, arm, principal.getName());
    }

    @RequestMapping(value = "/event/{eventName}/{reservationId}/notify", method = RequestMethod.PUT)
    public Result<Boolean> notifyReservation(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId,
                                             @RequestBody AdminReservationModification arm, Principal principal) {
        return adminReservationManager.notify(eventName, reservationId, arm, principal.getName());
    }

    @RequestMapping(value = "/event/{eventName}/{reservationId}", method = RequestMethod.GET)
    public Result<TicketReservationDescriptor> loadReservation(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        return adminReservationManager.loadReservation(eventName, reservationId, principal.getName())
            .map(triple -> toReservationDescriptor(reservationId, triple));
    }

    @RequestMapping(value = "/event/{eventName}/{reservationId}/ticket/{ticketId}", method = RequestMethod.GET)
    public Result<Ticket> loadTicket(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, @PathVariable("ticketId") int ticketId, Principal principal) {
        return adminReservationManager.loadReservation(eventName, reservationId, principal.getName()).flatMap(triple -> {
            //not optimal
            return triple.getMiddle().stream().filter(t -> t.getId() == ticketId).findFirst().map(Result::success).orElse(Result.error(ErrorCode.custom("not_found", "not found")));
        });
    }


    @RequestMapping(value = "/event/{eventName}/{reservationId}/remove-tickets", method = RequestMethod.POST)
    public Result<Boolean> removeTickets(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId,
                                             @RequestBody RemoveTicketsModification toRemove, Principal principal) {

        List<Integer> toRefund = toRemove.getRefundTo().entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        adminReservationManager.removeTickets(eventName, reservationId, toRemove.getTicketIds(), toRefund, principal.getName());
        return Result.success(true);
    }

    @RequestMapping(value = "/event/{eventName}/{reservationId}/payment-info", method = RequestMethod.GET)
    public Result<TransactionAndPaymentInfo> getPaymentInfo(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        return adminReservationManager.getPaymentInfo(eventName, reservationId, principal.getName());
    }

    @RequestMapping(value = "/event/{eventName}/{reservationId}/cancel", method = RequestMethod.POST)
    public Result<Boolean> removeReservation(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, @RequestParam("refund") boolean refund,
                                             Principal principal) {
        adminReservationManager.removeReservation(eventName, reservationId, refund, principal.getName());
        return Result.success(true);
    }

    @RequestMapping(value = "/event/{eventName}/{reservationId}/refund", method = RequestMethod.POST)
    public Result<Boolean> refund(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, @RequestBody RefundAmount amount, Principal principal) {
        return adminReservationManager.refund(eventName, reservationId, new BigDecimal(amount.amount), principal.getName());
    }

    private TicketReservationDescriptor toReservationDescriptor(String reservationId, Triple<TicketReservation, List<Ticket>, Event> triple) {
        List<SerializablePair<TicketCategory, List<Ticket>>> tickets = triple.getMiddle().stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).entrySet().stream()
            .map(entry -> SerializablePair.of(eventManager.getTicketCategoryById(entry.getKey(), triple.getRight().getId()), entry.getValue()))
            .collect(Collectors.toList());
        TicketReservation reservation = triple.getLeft();
        return new TicketReservationDescriptor(reservation, ticketReservationManager.orderSummaryForReservationId(reservationId, triple.getRight(), Locale.forLanguageTag(reservation.getUserLanguage())), tickets);
    }

    @RequiredArgsConstructor
    @Getter
    public static class TicketReservationDescriptor {
        private final TicketReservation reservation;
        private final OrderSummary orderSummary;
        private final List<SerializablePair<TicketCategory, List<Ticket>>> ticketsByCategory;
    }

    @RequiredArgsConstructor
    @Getter
    public static class RemoveTicketsModification {
        private final List<Integer> ticketIds;
        private Map<Integer, Boolean> refundTo;
    }

    @RequiredArgsConstructor
    @Getter
    public static class RefundAmount {
        private final String amount;
    }
}
