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
import io.bagarino.model.Ticket.TicketStatus;
import io.bagarino.model.TicketReservation.TicketReservationStatus;
import io.bagarino.model.modification.TicketReservationModification;
import io.bagarino.repository.TicketRepository;
import io.bagarino.repository.TicketReservationRepository;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
@Transactional
public class TicketReservationManager {
	
	public static final int RESERVATION_MINUTE = 25;
	
	private final TicketRepository ticketRepository;
	private final TicketReservationRepository ticketReservationRepository;
	private final ConfigurationManager configurationManager;
	
	@Autowired
	public TicketReservationManager(TicketRepository ticketRepository, TicketReservationRepository ticketReservationRepository, ConfigurationManager configurationManager) {
		this.ticketRepository = ticketRepository;
		this.ticketReservationRepository = ticketReservationRepository;
		this.configurationManager = configurationManager;
	}
	
    
    public String createTicketReservation(int eventId, List<TicketReservationModification> ticketReservations, Date reservationExpiration) {
    	
        String transactionId = UUID.randomUUID().toString();
        ticketReservationRepository.createNewReservation(transactionId, reservationExpiration);
        
        for(TicketReservationModification ticketReservation : ticketReservations) {
            List<Integer> reservedForUpdate = ticketRepository.selectTicketInCategoryForUpdate(eventId, ticketReservation.getTicketCategoryId(), ticketReservation.getAmount());
            Validate.isTrue(reservedForUpdate.size() == ticketReservation.getAmount().intValue(), "not enough tickets to reserve");
            ticketRepository.reserveTickets(transactionId, reservedForUpdate);
        }

    	return transactionId;
    }

	public void completeReservation(int eventId, String reservationId, String email, String fullName, String billingAddress) {
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


	public int maxAmountOfTickets() {
        return configurationManager.getIntConfigValue("MAX_AMOUNT_OF_TICKETS_BY_RESERVATION", 5);
	}
}
