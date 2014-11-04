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

import io.bagarino.manager.support.OrderSummary;
import io.bagarino.manager.support.PaymentResult;
import io.bagarino.manager.support.SummaryRow;
import io.bagarino.manager.system.ConfigurationManager;
import io.bagarino.manager.system.Mailer;
import io.bagarino.model.Event;
import io.bagarino.model.SpecialPrice;
import io.bagarino.model.SpecialPrice.Status;
import io.bagarino.model.Ticket;
import io.bagarino.model.Ticket.TicketStatus;
import io.bagarino.model.TicketReservation;
import io.bagarino.model.TicketReservation.TicketReservationStatus;
import io.bagarino.model.modification.TicketReservationWithOptionalCodeModification;
import io.bagarino.model.modification.TicketWithStatistic;
import io.bagarino.model.system.ConfigurationKeys;
import io.bagarino.repository.*;
import io.bagarino.repository.user.OrganizationRepository;
import io.bagarino.util.MonetaryUtil;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static io.bagarino.util.MonetaryUtil.formatCents;
import static io.bagarino.util.OptionalWrapper.optionally;

@Component
@Transactional
@Log4j2
public class TicketReservationManager {
	
	public static final int RESERVATION_MINUTE = 25;
    public static final String STUCK_TICKETS_MSG = "there are stuck tickets for the event %s. Please check admin area.";
    public static final String STUCK_TICKETS_SUBJECT = "warning: stuck tickets found";

    private final EventRepository eventRepository;
	private final OrganizationRepository organizationRepository;
    private final TicketRepository ticketRepository;
	private final TicketReservationRepository ticketReservationRepository;
	private final TicketCategoryRepository ticketCategoryRepository;
	private final ConfigurationManager configurationManager;
    private final PaymentManager paymentManager;
    private final SpecialPriceRepository specialPriceRepository;
    private final Mailer mailer;
    private final TransactionRepository transactionRepository;

	public static class NotEnoughTicketsException extends Exception {
		
	}
	
	@Data
	public static class TotalPrice {
		private final int priceWithVAT;
		private final int VAT;
	}
	
	@Autowired
	public TicketReservationManager(EventRepository eventRepository,
                                    OrganizationRepository organizationRepository,
                                    TicketRepository ticketRepository,
                                    TicketReservationRepository ticketReservationRepository,
                                    TicketCategoryRepository ticketCategoryRepository,
                                    ConfigurationManager configurationManager,
                                    PaymentManager paymentManager,
                                    SpecialPriceRepository specialPriceRepository,
                                    TransactionRepository transactionRepository,
                                    Mailer mailer) {
		this.eventRepository = eventRepository;
        this.organizationRepository = organizationRepository;
        this.ticketRepository = ticketRepository;
		this.ticketReservationRepository = ticketReservationRepository;
		this.ticketCategoryRepository = ticketCategoryRepository;
		this.configurationManager = configurationManager;
        this.paymentManager = paymentManager;
        this.specialPriceRepository = specialPriceRepository;
        this.mailer = mailer;
        this.transactionRepository = transactionRepository;
    }
	
    /**
     * Create a ticket reservation. It will create a reservation _only_ if it can find enough tickets. Note that it will not do date/validity validation. This must be ensured by the
     * caller.
     * 
     * @param eventId
     * @param list
     * @param reservationExpiration
     * @return
     */
    public String createTicketReservation(int eventId, List<TicketReservationWithOptionalCodeModification> list, Date reservationExpiration) throws NotEnoughTicketsException {
    	
        String transactionId = UUID.randomUUID().toString();
        ticketReservationRepository.createNewReservation(transactionId, reservationExpiration);
        
        for(TicketReservationWithOptionalCodeModification ticketReservation : list) {
            List<Integer> reservedForUpdate = ticketRepository.selectTicketInCategoryForUpdate(eventId, ticketReservation.getTicketCategoryId(), ticketReservation.getAmount());
            int requested = ticketReservation.getAmount();
			if (reservedForUpdate.size() != requested) {
				throw new NotEnoughTicketsException();
			}
            
            
			if (ticketReservation.getSpecialPrice().isPresent()) {
				Validate.isTrue(reservedForUpdate.size() == 1, "can assign exactly one special code");
				SpecialPrice specialPrice = ticketReservation.getSpecialPrice().get();
				ticketRepository.reserveTicket(transactionId, reservedForUpdate.stream().findFirst().orElseThrow(IllegalStateException::new),specialPrice.getId());
				specialPriceRepository.updateStatus(specialPrice.getId(), Status.PENDING.toString());
			} else {
				ticketRepository.reserveTickets(transactionId, reservedForUpdate);
			}
        }

    	return transactionId;
    }

