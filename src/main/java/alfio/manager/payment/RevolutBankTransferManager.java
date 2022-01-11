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
import alfio.model.PaymentInformation;
import alfio.model.PurchaseContext;
import alfio.model.TicketReservationWithTransaction;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.OfflineProcessor;
import alfio.model.transaction.capabilities.PaymentInfo;
import alfio.model.transaction.provider.RevolutTransactionDescriptor;
import alfio.repository.TransactionRepository;
import alfio.util.ClockProvider;
import alfio.util.Json;
import alfio.util.MonetaryUtil;
import alfio.util.oauth2.AccessTokenResponseDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.EventUtil.JSON_DATETIME_FORMATTER;

@Component
@Order(1)
@Transactional
@Log4j2
@AllArgsConstructor
public class RevolutBankTransferManager implements PaymentProvider, OfflineProcessor, PaymentInfo {

    private static final String GENERIC_ERROR = "error";
    private final BankTransferManager bankTransferManager;
    private final ConfigurationManager configurationManager;
    private final TransactionRepository transactionRepository;
    private final HttpClient client;
    private final ClockProvider clockProvider;
    private static final Cache<String, List<String>> accountsCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofHours(1))
        .build();

    @Override
    public Set<PaymentMethod> getSupportedPaymentMethods(PaymentContext paymentContext, TransactionRequest transactionRequest) {
        return bankTransferManager.getSupportedPaymentMethods(paymentContext, transactionRequest);
    }

    @Override
    public PaymentProxy getPaymentProxy() {
        return bankTransferManager.getPaymentProxy();
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context, TransactionRequest transactionRequest) {
        return paymentMethod == PaymentMethod.BANK_TRANSFER && isActive(context);
    }

    @Override
    public boolean isActive(PaymentContext paymentContext) {
        var options = bankTransferManager.options(paymentContext);
        return bankTransferManager.bankTransferActive(paymentContext, options)
            && options.get(REVOLUT_ENABLED).getValueAsBooleanOrDefault()
            && options.get(REVOLUT_API_KEY).isPresent();
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        return bankTransferManager.doPayment(spec);
    }

    @Override
    public Map<String, ?> getModelOptions(PaymentContext context) {
        return bankTransferManager.getModelOptions(context);
    }

    @Override
    public Result<List<String>> checkPendingReservations(Collection<TicketReservationWithTransaction> reservations,
                                                                                   PaymentContext context,
                                                                                   ZonedDateTime lastCheck) {
        if(reservations.isEmpty()) {
            return Result.success(List.of());
        }
        var options = bankTransferManager.options(context);
        var host = options.get(REVOLUT_LIVE_MODE).getValueAsBooleanOrDefault() ? "https://b2b.revolut.com" : "https://sandbox-b2b.revolut.com";
        var revolutKeyOptional = options.get(REVOLUT_API_KEY).getValue();
        if(revolutKeyOptional.isEmpty()) {
            return Result.error(ErrorCode.custom("unavailable", "cannot retrieve Revolut API KEY. Please fix configuration"));
        }
        var revolutKey = revolutKeyOptional.get();

        return loadAccounts(revolutKey, host)
            .flatMap(accounts -> loadTransactions(lastCheck, revolutKey, host, accounts))
            .flatMap(revolutTransactions -> matchTransactions(reservations, revolutTransactions, context, options.get(REVOLUT_MANUAL_REVIEW).getValueAsBooleanOrDefault()));
    }

    Result<List<String>> matchTransactions(Collection<TicketReservationWithTransaction> pendingReservations,
                                           List<RevolutTransactionDescriptor> transactions,
                                           PaymentContext context,
                                           boolean manualReviewRequired) {
        List<Pair<TicketReservationWithTransaction, RevolutTransactionDescriptor>> matched = pendingReservations.stream()
            .map(reservation -> Pair.of(reservation, transactions.stream().filter(transactionMatches(reservation, context)).findFirst()))
            .filter(pair -> pair.getRight().isPresent())
            .map(pair -> Pair.of(pair.getLeft(), pair.getRight().orElseThrow()))
            .collect(Collectors.toList());

        return Result.success(matched.stream().map(pair -> {
                var reservationId = pair.getLeft().getTicketReservation().getId();
                var transaction = pair.getLeft().getTransaction();
                Optional<Transaction> latestTransaction = transactionRepository.lockLatestForUpdate(reservationId);
                if(latestTransaction.isEmpty() || latestTransaction.get().getId() != transaction.getId() || latestTransaction.get().getStatus() != transaction.getStatus()) {
                    log.trace("transaction {} has been modified while processing. Current status is: {}", transaction.getId(), latestTransaction.map(t -> t.getStatus().name()).orElse("N/A"));
                    return null;
                }
                var revolutTransaction = pair.getRight();

                transactionRepository.update(transaction.getId(),
                    revolutTransaction.getId(),
                    revolutTransaction.getRequestId(),
                    revolutTransaction.getCompletedAt(),
                    0L,
                    0L,
                    manualReviewRequired ? Transaction.Status.OFFLINE_PENDING_REVIEW : Transaction.Status.OFFLINE_MATCHING_PAYMENT_FOUND,
                    revolutTransaction.getMetadata());
                return reservationId;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList()));
    }

    private Predicate<RevolutTransactionDescriptor> transactionMatches(TicketReservationWithTransaction reservationWithTransaction, PaymentContext context) {
        var reservation = reservationWithTransaction.getTicketReservation();
        var transaction = reservationWithTransaction.getTransaction();
        String reservationId = reservation.getId().toLowerCase();
        var shortReservationId = configurationManager.getShortReservationID(context.getPurchaseContext(), reservation).toLowerCase();
        String[] terms;
        if(reservation.getHasInvoiceNumber()) {
            terms = new String[] {reservation.getInvoiceNumber().toLowerCase(), shortReservationId, reservationId};
        } else {
            terms = new String[] {shortReservationId, reservationId};
        }

        return revolutTransaction -> revolutTransaction.getTransactionBalance().compareTo(BigDecimal.ZERO) > 0
            && Arrays.stream(terms).anyMatch(s -> revolutTransaction.getReference().toLowerCase().contains(s))
            && transaction.getCurrency().equals(revolutTransaction.getLegs().get(0).getCurrency())
            && transaction.getPriceInCents() == MonetaryUtil.unitToCents(revolutTransaction.getTransactionBalance(), transaction.getCurrency());
    }

    private Result<List<RevolutTransactionDescriptor>> loadTransactions(ZonedDateTime lastCheck, String revolutKey, String revolutUrl, List<String> accounts) {
        if(accounts.isEmpty()) {
            return Result.error(ErrorCode.custom("no-account", "No active accounts found."));
        }
        try {
            var from = lastCheck != null ? lastCheck.withZoneSameInstant(clockProvider.getClock().getZone()) : ZonedDateTime.now(clockProvider.getClock()).minusDays(1);//defaults to now - 24h
            var request = HttpRequest.newBuilder(URI.create(revolutUrl + "/api/1.0/transactions?from=" + from.format(JSON_DATETIME_FORMATTER)))
                .GET()
                .header("Authorization", "Bearer "+revolutKey)
                .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if(response.statusCode() == HttpStatus.OK.value()) {
                List<RevolutTransactionDescriptor> result = Json.fromJson(response.body(), new TypeReference<>() {});
                return Result.success(
                    result.stream()
                        .filter(t -> "completed".equals(t.getState()) && t.getLegs().size() == 1 && accounts.contains(t.getLegs().get(0).getAccountId()))
                        .collect(Collectors.toList()));
            }
            return Result.error(ErrorCode.custom("no data received", "No data received from Revolut. Status code is "+response.statusCode()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Request interrupted while calling Revolut API", e);
            return Result.error(ErrorCode.custom(GENERIC_ERROR, "Cannot call Revolut API"));
        } catch (Exception e) {
            log.warn("cannot call Revolut APIs", e);
            return Result.error(ErrorCode.custom(GENERIC_ERROR, "Cannot call Revolut API"));
        }
    }

    private Result<List<String>> loadAccounts(String revolutKey, String baseUrl) {
        var key = revolutKey + "@" + baseUrl;
        try {
            return Result.success(accountsCache.get(key, k -> loadAccountsFromAPI(revolutKey, baseUrl)));
        } catch (Exception e) {
            return Result.error(ErrorCode.custom(GENERIC_ERROR, e.getMessage()));
        }
    }

    private List<String> loadAccountsFromAPI(String revolutKey, String baseUrl) {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/1.0/accounts"))
            .GET()
            .header("Authorization", "Bearer "+revolutKey)
            .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == HttpStatus.OK.value()) {
                List<Map<String, ?>> result = Json.fromJson(response.body(), new TypeReference<>() {
                });
                return result.stream().filter(m -> "active".equals(m.get("state"))).map(m -> (String) m.get("id")).collect(Collectors.toList());
            }
            throw new IllegalStateException("cannot retrieve accounts");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Request interrupted while retrieving accounts", e);
            throw new IllegalStateException(e);
        } catch (Exception e) {
            log.warn("got error while retrieving accounts", e);
            throw new IllegalStateException(e);
        }
    }


    @Override
    public Optional<PaymentInformation> getInfo(Transaction transaction, PurchaseContext purchaseContext) {
        var metadata = transaction.getMetadata();
        if(metadata != null && metadata.containsKey("counterpartyAccountId")) {
            return Optional.of(new PaymentInformation(MonetaryUtil.formatCents(transaction.getPriceInCents(), transaction.getCurrency()), null, String.valueOf(transaction.getGatewayFee()), String.valueOf(transaction.getPlatformFee())));
        }
        return Optional.empty();
    }

    @Override
    public boolean accept(Transaction transaction) {
        return false;
    }

    @Override
    public PaymentMethod getPaymentMethodForTransaction(Transaction transaction) {
        return bankTransferManager.getPaymentMethodForTransaction(transaction);
    }
}
