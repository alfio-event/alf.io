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


import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import io.bagarino.manager.system.ConfigurationManager;
import io.bagarino.model.Event;
import io.bagarino.repository.TicketRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static io.bagarino.model.system.ConfigurationKeys.STRIPE_PUBLIC_KEY;
import static io.bagarino.model.system.ConfigurationKeys.STRIPE_SECRET_KEY;

@Component
public class StripeManager {

    private final ConfigurationManager configurationManager;
    private final TicketRepository ticketRepository;

    @Autowired
    public StripeManager(ConfigurationManager configurationManager,
                         TicketRepository ticketRepository) {
        this.configurationManager = configurationManager;
        this.ticketRepository = ticketRepository;
    }

    public String getSecretKey() {
        return configurationManager.getRequiredValue(STRIPE_SECRET_KEY);
    }

    public String getPublicKey() {
        return configurationManager.getRequiredValue(STRIPE_PUBLIC_KEY);
    }

    /**
     * After client side integration with stripe widget, our server receives the stripeToken
     * StripeToken is a single-use token that allows our server to effectively charge the credit card and
     * get money on our account.
     * <p>
     * as documented in https://stripe.com/docs/tutorials/charges
     *
     * @return
     * @throws StripeException
     */
    public Charge chargeCreditCard(String stripeToken, long amountInCent, Event event,
                                   String reservationId, String email, String fullName, String billingAddress) throws StripeException {

        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", amountInCent);
        chargeParams.put("currency", event.getCurrency());
        chargeParams.put("card", stripeToken);

        int tickets = ticketRepository.countTicketsInReservation(reservationId);
        chargeParams.put("description", String.format("%d ticket(s) for event %s", tickets, event.getShortName()));

        Map<String, String> initialMetadata = new HashMap<>();
        initialMetadata.put("reservationId", reservationId);
        initialMetadata.put("email", email);
        initialMetadata.put("fullName", fullName);
        if (StringUtils.isNotBlank(billingAddress)) {
            initialMetadata.put("billingAddress", billingAddress);
        }
        chargeParams.put("metadata", initialMetadata);
        return Charge.create(chargeParams, getSecretKey());
    }

}
