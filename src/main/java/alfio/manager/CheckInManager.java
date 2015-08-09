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

import alfio.model.Ticket.TicketStatus;
import alfio.repository.TicketRepository;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class CheckInManager {
    
    private final TicketRepository ticketRepository;
    
    @Autowired
    public CheckInManager(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    
    public void checkIn(String uuid) {
        Validate.isTrue(ticketRepository.findByUUID(uuid).getStatus() == TicketStatus.ACQUIRED);
        
        ticketRepository.updateTicketStatusWithUUID(uuid, TicketStatus.CHECKED_IN.toString());
    }
    
    public void acquire(String uuid) {
        Validate.isTrue(ticketRepository.findByUUID(uuid).getStatus() == TicketStatus.TO_BE_PAID);
        ticketRepository.updateTicketStatusWithUUID(uuid, TicketStatus.ACQUIRED.toString());
    }
}
