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

import io.bagarino.manager.system.ConfigurationManager;
import io.bagarino.model.Event;
import io.bagarino.model.Ticket;
import io.bagarino.model.Ticket.TicketStatus;
import io.bagarino.model.TicketReservation;
import io.bagarino.model.TicketReservation.TicketReservationStatus;
import io.bagarino.model.modification.TicketReservationModification;
import io.bagarino.model.system.ConfigurationKeys;
import io.bagarino.repository.EventRepository;
import io.bagarino.repository.TicketCategoryRepository;
import io.bagarino.repository.TicketRepository;
import io.bagarino.repository.TicketReservationRepository;
import io.bagarino.util.MonetaryUtil;
import lombok.Data;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
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
public class TicketReservationManager {
	
	public static final int RESERVATION_MINUTE = 25;

	private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
	private final TicketReservationRepository ticketReservationRepository;
	private final TicketCategoryRepository ticketCategoryRepository;
	private final ConfigurationManager configurationManager;
	
	public static class NotEnoughTicketsException extends Exception {
		
	}
	
	@Data
	public static class TotalPrice {
		private final int priceWithVAT;
		private final int VAT;
	}
	
	@Autowired
	public TicketReservationManager(EventRepository eventRepository, 
			TicketRepository ticketRepository, TicketReservationRepository ticketReservationRepository, 
			TicketCategoryRepository ticketCategoryRepository, 
			ConfigurationManager configurationManager) {
		this.eventRepository = eventRepository;
		this.ticketRepository = ticketRepository;
		this.ticketReservationRepository = ticketReservationRepository;
		this.ticketCategoryRepository = ticketCategoryRepository;
		this.configurationManager = configurationManager;
	}
	
    /**
     * Create a ticket reservation. It will create a reservation _only_ if it can find enough tickets. Note that it will not do date/validity validation. This must be ensured by the
     * caller.
     * 
     * @param eventId
     * @param ticketReservations
     * @param reservationExpiration
     * @return
     */
    public String createTicketReservation(int eventId, List<TicketReservationModification> ticketReservations, Date reservationExpiration) throws NotEnoughTicketsException {
    	
        String transactionId = UUID.randomUUID().toString();
        ticketReservationRepository.createNewReservation(transactionId, reservationExpiration);
        
        for(TicketReservationModification ticketReservation : ticketReservations) {
            List<Integer> reservedForUpdate = ticketRepository.selectTicketInCategoryForUpdate(eventId, ticketReservation.getTicketCategoryId(), ticketReservation.getAmount());
            
			if (reservedForUpdate.size() != ticketReservation.getAmount().intValue()) {
				throw new NotEnoughTicketsException();
			}
            
            ticketRepository.reserveTickets(transactionId, reservedForUpdate);
        }

    	return transactionId;
    }
    
    public void transitionToInPayment(String reservationId, String email, String fullName, String billingAddress) {
    	int updatedReservation = ticketReservationRepository.updateTicketReservation(reservationId, TicketReservationStatus.IN_PAYMENT.toString(), email, fullName, billingAddress);
		Validate.isTrue(updatedReservation == 1);
    }
    
    public Optional<Triple<Event, TicketReservation, Ticket>> from(String eventName, String reservationId, String ticketIdentifier) {
    	return optionally(() -> Triple.of(eventRepository.findByShortName(eventName), 
				ticketReservationRepository.findReservationById(reservationId), 
				ticketRepository.findByUUID(ticketIdentifier)));
    }

    /**
     * Set the tickets attached to the reservation to the ACQUIRED state and the ticket reservation to the COMPLETE state. Additionally it will save email/fullName/billingaddress.
     * 
     * TODO: should save the transaction id from stripe&co too!
     * 
     * @param reservationId
     * @param email
     * @param fullName
     * @param billingAddress
     */
	public void completeReservation(String reservationId, String email, String fullName, String billingAddress) {
		int updatedTickets = ticketRepository.updateTicketStatus(reservationId, TicketStatus.ACQUIRED.toString());
		Validate.isTrue(updatedTickets > 0);
		int updatedReservation = ticketReservationRepository.updateTicketReservation(reservationId, TicketReservationStatus.COMPLETE.toString(), email, fullName, billingAddress);
		Validate.isTrue(updatedReservation == 1);
	}


	public void cleanupExpiredPendingReservation(Date expirationDate) {
		List<String> expired = ticketReservationRepository.findExpiredReservation(expirationDate);
		if(expired.isEmpty()) {
			return;
		}
		
		ticketRepository.freeFromReservation(expired);
		ticketReservationRepository.remove(expired);
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
    		String categoryName = ticketCategoryRepository.getById(categoryId).getName();
            final int subTotal = paidPriceInCents * ticketsByCategory.size();
            summary.add(new SummaryRow(categoryName, formatCents(paidPriceInCents), ticketsByCategory.size(), formatCents(subTotal), subTotal));
    	});
    	return summary;
    } 
    
    
    @Data
    public static class OrderSummary {
    	private final TotalPrice originalTotalPrice;
    	private final List<SummaryRow> summary; 
    	private final boolean free;
    	private final String totalPrice;
    	private final String totalVAT;
    	
    	/* lol jmustache */
    	public boolean getFree() {
    		return free;
    	}
    }
    
    @Data
    public static class SummaryRow {
    	private final String name;
    	private final String price;
    	private final int amount;
    	private final String subTotal;
        private final int originalSubTotal;
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
		
		List<String> toRemove = Collections.singletonList(reservationId);
		int updatedTickets = ticketRepository.freeFromReservation(toRemove);
		Validate.isTrue(updatedTickets > 0);
		int removedReservation = ticketReservationRepository.remove(toRemove);
		Validate.isTrue(removedReservation == 1);
	}
}
