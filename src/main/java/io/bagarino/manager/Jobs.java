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
package io.bagarino.manager;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class Jobs {

	private final TicketReservationManager ticketReservationManager;
    private final SpecialPriceTokenGenerator specialPriceTokenGenerator;

	@Autowired
	public Jobs(TicketReservationManager ticketReservationManager,
                SpecialPriceTokenGenerator specialPriceTokenGenerator) {
		this.ticketReservationManager = ticketReservationManager;
        this.specialPriceTokenGenerator = specialPriceTokenGenerator;
    }

	@Scheduled(initialDelay = 1000 * 60, fixedDelay = 1000 * 30)
	public void cleanupExpiredPendingReservation() {
		//cleanup reservation that have a expiration older than "now minus 10 minutes": this give some additional slack.
        final Date expirationDate = DateUtils.addMinutes(new Date(), -10);
        ticketReservationManager.cleanupExpiredPendingReservation(expirationDate);
        ticketReservationManager.markExpiredInPaymentReservationAsStuck(expirationDate);
	}

    @Scheduled(fixedDelay = 1000 * 30)
    public void generateSpecialPriceCodes() {
        specialPriceTokenGenerator.generatePendingCodes();
    }
}
