package alfio.manager.payment;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import alfio.manager.support.PaymentResult;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProvider;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.Transaction;
import alfio.model.transaction.TransactionRequest;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.ClockProvider;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import static alfio.manager.TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID;
import static alfio.model.TicketReservation.TicketReservationStatus.CUSTOM_OFFLINE_PAYMENT;

@Component
public class CustomOfflinePaymentManager implements PaymentProvider {
    private final ClockProvider clockProvider;
    private final ConfigurationRepository configurationRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final TransactionRepository transactionRepository;
    private static final Logger log = LoggerFactory.getLogger(CustomOfflinePaymentManager.class);

    public CustomOfflinePaymentManager(
        ClockProvider clockProvider,
        ConfigurationRepository configurationRepository,
        TicketReservationRepository ticketReservationRepository,
        TransactionRepository transactionRepository
    ) {
        this.clockProvider = clockProvider;
        this.configurationRepository = configurationRepository;
        this.ticketReservationRepository = ticketReservationRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public Set<PaymentMethod> getSupportedPaymentMethods(PaymentContext paymentContext,
            TransactionRequest transactionRequest) {
        OptionalInt orgId = paymentContext.getConfigurationLevel().getOrganizationId();

        if(orgId.isPresent()) {
            configurationRepository.findByKeyAtOrganizationLevel(orgId.getAsInt(), "CUSTOM_OFFLINE_PAYMENTS");
        }

        return Set.of(PaymentMethod.ETRANSFER);
    }

    @Override
    public PaymentProxy getPaymentProxy() {
        return PaymentProxy.CUSTOM_OFFLINE;
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context, TransactionRequest transactionRequest) {
        // TODO Auto-generated method stub
        //throw new UnsupportedOperationException("Unimplemented method 'accept'");
        return true;
    }

    @Override
    public boolean accept(Transaction transaction) {
        // TODO Auto-generated method stub
        //throw new UnsupportedOperationException("Unimplemented method 'accept'");
        return true;
    }

    @Override
    public PaymentMethod getPaymentMethodForTransaction(Transaction transaction) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPaymentMethodForTransaction'");
    }

    @Override
    public boolean isActive(PaymentContext paymentContext) {
        //throw new UnsupportedOperationException("Unimplemented method 'isActive'");
        return true;
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        // TODO: Add Real Payment Processing

        // GET EVENT END TIME AS DEADLINE
        ZonedDateTime deadline = spec.getPurchaseContext().event().get().getEnd();

        // POST PONE
        int updatedReservation = ticketReservationRepository.postponePayment(
            spec.getReservationId(),
            CUSTOM_OFFLINE_PAYMENT,
            Date.from(deadline.toInstant()),
            spec.getEmail(),
            spec.getCustomerName().getFullName(),
            spec.getCustomerName().getFirstName(),
            spec.getCustomerName().getLastName(),
            spec.getBillingAddress(),
            spec.getCustomerReference()
        );
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
            PaymentProxy.OFFLINE.name(),
            0L,
            0L,
            Transaction.Status.PENDING,
            Map.of()
        );

        // RETURN RESULT
        return PaymentResult.successful(NOT_YET_PAID_TRANSACTION_ID);
    }
}