    public PaymentResult confirm(String gatewayToken, Event event, String reservationId,
                        String email, String fullName, String billingAddress,
                        TotalPrice reservationCost) {
        try {
            PaymentResult paymentResult;
            if(reservationCost.getPriceWithVAT() > 0) {
                transitionToInPayment(reservationId, email, fullName, billingAddress);
                paymentResult = paymentManager.processPayment(reservationId, gatewayToken, reservationCost.getPriceWithVAT(), event, email, fullName, billingAddress);
                if(!paymentResult.isSuccessful()) {
                    reTransitionToPending(reservationId);
                    return paymentResult;
                }
            } else {
                paymentResult = PaymentResult.successful("not-paid");
            }
            completeReservation(reservationId, email, fullName, billingAddress);
            return paymentResult;
        } catch(Exception ex) {
            //it is guaranteed that in this case we're dealing with "local" error (e.g. database failure),
            //thus it is safer to not rollback the reservation status
            log.error("unexpected error during payment confirmation", ex);
            return PaymentResult.unsuccessful("error.STEP2_STRIPE_unexpected");
        }

    }

    private void transitionToInPayment(String reservationId, String email, String fullName, String billingAddress) {
    	int updatedReservation = ticketReservationRepository.updateTicketReservation(reservationId, TicketReservationStatus.IN_PAYMENT.toString(), email, fullName, billingAddress);
		Validate.isTrue(updatedReservation == 1);
    }
    
	private void reTransitionToPending(String reservationId) {
		int updatedReservation = ticketReservationRepository.updateTicketStatus(reservationId, TicketReservationStatus.PENDING.toString());
		Validate.isTrue(updatedReservation == 1);
		
	}
    
	//check internal consistency between the 3 values
    public Optional<Triple<Event, TicketReservation, Ticket>> from(String eventName, String reservationId, String ticketIdentifier) {
    	return optionally(() -> Triple.of(eventRepository.findByShortName(eventName), 
				ticketReservationRepository.findReservationById(reservationId), 
				ticketRepository.findByUUID(ticketIdentifier))).flatMap((x) -> {
					
					Ticket t = x.getRight();
					Event e = x.getLeft();
					TicketReservation tr = x.getMiddle();
					
					if(tr.getId().equals(t.getTicketsReservationId()) && e.getId() == t.getEventId()) {
						return Optional.of(x);
					} else {
						return Optional.empty();
					}
					
				});
    }

    /**
     * Set the tickets attached to the reservation to the ACQUIRED state and the ticket reservation to the COMPLETE state. Additionally it will save email/fullName/billingaddress.
     *
     * @param reservationId
     * @param email
     * @param fullName
     * @param billingAddress
     */
	private void completeReservation(String reservationId, String email, String fullName, String billingAddress) {
		int updatedTickets = ticketRepository.updateTicketStatus(reservationId, TicketStatus.ACQUIRED.toString());
		Validate.isTrue(updatedTickets > 0);
		
		
		specialPriceRepository.updateStatusForReservation(Collections.singletonList(reservationId), Status.TAKEN.toString());
		
		int updatedReservation = ticketReservationRepository.updateTicketReservation(reservationId, TicketReservationStatus.COMPLETE.toString(), email, fullName, billingAddress);
		Validate.isTrue(updatedReservation == 1);
	}


	public void cleanupExpiredPendingReservation(Date expirationDate) {
		List<String> expiredReservationIds = ticketReservationRepository.findExpiredReservation(expirationDate);
		if(expiredReservationIds.isEmpty()) {
			return;
		}
		
		specialPriceRepository.updateStatusForReservation(expiredReservationIds, Status.FREE.toString());
		ticketRepository.freeFromReservation(expiredReservationIds);
		ticketReservationRepository.remove(expiredReservationIds);
	}

    /**
     * Finds all the reservations that are "stuck" in payment status.
     * This could happen when there is an internal error after a successful credit card charge.
     *
     * @param expirationDate expiration date
     */
    public void markExpiredInPaymentReservationAsStuck(Date expirationDate) {
        final List<String> stuckReservations = ticketReservationRepository.findStuckReservations(expirationDate);
        stuckReservations.forEach(reservationId -> ticketReservationRepository.updateTicketStatus(reservationId, TicketReservationStatus.STUCK.name()));
        stuckReservations.stream()
                .map(id -> ticketRepository.findTicketsInReservation(id).stream().findFirst())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .mapToInt(Ticket::getEventId)
                .distinct()
                .mapToObj(eventRepository::findById)
                .map(e -> Pair.of(e, organizationRepository.getById(e.getOrganizationId())))
                .forEach(pair -> mailer.send(pair.getRight().getEmail(),
                                    STUCK_TICKETS_SUBJECT,
                                    String.format(STUCK_TICKETS_MSG, pair.getLeft().getShortName()),
                                    Optional.empty())
                );
    }

