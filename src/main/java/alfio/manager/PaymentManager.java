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
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNullElse;

@Component
public class PaymentManager {

    public static final String PAYMENT_TOKEN = "PAYMENT_TOKEN";

    private static final Logger log = LoggerFactory.getLogger(PaymentManager.class);

    private final TransactionRepository transactionRepository;
    private final ConfigurationManager configurationManager;
    private final AuditingRepository auditingRepository;
    private final UserRepository userRepository;
    private final ExtensionManager extensionManager;

    private final List<PaymentProvider> paymentProviders; // injected by Spring

    public PaymentManager(TransactionRepository transactionRepository,
                          ConfigurationManager configurationManager,
                          AuditingRepository auditingRepository,
                          UserRepository userRepository,
                          ExtensionManager extensionManager,
                          List<PaymentProvider> paymentProviders) {
        this.transactionRepository = transactionRepository;
        this.configurationManager = configurationManager;
        this.auditingRepository = auditingRepository;
        this.userRepository = userRepository;
        this.extensionManager = extensionManager;
        this.paymentProviders = paymentProviders;
    }

    public Optional<PaymentProvider> lookupProviderByTransactionAndCapabilities(Transaction transaction, List<Class<? extends Capability>> capabilities) {
        return paymentProviders.stream()
            .filter(filterByCapabilities(capabilities))
            .filter(paymentProvider -> paymentProvider.accept(transaction))
            .findFirst();
    }

    Optional<PaymentProvider> lookupProviderByMethodAndCapabilities(PaymentMethod paymentMethod,
                                                                    PaymentContext context,
                                                                    TransactionRequest transactionRequest,
                                                                    List<Class<? extends Capability>> capabilities) {
        return compatibleStream(paymentMethod, context, transactionRequest)
            .filter(p -> Objects.requireNonNull(capabilities).stream().allMatch(c -> c.isInstance(p))).findFirst();
    }

    public Stream<PaymentProvider> streamActiveProvidersByProxy(PaymentProxy paymentProxy, PaymentContext paymentContext) {
        return streamActiveProvidersByProxyAndCapabilities(paymentProxy, paymentContext, List.of());
    }

