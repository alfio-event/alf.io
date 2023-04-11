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
package alfio.manager.payment;

import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.PurchaseContext;
import alfio.model.TicketReservation;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.*;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;
import alfio.util.ClockProvider;
import alfio.util.WorkingDaysAdjusters;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static alfio.manager.TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID;
import static alfio.model.TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT;
import static alfio.model.system.ConfigurationKeys.*;

@Component
@Log4j2
@AllArgsConstructor
public class BankTransferManager implements PaymentProvider {

    private static final EnumSet<ConfigurationKeys> OPTIONS_TO_LOAD = EnumSet.of(BANK_TRANSFER_ENABLED,
        DEFERRED_BANK_TRANSFER_ENABLED, OFFLINE_PAYMENT_DAYS, REVOLUT_ENABLED, REVOLUT_API_KEY,
        REVOLUT_LIVE_MODE, REVOLUT_MANUAL_REVIEW);
    private final ConfigurationManager configurationManager;
    private final TicketReservationRepository ticketReservationRepository;
    private final TransactionRepository transactionRepository;
    private final ClockProvider clockProvider;

    @Override
    public Set<PaymentMethod> getSupportedPaymentMethods(PaymentContext paymentContext, TransactionRequest transactionRequest) {
        return EnumSet.of(PaymentMethod.BANK_TRANSFER);
    }

    @Override
    public PaymentProxy getPaymentProxy() {
        return PaymentProxy.OFFLINE;
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext paymentContext, TransactionRequest transactionRequest) {
        var options = options(paymentContext);
        return bankTransferEnabledForMethod(paymentMethod, paymentContext, options)
            && !options.get(REVOLUT_ENABLED).getValueAsBooleanOrDefault();
    }

    boolean bankTransferEnabledForMethod(PaymentMethod paymentMethod, PaymentContext paymentContext, Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> options) {
        if(paymentMethod != PaymentMethod.BANK_TRANSFER) {
            return false;
        }
        return bankTransferActive(paymentContext, options);
    }

    boolean bankTransferActive(PaymentContext paymentContext, Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> options) {
        return options.get(BANK_TRANSFER_ENABLED).getValueAsBooleanOrDefault()
            && (paymentContext.getPurchaseContext() == null || getOfflinePaymentWaitingPeriod(paymentContext.getPurchaseContext(), options.get(OFFLINE_PAYMENT_DAYS).getValueAsIntOrDefault(5)).orElse(0) > 0);
    }

    Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> options(PaymentContext paymentContext) {
        return configurationManager.getFor(OPTIONS_TO_LOAD, paymentContext.getConfigurationLevel());
    }

    boolean isPaymentDeferredEnabled(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> options) {
        return options.get(DEFERRED_BANK_TRANSFER_ENABLED).getValueAsBooleanOrDefault();
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        transitionToOfflinePayment(spec);
        overrideExistingTransactions(spec);
        return PaymentResult.successful(NOT_YET_PAID_TRANSACTION_ID);
    }

    void overrideExistingTransactions(PaymentSpecification spec) {
        PaymentManagerUtils.invalidateExistingTransactions(spec.getReservationId(), transactionRepository);
        transactionRepository.insert(UUID.randomUUID().toString(), null,
            spec.getReservationId(), ZonedDateTime.now(clockProvider.getClock()), spec.getPriceWithVAT(), spec.getCurrencyCode(),
            "", PaymentProxy.OFFLINE.name(), 0L, 0L, Transaction.Status.PENDING, Map.of());
    }