    public List<TicketWithStatistic> loadModifiedTickets(int eventId, int categoryId) {
        Event event = eventRepository.findById(eventId);
        return ticketRepository.findAllModifiedTickets(eventId, categoryId).stream()
                .map(t -> new TicketWithStatistic(t, ticketReservationRepository.findReservationById(t.getTicketsReservationId()),
                        event.getZoneId(), optionally(() -> transactionRepository.loadByReservationId(t.getTicketsReservationId()))))
                .collect(Collectors.toList());
    }
	
	private int totalFrom(List<Ticket> tickets) {
    	return tickets.stream().mapToInt(Ticket::getPaidPriceInCents).sum();
    }

	/**
	 * Get the total cost with VAT if it's not included in the ticket price.
	 * 
	 * @param reservationId
	 * @return
	 */
    public TotalPrice totalReservationCostWithVAT(String reservationId) {
    	Event event = eventRepository.findByReservationId(reservationId);
    	int total = totalFrom(ticketRepository.findTicketsInReservation(reservationId));
    	if(event.isVatIncluded()) {
            final int vat = MonetaryUtil.calcVat(total, event.getVat());
            return new TotalPrice(total + vat, vat);
    	} else {
    		int priceWithVAT = MonetaryUtil.addVAT(total, event.getVat());
    		return new TotalPrice(priceWithVAT, priceWithVAT - total);
    	}
    }
    
    public OrderSummary orderSummaryForReservationId(String reservationId, Event event) {
    	TotalPrice reservationCost = totalReservationCostWithVAT(reservationId);
    	List<SummaryRow> summary = extractSummary(reservationId, event);
    	return new OrderSummary(reservationCost, summary, reservationCost.getPriceWithVAT() == 0, formatCents(reservationCost.getPriceWithVAT()), formatCents(reservationCost.getVAT()));
    }
    
    private List<SummaryRow> extractSummary(String reservationId, Event event) {
    	List<SummaryRow> summary = new ArrayList<>();
    	List<Ticket> tickets = ticketRepository.findTicketsInReservation(reservationId);
    	tickets.stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).forEach((categoryId, ticketsByCategory) -> {
            int paidPriceInCents = ticketsByCategory.get(0).getPaidPriceInCents();
            if(event.isVatIncluded()) {
                paidPriceInCents = MonetaryUtil.addVAT(paidPriceInCents, event.getVat());
            }
    		String categoryName = ticketCategoryRepository.getById(categoryId, event.getId()).getName();
            final int subTotal = paidPriceInCents * ticketsByCategory.size();
            summary.add(new SummaryRow(categoryName, formatCents(paidPriceInCents), ticketsByCategory.size(), formatCents(subTotal), subTotal));
    	});
    	return summary;
    } 
    
    public String reservationUrl(String reservationId) {
    	Event event = eventRepository.findByReservationId(reservationId);
		return StringUtils.removeEnd(configurationManager.getRequiredValue(ConfigurationKeys.BASE_URL), "/")
				+ "/event/" + event.getShortName() + "/reservation/" + reservationId;
    }


	public int maxAmountOfTickets() {
        return configurationManager.getIntConfigValue(ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, 5);
	}
	
	public Optional<TicketReservation> findById(String reservationId) {
		return optionally(() -> ticketReservationRepository.findReservationById(reservationId));
	}


	public void cancelPendingReservation(String reservationId) {
		
		Validate.isTrue(ticketReservationRepository.findReservationById(reservationId).getStatus() == TicketReservationStatus.PENDING);
		
		List<String> reservationIdsToRemove = Collections.singletonList(reservationId);
		specialPriceRepository.updateStatusForReservation(reservationIdsToRemove, Status.FREE.toString());
		int updatedTickets = ticketRepository.freeFromReservation(reservationIdsToRemove);
		Validate.isTrue(updatedTickets > 0);
		int removedReservation = ticketReservationRepository.remove(reservationIdsToRemove);
		Validate.isTrue(removedReservation == 1);
	}
}
