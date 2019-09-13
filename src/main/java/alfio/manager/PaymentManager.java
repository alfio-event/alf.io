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
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.ClientServerTokenRequest;
import alfio.model.transaction.capabilities.ExtractPaymentTokenFromTransaction;
import alfio.model.transaction.capabilities.PaymentInfo;
import alfio.model.transaction.capabilities.RefundRequest;
import alfio.repository.AuditingRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.user.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Log4j2
@AllArgsConstructor
public class PaymentManager {

    public static final String PAYMENT_TOKEN = "PAYMENT_TOKEN";

    private final TransactionRepository transactionRepository;
    private final ConfigurationManager configurationManager;
    private final AuditingRepository auditingRepository;
    private final UserRepository userRepository;

    private final List<PaymentProvider> paymentProviders; // injected by Spring

    public Optional<PaymentProvider> lookupProviderByMethod(PaymentMethod paymentMethod, PaymentContext context) {
        return compatibleStream(paymentMethod, context).findFirst();
    }

    Optional<PaymentProvider> lookupProviderByMethodAndCapabilities(PaymentMethod paymentMethod,
                                                                    PaymentContext context,
                                                                    List<Class<? extends Capability>> capabilities) {
        return doLookupProvidersByMethodAndCapabilities(paymentMethod, context, capabilities).findFirst();
    }

    Optional<PaymentProvider> lookupByTransactionAndCapabilities(Transaction transaction, List<Class<? extends Capability>> capabilities) {
        return paymentProviders.stream().filter(p -> p.accept(transaction)).filter(p -> capabilities.stream().allMatch(c -> c.isInstance(p))).findFirst();
    }

    List<PaymentProvider> lookupProvidersByMethodAndCapabilities(PaymentMethod paymentMethod,
                                                                 PaymentContext context,
                                                                 List<Class<? extends Capability>> capabilities) {
        return doLookupProvidersByMethodAndCapabilities(paymentMethod, context, capabilities).collect(Collectors.toList());
    }

    private Stream<PaymentProvider> doLookupProvidersByMethodAndCapabilities(PaymentMethod paymentMethod,
                                                                             PaymentContext context,
                                                                             List<Class<? extends Capability>> capabilities) {
        return compatibleStream(paymentMethod, context)
            .filter(p -> Objects.requireNonNull(capabilities).stream().allMatch(c -> c.isInstance(p)));
    }

    private Stream<PaymentProvider> compatibleStream(PaymentMethod paymentMethod, PaymentContext context) {
        return paymentProviders.stream()
            .filter(p -> p.accept(paymentMethod, context));
    }

    private List<PaymentMethodDTO> getPaymentMethods(PaymentContext context) {
        String blacklist = configurationManager.getFor(ConfigurationKeys.PAYMENT_METHODS_BLACKLIST, context.getConfigurationLevel()).getValueOrDefault("");
        return PaymentProxy.availableProxies()
            .stream()
            .filter(p -> !blacklist.contains(p.getKey()))
            .map(p -> {
                Optional<PaymentProvider> paymentProvider = lookupProviderByMethod(p.getPaymentMethod(), context);
                PaymentMethodDTO.PaymentMethodStatus status = paymentProvider.isPresent() ? PaymentMethodDTO.PaymentMethodStatus.ACTIVE : PaymentMethodDTO.PaymentMethodStatus.ERROR;
                return new PaymentMethodDTO(p, status);
            })
            .collect(Collectors.toList());
    }

    public List<PaymentMethodDTO> getPaymentMethods(Event event) {
        return getPaymentMethods(new PaymentContext(event));
    }

    public List<PaymentMethodDTO> getPaymentMethods(int organizationId) {
        return getPaymentMethods(new PaymentContext(null, ConfigurationLevel.organization(organizationId)));
    }

    public boolean refund(TicketReservation reservation, Event event, Integer amount, String username) {
        Transaction transaction = transactionRepository.loadByReservationId(reservation.getId());

        boolean res = lookupProviderByMethod(reservation.getPaymentMethod().getPaymentMethod(), new PaymentContext(event))
            .filter(RefundRequest.class::isInstance)
            .map(paymentProvider -> ((RefundRequest)paymentProvider).refund(transaction, event, amount))
            .orElse(false);

        if(res) {
            Map<String, Object> changes = new HashMap<>();
            changes.put("refund", amount != null ? amount.toString() : "full");
            changes.put("paymentMethod", reservation.getPaymentMethod().toString());
            auditingRepository.insert(reservation.getId(), userRepository.findIdByUserName(username).orElse(null),
                event.getId(),
                Audit.EventType.REFUND, new Date(), Audit.EntityType.RESERVATION, reservation.getId(),
                Collections.singletonList(changes));
        }

        return res;
    }

