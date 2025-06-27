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

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProvider;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.Transaction;
import alfio.model.transaction.TransactionRequest;
import alfio.model.transaction.UserDefinedOfflinePaymentMethod;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.ClockProvider;
import alfio.util.Json;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;

import static alfio.manager.TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID;
import static alfio.model.TicketReservation.TicketReservationStatus.CUSTOM_OFFLINE_PAYMENT;

@Component
public class CustomOfflinePaymentManager implements PaymentProvider {
    private final ClockProvider clockProvider;
    private final ConfigurationRepository configurationRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final TransactionRepository transactionRepository;
    private final ConfigurationManager configurationManager;
    private static final Logger log = LoggerFactory.getLogger(CustomOfflinePaymentManager.class);

    public CustomOfflinePaymentManager(
            ClockProvider clockProvider,
            ConfigurationRepository configurationRepository,
            TicketReservationRepository ticketReservationRepository,
            TransactionRepository transactionRepository,
            ConfigurationManager configurationManager) {
        this.clockProvider = clockProvider;
        this.configurationRepository = configurationRepository;
        this.ticketReservationRepository = ticketReservationRepository;
        this.transactionRepository = transactionRepository;
        this.configurationManager = configurationManager;
    }

    @Override
    public Set<? extends PaymentMethod> getSupportedPaymentMethods(
        PaymentContext paymentContext,
        TransactionRequest transactionRequest
    ) {
        OptionalInt maybeOrgId = paymentContext.getConfigurationLevel().getOrganizationId();

        if (!maybeOrgId.isPresent()) {
            return Set.of();
        }

        var orgId = maybeOrgId.getAsInt();

        var maybeConfig = configurationRepository
            .findByKeyAtOrganizationLevel(orgId, ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.getValue());

        if(!maybeConfig.isPresent()) {
            return Set.of();
        }

        var paymentMethods = configurationManager
            .getFor(ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS, ConfigurationLevel.organization(orgId))
            .getValue()
            .map(v -> Json.fromJson(v, new TypeReference<Set<UserDefinedOfflinePaymentMethod>>() {}))
            .orElse(new HashSet<UserDefinedOfflinePaymentMethod>());

        return paymentMethods;
    }

    @Override
    public PaymentProxy getPaymentProxy() {
        return PaymentProxy.CUSTOM_OFFLINE;
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context, TransactionRequest transactionRequest) {
        // TODO Auto-generated method stub
        // throw new UnsupportedOperationException("Unimplemented method 'accept'");
        return true;
    }

    @Override
    public boolean accept(Transaction transaction) {
        // TODO Auto-generated method stub
        // throw new UnsupportedOperationException("Unimplemented method 'accept'");
        return true;
    }

    @Override
    public PaymentMethod getPaymentMethodForTransaction(Transaction transaction) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPaymentMethodForTransaction'");
    }

    @Override
    public boolean isActive(PaymentContext paymentContext) {
        // throw new UnsupportedOperationException("Unimplemented method 'isActive'");
        return true;
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        // GET EVENT END TIME AS DEADLINE
        ZonedDateTime deadline = spec.getPurchaseContext().event().get().getEnd();

        // POST PONE
        int updatedReservation = ticketReservationRepository.postponePayment(
                spec.getReservationId(),
                CUSTOM_OFFLINE_PAYMENT,
                this.getPaymentProxy().name(),
                Date.from(deadline.toInstant()),
                spec.getEmail(),
                spec.getCustomerName().getFullName(),
                spec.getCustomerName().getFirstName(),
                spec.getCustomerName().getLastName(),
                spec.getBillingAddress(),
                spec.getCustomerReference());
        Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got " + updatedReservation);

        // OVERRIDE EXISTING TRANSACTION
        PaymentManagerUtils.invalidateExistingTransactions(spec.getReservationId(), transactionRepository);
        transactionRepository.insert(
                UUID.randomUUID().toString(),
                null,
                spec.getReservationId(),
                ZonedDateTime.now(clockProvider.getClock()),
                spec.getPriceWithVAT(),
                spec.getCurrencyCode(),
                "",
                PaymentProxy.CUSTOM_OFFLINE.name(),
                0L,
                0L,
                Transaction.Status.PENDING,
                Map.of("selectedPaymentMethod", spec.getSelectedPaymentMethod().getPaymentMethodId()));

        // RETURN RESULT
        return PaymentResult.successful(NOT_YET_PAID_TRANSACTION_ID);
    }
}
