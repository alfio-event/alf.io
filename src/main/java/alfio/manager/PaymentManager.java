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

import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProvider;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.Transaction;
import alfio.repository.AuditingRepository;
import alfio.repository.TicketRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.user.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Log4j2
@AllArgsConstructor
public class PaymentManager {

    private final StripeCreditCardManager stripeCreditCardManager;
    private final PaypalManager paypalManager;
    private final MollieCreditCardManager mollieCreditCardManager;
    private final TransactionRepository transactionRepository;
    private final ConfigurationManager configurationManager;
    private final AuditingRepository auditingRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;

    private final List<PaymentProvider> paymentProviders; // injected by Spring

    public Optional<PaymentProvider> lookupProviderByMethod( PaymentMethod paymentMethod, Function<ConfigurationKeys, Configuration.ConfigurationPathKey> pathKeyProvider ) {
        return paymentProviders.stream()
                .filter( p -> p.accept( paymentMethod, pathKeyProvider ) )
                .findFirst();
    }

    public List<PaymentMethodDTO> getPaymentMethods( int organizationId) {
        String blacklist = configurationManager.getStringConfigValue(Configuration.from(organizationId, ConfigurationKeys.PAYMENT_METHODS_BLACKLIST), "");
        return PaymentProxy.availableProxies()
            .stream()
            .filter(p -> !blacklist.contains(p.getKey()))
            .map(p -> {
                PaymentMethodDTO.PaymentMethodStatus status = ConfigurationKeys.byCategory(p.getSettingCategories()).stream()
                    .allMatch(c -> c.isBackedByDefault() || configurationManager.getStringConfigValue(Configuration.from(organizationId, c)).filter(StringUtils::isNotEmpty).isPresent()) ? PaymentMethodDTO.PaymentMethodStatus.ACTIVE : PaymentMethodDTO.PaymentMethodStatus.ERROR;
                return new PaymentMethodDTO(p, status);
            })
            .collect(Collectors.toList());
    }

    public String getStripePublicKey(Event event) {
        return stripeCreditCardManager.getPublicKey(event);
    }

    public boolean refund(TicketReservation reservation, Event event, Optional<Integer> amount, String username) {
        Transaction transaction = transactionRepository.loadByReservationId(reservation.getId());
        boolean res;
        switch(reservation.getPaymentMethod()) {
            case PAYPAL:
                res = paypalManager.refund(transaction, event, amount);
                break;
            case STRIPE:
                res = stripeCreditCardManager.refund(transaction, event, amount);
                break;
            default:
                throw new IllegalStateException("Cannot refund ");
        }

        if(res) {
            Map<String, Object> changes = new HashMap<>();
            changes.put("refund", amount.map(Object::toString).orElse("full"));
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
            .map(transaction -> {
                switch(reservation.getPaymentMethod()) {
                    case PAYPAL:
                        return new TransactionAndPaymentInfo(reservation.getPaymentMethod(), transaction, paypalManager.getInfo(transaction, event).orElse(null));
                    case STRIPE:
                        return new TransactionAndPaymentInfo(reservation.getPaymentMethod(), transaction, stripeCreditCardManager.getInfo(transaction, event).orElse(null));
                    default:
                        return new TransactionAndPaymentInfo(reservation.getPaymentMethod(), transaction, new PaymentInformation(reservation.getPaidAmount(), null, String.valueOf(transaction.getGatewayFee()), String.valueOf(transaction.getPlatformFee())));
                }
            });
        maybeTransaction.ifPresent(info -> {
            try {
                Transaction transaction = info.getTransaction();
                String transactionId = transaction.getTransactionId();
                PaymentInformation paymentInformation = info.getPaymentInformation();
                if(paymentInformation != null) {
                    transactionRepository.updateFees(transactionId, reservation.getId(), safeParseLong(paymentInformation.getPlatformFee()), safeParseLong(paymentInformation.getFee()));
                }
            } catch (Exception e) {
                log.warn("cannot update fees", e);
            }
        });
        return maybeTransaction.orElseGet(() -> new TransactionAndPaymentInfo(reservation.getPaymentMethod(),null, new PaymentInformation(reservation.getPaidAmount(), null, null, null)));
    }

    private static long safeParseLong(String src) {
        return Optional.ofNullable(src).map(Long::parseLong).orElse(0L);
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