    /**
     * validates the compatibility between the selected proxies, and returns the conflicts
     *
     * @param paymentProxies the {@link PaymentProxy PaymentProxies} to validate
     * @param organizationId the organization for which the validation is made
     * @return the conflicting {@link PaymentProxy proxies}
     */
    List<Map.Entry<PaymentMethod, Set<PaymentProxy>>> validateSelection(List<PaymentProxy> paymentProxies, int organizationId) {
        var paymentContext = new PaymentContext(null, ConfigurationLevel.organization(organizationId));

        Map<PaymentMethod, Set<PaymentProxy>> proxiesByMethod = paymentProxies.stream()
            .flatMap(proxy -> streamProvidersByProxyAndCapabilities(proxy, List.of()))
            .map(provider -> Pair.of(provider.getPaymentProxy(), provider.getSupportedPaymentMethods(paymentContext, TransactionRequest.empty())))
            .flatMap(pair -> pair.getValue().stream().map(pm -> Pair.of(pm, pair.getKey()))) // flip
            .collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toSet())));

        return proxiesByMethod.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .toList();
    }

    private Stream<PaymentProvider> streamProvidersByProxyAndCapabilities(PaymentProxy paymentProxy,
                                                                  List<Class<? extends Capability>> capabilities) {
        return paymentProviders.stream()
            .filter(pp -> pp.getPaymentProxy() == paymentProxy)
            .filter(filterByCapabilities(capabilities));
    }

    Stream<PaymentProvider> streamActiveProvidersByProxyAndCapabilities(PaymentProxy paymentProxy,
                                                                                PaymentContext paymentContext,
                                                                                List<Class<? extends Capability>> capabilities) {
        return streamProvidersByProxyAndCapabilities(paymentProxy, capabilities).filter((pp) -> pp.isActive(paymentContext));
    }

    private static Predicate<PaymentProvider> filterByCapabilities(List<Class<? extends Capability>> capabilities) {
        return p -> capabilities.isEmpty() || capabilities.stream().allMatch(c -> c.isInstance(p));
    }

    private Stream<PaymentProvider> compatibleStream(PaymentMethod paymentMethod, PaymentContext context, TransactionRequest transactionRequest) {
        return paymentProviders.stream()
            .filter(p -> p.accept(paymentMethod, context, transactionRequest));
    }

    private List<PaymentMethodDTO> getPaymentMethods(PaymentContext context, TransactionRequest transactionRequest) {
        String blacklist = configurationManager.getFor(ConfigurationKeys.PAYMENT_METHODS_BLACKLIST, context.getConfigurationLevel()).getValueOrDefault("");
        var proxies = Optional.ofNullable(context.getPurchaseContext()).map(PurchaseContext::getAllowedPaymentProxies).orElseGet(PaymentProxy::availableProxies);
        return proxies.stream()
            .filter(p -> !blacklist.contains(p.getKey()))
            .map(proxy -> Pair.of(proxy, paymentMethodsByProxy(context, transactionRequest, proxy)))
            .flatMap(pair -> pair.getRight().stream().map(pm -> new PaymentMethodDTO(pair.getLeft(), pm, PaymentMethodDTO.PaymentMethodStatus.ACTIVE)))
            .toList();
    }

    private Set<PaymentMethod> paymentMethodsByProxy(PaymentContext context, TransactionRequest transactionRequest, PaymentProxy proxy) {
        return paymentProviders.stream()
            .filter(pp -> pp.getPaymentProxy() == proxy)
            .flatMap(pp -> pp.getSupportedPaymentMethods(context, transactionRequest).stream())
            .collect(Collectors.toSet());
    }

    public List<PaymentMethodDTO> getPaymentMethods(PurchaseContext purchaseContext, TransactionRequest transactionRequest) {
        return getPaymentMethods(new PaymentContext(purchaseContext), transactionRequest);
    }

    public List<PaymentMethodDTO> getPaymentMethods(int organizationId) {
        return getPaymentMethods(new PaymentContext(null, ConfigurationLevel.organization(organizationId)), TransactionRequest.empty());
    }

    public boolean refund(TicketReservation reservation, PurchaseContext purchaseContext, Integer amount, String username) {
        Transaction transaction = transactionRepository.loadByReservationId(reservation.getId());

        boolean res = lookupProviderByTransactionAndCapabilities(transaction, List.of(RefundRequest.class))
            .map(paymentProvider -> ((RefundRequest)paymentProvider).refund(transaction, purchaseContext, amount))
            .orElse(false);

        Map<String, Object> changes = Map.of(
            "refund", amount != null ? amount.toString() : "full",
            "paymentMethod", reservation.getPaymentMethod().toString()
        );
        if(res) {
            auditingRepository.insert(reservation.getId(), userRepository.findIdByUserName(username).orElse(null),
                    purchaseContext,
                Audit.EventType.REFUND, new Date(), Audit.EntityType.RESERVATION, reservation.getId(),
                Collections.singletonList(changes));
            extensionManager.handleRefund(purchaseContext, reservation, getInfo(reservation, purchaseContext));
        } else {
            auditingRepository.insert(reservation.getId(), userRepository.findIdByUserName(username).orElse(null),
                    purchaseContext,
                Audit.EventType.REFUND_ATTEMPT_FAILED, new Date(), Audit.EntityType.RESERVATION, reservation.getId(),
                Collections.singletonList(changes));
        }

        return res;
    }

    public TransactionAndPaymentInfo getInfo(TicketReservation reservation, PurchaseContext purchaseContext) {
        Optional<TransactionAndPaymentInfo> maybeTransaction = transactionRepository.loadOptionalByReservationId(reservation.getId())
            .map(transaction -> internalGetInfo(reservation, purchaseContext, transaction));
        maybeTransaction.ifPresent(info -> {
            try {
                Transaction transaction = info.transaction();
                String transactionId = transaction.getTransactionId();
                PaymentInformation paymentInformation = info.paymentInformation();
                if(paymentInformation != null && feesUpdated(transaction, paymentInformation)) {
                    transactionRepository.updateFees(transactionId, reservation.getId(), safeParseLong(paymentInformation.getPlatformFee()), safeParseLong(paymentInformation.getFee()));
                }
            } catch (Exception e) {
                log.warn("cannot update fees", e);
            }
        });
        return maybeTransaction.orElseGet(() -> new TransactionAndPaymentInfo(reservation.getPaymentMethod(),null, new PaymentInformation(reservation.getPaidAmount(), null, null, null)));
    }

    public void updateTransactionDetails(String reservationId,
                                         String notes,
                                         ZonedDateTime timestamp,
                                         Principal principal) {
        // TODO check if user can modify transaction once we have a centralized service.
        var existingTransaction = transactionRepository.loadByReservationId(reservationId);
        Validate.isTrue(existingTransaction.isTimestampEditable() || timestamp == null, "Cannot modify timestamp");
        var existingMetadata = new HashMap<>(existingTransaction.getMetadata());
        existingMetadata.put(Transaction.NOTES_KEY, notes);
        int result = transactionRepository.updateDetailsById(existingTransaction.getId(), existingMetadata, requireNonNullElse(timestamp, existingTransaction.getTimestamp()));
        Validate.isTrue(result == 1, "Expected 1, got " + result);
    }

    private boolean feesUpdated(Transaction transaction, PaymentInformation paymentInformation) {
        return transaction.getPlatformFee() != safeParseLong(paymentInformation.getPlatformFee())
            || transaction.getGatewayFee()  != safeParseLong(paymentInformation.getFee());
    }

    private TransactionAndPaymentInfo internalGetInfo(TicketReservation reservation, PurchaseContext purchaseContext, Transaction transaction) {
        return lookupProviderByTransactionAndCapabilities(transaction, List.of(PaymentInfo.class))
            .map(provider -> {
                Optional<PaymentInformation> info = ((PaymentInfo) provider).getInfo(transaction, purchaseContext);
                return new TransactionAndPaymentInfo(reservation.getPaymentMethod(), transaction, info.orElse(null));
            })
            .orElseGet(() -> {
                //
                return new TransactionAndPaymentInfo(
                    reservation.getPaymentMethod(),
                    transaction,
                    new PaymentInformation(
                        reservation.getPaidAmount(),
                        null,
                        String.valueOf(transaction.getGatewayFee()),
                        String.valueOf(transaction.getPlatformFee())));
            });
    }

    private static long safeParseLong(String src) {
        return Optional.ofNullable(src).map(Long::parseLong).orElse(0L);
    }

    public Map<String, ?> loadModelOptionsFor(List<PaymentProxy> activePaymentMethods, PurchaseContext purchaseContext) {
        PaymentContext context = new PaymentContext(purchaseContext);
        return activePaymentMethods.stream()
            .flatMap(pp -> getProviderOptions(context, pp))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Stream<? extends Map.Entry<String, ?>> getProviderOptions(PaymentContext context, PaymentProxy pp) {
        return streamActiveProvidersByProxy(pp, context)
            .flatMap(it -> it.getModelOptions(context).entrySet().stream().filter(kv -> kv.getValue() != null));
    }

    public PaymentToken buildPaymentToken(String gatewayToken, PaymentProxy proxy, PaymentContext context) {
        return streamActiveProvidersByProxyAndCapabilities(proxy, context, List.of(ClientServerTokenRequest.class))
            .map(ClientServerTokenRequest.class::cast)
            .map(pp -> pp.buildPaymentToken(gatewayToken, context))
            .findFirst()
            .orElse(null);
    }

    public Optional<PaymentResult> getTransactionStatus(TicketReservation reservation, PaymentMethod paymentMethod) {
        return transactionRepository.loadOptionalByReservationId(reservation.getId())
            .filter(transaction -> transaction.getPaymentProxy().getPaymentMethod() == paymentMethod)
            .map(transaction -> {
                return switch (transaction.getStatus()) {
                    case COMPLETE -> PaymentResult.successful(transaction.getPaymentId());
                    case FAILED -> PaymentResult.failed(null);
                    default -> PaymentResult.initialized(transaction.getPaymentId());
                };
            });
    }

    public Optional<PaymentToken> getPaymentToken(String reservationId) {
        return transactionRepository.loadOptionalByReservationId(reservationId)
            .filter(t->t.getStatus() == Transaction.Status.PENDING)
            .flatMap(t -> {
                if(t.getMetadata().containsKey(PAYMENT_TOKEN)) {
                    return lookupProviderByTransactionAndCapabilities(t, List.of(ExtractPaymentTokenFromTransaction.class))
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


    public static final class PaymentMethodDTO {

        public enum PaymentMethodStatus {
            ACTIVE, ERROR
        }

        public PaymentMethodDTO(PaymentProxy paymentProxy, PaymentMethod paymentMethod, PaymentMethodStatus status) {
            this.paymentProxy = paymentProxy;
            this.paymentMethod = paymentMethod;
            this.status = status;
        }

        private final PaymentProxy paymentProxy;
        private final PaymentMethod paymentMethod;

        private final PaymentMethodStatus status;

        public boolean isActive() {
            return status == PaymentMethodStatus.ACTIVE;
        }

        @Deprecated(forRemoval = true)
        public Set<String> getOnlyForCurrency() {
            return paymentProxy.getOnlyForCurrency();
        }

        public PaymentMethod getPaymentMethod() {
            return paymentMethod;
        }

        public PaymentProxy getPaymentProxy() {
            return paymentProxy;
        }

        public PaymentMethodStatus getStatus() {
            return status;
        }
    }
}