    @Override
    public Map<String, ?> getModelOptions(PaymentContext context) {
        OptionalInt delay = getOfflinePaymentWaitingPeriod(context, configurationManager);
        PurchaseContext purchaseContext = context.getPurchaseContext();
        if(delay.isEmpty()) {
            log.error("Already started event {} has been found with OFFLINE payment enabled" , purchaseContext.getDisplayName());
        }
        Map<String, Object> model = new HashMap<>();
        model.put("delayForOfflinePayment", Math.max(1, delay.orElse( 0 )));
        boolean recaptchaEnabled = configurationManager.isRecaptchaForOfflinePaymentAndFreeEnabled(purchaseContext.getConfigurationLevel());
        model.put("captchaRequestedForOffline", recaptchaEnabled);
        if(recaptchaEnabled) {
            model.put("recaptchaApiKey", configurationManager.getForSystem(RECAPTCHA_API_KEY).getValue().orElse(null));
        }
        return model;
    }

    private void transitionToOfflinePayment(PaymentSpecification spec) {
        ZonedDateTime deadline = getOfflinePaymentDeadline(spec.getPaymentContext(), configurationManager);
        postponePayment(spec, OFFLINE_PAYMENT, deadline);
    }

    void postponePayment(PaymentSpecification spec, TicketReservation.TicketReservationStatus status, ZonedDateTime deadline) {
        int updatedReservation = ticketReservationRepository.postponePayment(spec.getReservationId(), status, Date.from(deadline.toInstant()), spec.getEmail(),
            spec.getCustomerName().getFullName(), spec.getCustomerName().getFirstName(), spec.getCustomerName().getLastName(), spec.getBillingAddress(), spec.getCustomerReference());
        Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got " + updatedReservation);
    }

    public static ZonedDateTime getOfflinePaymentDeadline(PaymentContext context, ConfigurationManager configurationManager) {
        PurchaseContext purchaseContext = context.getPurchaseContext();
        ZonedDateTime now = purchaseContext.now(ClockProvider.clock());
        int waitingPeriod = getOfflinePaymentWaitingPeriod(context, configurationManager).orElse( 0 );
        if(waitingPeriod == 0) {
            log.warn("accepting offline payments the same day is a very bad practice and should be avoided. Please set cash payment as payment method next time");
            //if today is the event start date, then we add a couple of hours.
            //TODO Maybe should we avoid this wrong behavior upfront, in the admin area?
            return now.plusHours(2);
        }
        return now.plusDays(waitingPeriod).truncatedTo(ChronoUnit.HALF_DAYS).with(WorkingDaysAdjusters.defaultWorkingDays());
    }

    public static OptionalInt getOfflinePaymentWaitingPeriod(PaymentContext paymentContext, ConfigurationManager configurationManager) {
        PurchaseContext purchaseContext = paymentContext.getPurchaseContext();
        return getOfflinePaymentWaitingPeriod(purchaseContext, configurationManager.getFor(OFFLINE_PAYMENT_DAYS, purchaseContext.getConfigurationLevel()).getValueAsIntOrDefault(5));
    }

    private static OptionalInt getOfflinePaymentWaitingPeriod(PurchaseContext purchaseContext, int configuredValue) {
        ZonedDateTime now = purchaseContext.now(ClockProvider.clock());
        ZonedDateTime maxDate = purchaseContext.event()
            .map(BankTransferManager::getMaxPaymentDate)
            .orElse(purchaseContext.getBegin());
        int daysToBegin = (int) ChronoUnit.DAYS.between(now.toLocalDate(), maxDate.toLocalDate());
        if (daysToBegin < 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of( Math.min(daysToBegin, configuredValue) );
    }

    private static ZonedDateTime getMaxPaymentDate(Event event) {
        if (event.getSameDay()) {
            return event.getBegin();
        } else {
            return event.getEnd();
        }
    }

    @Override
    public boolean accept(Transaction transaction) {
        return PaymentProxy.OFFLINE == transaction.getPaymentProxy();
    }

    @Override
    public PaymentMethod getPaymentMethodForTransaction(Transaction transaction) {
        return PaymentMethod.BANK_TRANSFER;
    }

    @Override
    public boolean isActive(PaymentContext paymentContext) {
        return bankTransferActive(paymentContext, options(paymentContext));
    }
}