    TransactionAndPaymentInfo getInfo(TicketReservation reservation, Event event) {
        Optional<TransactionAndPaymentInfo> maybeTransaction = transactionRepository.loadOptionalByReservationId(reservation.getId())
            .map(transaction -> internalGetInfo(reservation, event, transaction));
        maybeTransaction.ifPresent(info -> {
            try {
                Transaction transaction = info.getTransaction();
                String transactionId = transaction.getTransactionId();
                PaymentInformation paymentInformation = info.getPaymentInformation();
                if(paymentInformation != null && feesUpdated(transaction, paymentInformation)) {
                    transactionRepository.updateFees(transactionId, reservation.getId(), safeParseLong(paymentInformation.getPlatformFee()), safeParseLong(paymentInformation.getFee()));
                }
            } catch (Exception e) {
                log.warn("cannot update fees", e);
            }
        });
        return maybeTransaction.orElseGet(() -> new TransactionAndPaymentInfo(reservation.getPaymentMethod(),null, new PaymentInformation(reservation.getPaidAmount(), null, null, null)));
    }

    private boolean feesUpdated(Transaction transaction, PaymentInformation paymentInformation) {
        return transaction.getPlatformFee() != safeParseLong(paymentInformation.getPlatformFee())
            || transaction.getGatewayFee()  != safeParseLong(paymentInformation.getFee());
    }

    private TransactionAndPaymentInfo internalGetInfo(TicketReservation reservation, Event event, Transaction transaction) {
        return Optional.ofNullable(reservation.getPaymentMethod())
            .flatMap(pm -> lookupProviderByMethod(pm.getPaymentMethod(), new PaymentContext(event)))
            .filter(PaymentInfo.class::isInstance)
            .map(provider -> {
                Optional<PaymentInformation> info = ((PaymentInfo) provider).getInfo(transaction, event);
                return new TransactionAndPaymentInfo(reservation.getPaymentMethod(), transaction, info.orElse(null));
            })
            .orElseGet(() -> new TransactionAndPaymentInfo(reservation.getPaymentMethod(), transaction, new PaymentInformation(reservation.getPaidAmount(), null, String.valueOf(transaction.getGatewayFee()), String.valueOf(transaction.getPlatformFee()))));
    }

    private static long safeParseLong(String src) {
        return Optional.ofNullable(src).map(Long::parseLong).orElse(0L);
    }

    public Map<String, ?> loadModelOptionsFor(List<PaymentProxy> activePaymentMethods, Event event) {
        PaymentContext context = new PaymentContext(event);
        return activePaymentMethods.stream()
            .flatMap(pp -> getProviderOptions(context, pp))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Stream<? extends Map.Entry<String, ?>> getProviderOptions(PaymentContext context, PaymentProxy pp) {
        return lookupProviderByMethod(pp.getPaymentMethod(), context).stream()
            .flatMap(it -> it.getModelOptions(context).entrySet().stream().filter(kv -> kv.getValue() != null));
    }

    public PaymentToken buildPaymentToken(String gatewayToken, PaymentProxy proxy, PaymentContext context) {
        return lookupProviderByMethod(proxy.getPaymentMethod(), context)
            .filter(ClientServerTokenRequest.class::isInstance)
            .map(ClientServerTokenRequest.class::cast)
            .map(pp -> pp.buildPaymentToken(gatewayToken, context))
            .orElse(null);
    }

    public Optional<PaymentResult> getTransactionStatus(TicketReservation reservation, PaymentMethod paymentMethod) {
        return transactionRepository.loadOptionalByReservationId(reservation.getId())
            .filter(transaction -> transaction.getPaymentProxy().getPaymentMethod() == paymentMethod)
            .map(transaction -> {
                switch(transaction.getStatus()) {
                    case COMPLETE:
                        return PaymentResult.successful(transaction.getPaymentId());
                    case FAILED:
                        return PaymentResult.failed(null);
                    default:
                        return PaymentResult.initialized(transaction.getPaymentId());
                }
            });
    }

    public Optional<PaymentToken> getPaymentToken(String reservationId) {
        return transactionRepository.loadOptionalByReservationId(reservationId)
            .filter(t->t.getStatus() == Transaction.Status.PENDING)
            .flatMap(t -> {
            if(t.getMetadata().containsKey(PAYMENT_TOKEN)) {
                return lookupByTransactionAndCapabilities(t, List.of(ExtractPaymentTokenFromTransaction.class))
                    .map(ExtractPaymentTokenFromTransaction.class::cast)
                    .flatMap(paymentProvider -> paymentProvider.extractToken(t));
            }
            return Optional.empty();
        });
    }

    public boolean removePaymentTokenReservation(String reservationId) {
        return transactionRepository.loadOptionalByReservationId(reservationId).filter(t->t.getStatus() == Transaction.Status.PENDING)
            .map(t -> {
                if (t.getMetadata().containsKey(PAYMENT_TOKEN)) {
                    return transactionRepository.invalidateById(t.getId()) == 1;
                } else {
                    return false;
                }
            }).orElse(false);
    }

    @Data
    public static final class PaymentMethodDTO {

        public enum PaymentMethodStatus {
            ACTIVE, ERROR
        }

        private final PaymentProxy paymentProxy;

        private final PaymentMethodStatus status;

        public boolean isActive() {
            return status == PaymentMethodStatus.ACTIVE;
        }

        public Set<String> getOnlyForCurrency() {
            return paymentProxy.getOnlyForCurrency();
        }
    }
}
