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

import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.system.Configuration;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProvider;
import alfio.repository.TicketReservationRepository;
import alfio.util.WorkingDaysAdjusters;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

import static alfio.manager.TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID;
import static alfio.model.system.ConfigurationKeys.*;

@Component
@Log4j2
@AllArgsConstructor
public class BankTransferManager implements PaymentProvider {

    private final ConfigurationManager configurationManager;
    private final TicketReservationRepository ticketReservationRepository;

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext paymentContext) {
        return paymentMethod == PaymentMethod.BANK_TRANSFER &&
            configurationManager.getBooleanConfigValue( paymentContext.narrow(BANK_TRANSFER_ENABLED), false )
            && (paymentContext.getEvent() == null || getOfflinePaymentWaitingPeriod(paymentContext, configurationManager).orElse(0) > 0);
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        transitionToOfflinePayment(spec);
        return PaymentResult.successful(NOT_YET_PAID_TRANSACTION_ID);
    }

    @Override
    public Map<String, ?> getModelOptions(PaymentContext context) {
        OptionalInt delay = getOfflinePaymentWaitingPeriod(context, configurationManager);
        Event event = context.getEvent();
        if(!delay.isPresent()) {
            log.error("Already started event {} has been found with OFFLINE payment enabled" , event.getDisplayName());
        }
        Map<String, Object> model = new HashMap<>();
        model.put("delayForOfflinePayment", Math.min(1, delay.orElse( 0 )));
        boolean recaptchaEnabled = configurationManager.isRecaptchaForOfflinePaymentEnabled(event);
        model.put("captchaRequestedForOffline", recaptchaEnabled);
        if(recaptchaEnabled) {
            model.put("recaptchaApiKey", configurationManager.getStringConfigValue(Configuration.getSystemConfiguration(RECAPTCHA_API_KEY), null));
        }
        return model;
    }

    private void transitionToOfflinePayment(PaymentSpecification spec) {
        ZonedDateTime deadline = getOfflinePaymentDeadline(spec.getPaymentContext(), configurationManager);
        int updatedReservation = ticketReservationRepository.postponePayment(spec.getReservationId(), Date.from(deadline.toInstant()), spec.getEmail(),
            spec.getCustomerName().getFullName(), spec.getCustomerName().getFirstName(), spec.getCustomerName().getLastName(), spec.getBillingAddress(), spec.getCustomerReference());
        Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got " + updatedReservation);
    }

    static ZonedDateTime getOfflinePaymentDeadline(PaymentContext context, ConfigurationManager configurationManager) {
        Event event = context.getEvent();
        ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
        int waitingPeriod = getOfflinePaymentWaitingPeriod(context, configurationManager).orElse( 0 );
        if(waitingPeriod == 0) {
            log.warn("accepting offline payments the same day is a very bad practice and should be avoided. Please set cash payment as payment method next time");
            //if today is the event start date, then we add a couple of hours.
            //TODO Maybe should we avoid this wrong behavior upfront, in the admin area?
            return now.plusHours(2);
        }
        return now.plusDays(waitingPeriod).truncatedTo(ChronoUnit.HALF_DAYS).with(WorkingDaysAdjusters.defaultWorkingDays());
    }

    static OptionalInt getOfflinePaymentWaitingPeriod(PaymentContext paymentContext, ConfigurationManager configurationManager) {
        Event event = paymentContext.getEvent();
        ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
        ZonedDateTime eventBegin = event.getBegin();
        int daysToBegin = (int) ChronoUnit.DAYS.between(now.toLocalDate(), eventBegin.toLocalDate());
        if (daysToBegin < 0) {
            return OptionalInt.empty();
        }
        int waitingPeriod = configurationManager.getIntConfigValue(paymentContext.narrow(OFFLINE_PAYMENT_DAYS), 5);
        return OptionalInt.of( Math.min(daysToBegin, waitingPeriod) );
    }
}
