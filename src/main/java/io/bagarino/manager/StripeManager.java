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


import com.stripe.exception.*;
import com.stripe.model.Charge;
import io.bagarino.manager.system.ConfigurationManager;
import io.bagarino.model.Event;
import io.bagarino.repository.TicketRepository;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.bagarino.model.system.ConfigurationKeys.STRIPE_PUBLIC_KEY;
import static io.bagarino.model.system.ConfigurationKeys.STRIPE_SECRET_KEY;

@Component
@Log4j2
public class StripeManager {

    private final Map<Class<? extends StripeException>, StripeExceptionHandler> handlers;
    private final ConfigurationManager configurationManager;
    private final TicketRepository ticketRepository;

    @Autowired
    public StripeManager(ConfigurationManager configurationManager,
                         TicketRepository ticketRepository) {
        this.configurationManager = configurationManager;
        this.ticketRepository = ticketRepository;

        handlers = new HashMap<>();
        handlers.put(CardException.class, this::handleCardException);
        handlers.put(InvalidRequestException.class, this::handleInvalidRequestException);
        handlers.put(AuthenticationException.class, this::handleAuthenticationException);
        handlers.put(APIConnectionException.class, this::handleApiConnectionException);
        handlers.put(StripeException.class, this::handleGenericException);
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
    public Optional<Charge> chargeCreditCard(String stripeToken, long amountInCent, Event event,
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
        Charge result = Charge.create(chargeParams, getSecretKey());
        if(result.getPaid()) {
            return Optional.of(result);
        }
        return Optional.empty();
    }

    public String handleException(StripeException exc) {
        return findExceptionHandler(exc).handle(exc);
    }

    private StripeExceptionHandler findExceptionHandler(StripeException exc) {
        final Optional<StripeExceptionHandler> eh = Optional.ofNullable(handlers.get(exc.getClass()));
        if(!eh.isPresent()) {
            log.warn("cannot find an ExceptionHandler for {}. Falling back to the default one.", exc.getClass());
        }
        return eh.orElseGet(() -> handlers.get(StripeException.class));
    }

    /* exception handlers... */

    /**
     * This handler simply returns the message code from stripe.
     * There is no need in writing something in the log.
     * @param e the exception
     * @return the code
     */
    private String handleCardException(StripeException e) {
        CardException ce = (CardException)e;
        return "error.STEP2_STRIPE_" + ce.getCode();
    }

    /**
     * handles invalid request exception using the error.STEP2_STRIPE_invalid_ prefix for the message.
     * @param e the exception
     * @return message code
     */
    private String handleInvalidRequestException(StripeException e) {
        InvalidRequestException ire = (InvalidRequestException)e;
        return "error.STEP2_STRIPE_invalid_" + ire.getParam();
    }

    /**
     * Logs the failure and report the failure to the admin (to be done)
     * @param e the exception
     * @return error.STEP2_STRIPE_abort
     */
    private String handleAuthenticationException(StripeException e) {
        log.error("an AuthenticationException has occurred. Please fix configuration!!", e);
        return "error.STEP2_STRIPE_abort";
    }

    /**
     * Logs the failure and report the failure to the admin (to be done)
     * @param e
     * @return
     */
    private String handleApiConnectionException(StripeException e) {
        log.error("unable to connect to the Stripe API", e);
        return "error.STEP2_STRIPE_abort";
    }

    /**
     * Logs the failure and report the failure to the admin (to be done)
     * @param e
     * @return
     */
    private String handleGenericException(StripeException e) {
        log.error("unexpected error during transaction", e);
        return "error.STEP2_STRIPE_unexpected";
    }


    @FunctionalInterface
    private static interface StripeExceptionHandler {
        String handle(StripeException exc);
    }

}
