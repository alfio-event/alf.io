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

import alfio.manager.PaymentManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.support.FeeCalculator;
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.*;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.ExtractPaymentTokenFromTransaction;
import alfio.model.transaction.capabilities.PaymentInfo;
import alfio.model.transaction.capabilities.RefundRequest;
import alfio.model.transaction.token.PayPalToken;
import alfio.repository.TicketRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;
import alfio.util.ErrorsCode;
import alfio.util.Json;
import alfio.util.MonetaryUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.paypal.api.payments.Transaction;
import com.paypal.api.payments.*;
import com.paypal.base.rest.APIContext;
import com.paypal.base.rest.PayPalRESTException;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static alfio.model.system.ConfigurationKeys.PAYPAL_ENABLED;
import static alfio.util.MonetaryUtil.formatCents;

@Component
@Log4j2
@AllArgsConstructor
public class PayPalManager implements PaymentProvider, RefundRequest, PaymentInfo, ExtractPaymentTokenFromTransaction {

    private final ConfigurationManager configurationManager;
    private final MessageSourceManager messageSourceManager;
    private final Cache<String, String> cachedWebProfiles = Caffeine.newBuilder().expireAfterAccess(24, TimeUnit.HOURS).build();
    private final TicketReservationRepository ticketReservationRepository;
    private final TicketRepository ticketRepository;
    private final TransactionRepository transactionRepository;
    private final Json json;

    private APIContext getApiContext(EventAndOrganizationId event) {

        var paypalConf = configurationManager.getFor(Set.of(ConfigurationKeys.PAYPAL_LIVE_MODE, ConfigurationKeys.PAYPAL_CLIENT_ID, ConfigurationKeys.PAYPAL_CLIENT_SECRET),
            ConfigurationLevel.event(event));

        boolean isLive = paypalConf.get(ConfigurationKeys.PAYPAL_LIVE_MODE).getValueAsBooleanOrDefault(false);
        String clientId = paypalConf.get(ConfigurationKeys.PAYPAL_CLIENT_ID).getRequiredValue();
        String clientSecret = paypalConf.get(ConfigurationKeys.PAYPAL_CLIENT_SECRET).getRequiredValue();
        return new APIContext(clientId, clientSecret, isLive ? "live" : "sandbox");
    }

    private static String toWebProfileName(Event event, Locale locale, APIContext apiContext) {
            return "ALFIO-" + DigestUtils.md5Hex( apiContext.getClientID() + "-" + event.getId() + "-" + event.getShortName()) + "-" + locale.toString();
    }

    private Optional<WebProfile> getWebProfile(Event event, Locale locale, APIContext apiContext) {
        try {
            String webProfileName = toWebProfileName(event, locale, apiContext);
            return WebProfile.getList(apiContext).stream().filter(webProfile -> webProfileName.equals(webProfile.getName())).findFirst();
        } catch(PayPalRESTException e) {
            return Optional.empty();
        }
    }

    private Optional<String> getOrCreateWebProfile(Event event, Locale locale, APIContext apiContext) {
        String webProfileName = toWebProfileName(event, locale, apiContext);

        String id = cachedWebProfiles.get(webProfileName, missingKey -> getWebProfile(event, locale, apiContext).map(WebProfile::getId).orElseGet(() -> {
            //create profile
            WebProfile webProfile = new WebProfile(webProfileName);
            webProfile.setInputFields(new InputFields().setNoShipping(1).setAddressOverride(0).setAllowNote(false));
            try {
                return webProfile.create(apiContext).getId();
            } catch (PayPalRESTException e) {
                log.warn("error while creating web experience", e);
                //do absolutely nothing, worst case: the web experience will not be optimal
                return null;
            }
        }) );
        return Optional.ofNullable(id);
    }

    private List<Transaction> buildPaymentDetails(Event event, OrderSummary orderSummary, TicketReservation reservation, Locale locale) {
        Amount amount = new Amount()
            .setCurrency(event.getCurrency())
            .setTotal(orderSummary.getTotalPrice());


        Transaction transaction = new Transaction();
        String description = messageSourceManager.getMessageSourceForEvent(event).getMessage("reservation-email-subject", new Object[] {configurationManager.getShortReservationID(event, reservation), event.getDisplayName()}, locale);
        transaction.setDescription(description).setAmount(amount);


        List<Item> items = new ArrayList<>();
        items.add(new Item(description, "1", orderSummary.getTotalPrice(), event.getCurrency()));
        transaction.setItemList(new ItemList().setItems(items));

        List<Transaction> transactions = new ArrayList<>();
        transactions.add(transaction);
        return transactions;
    }

    private static final String URL_PLACEHOLDER = "--operation--";

    private String createCheckoutRequest(PaymentSpecification spec) throws Exception {
        APIContext apiContext = getApiContext(spec.getEvent());

        Optional<String> experienceProfileId = getOrCreateWebProfile(spec.getEvent(), spec.getLocale(), apiContext);

        TicketReservation reservation = ticketReservationRepository.findReservationById(spec.getReservationId());
        List<Transaction> transactions = buildPaymentDetails(spec.getEvent(), spec.getOrderSummary(), reservation, spec.getLocale());
        String eventName = spec.getEvent().getShortName();

        Payer payer = new Payer();
        payer.setPaymentMethod("paypal");

        Payment payment = new Payment();
        payment.setIntent("sale");
        payment.setPayer(payer);
        payment.setTransactions(transactions);
        RedirectUrls redirectUrls = new RedirectUrls();

        String baseUrl = StringUtils.removeEnd(configurationManager.getFor(ConfigurationKeys.BASE_URL, ConfigurationLevel.event(spec.getEvent())).getRequiredValue(), "/");
        String bookUrl = baseUrl+"/event/" + eventName + "/reservation/" + spec.getReservationId() + "/payment/paypal/" + URL_PLACEHOLDER;

        UriComponentsBuilder bookUrlBuilder = UriComponentsBuilder.fromUriString(bookUrl)
            .queryParam("hmac", computeHMAC(spec.getCustomerName(), spec.getEmail(), spec.getBillingAddress(), spec.getEvent()));
        String finalUrl = bookUrlBuilder.toUriString();

        redirectUrls.setCancelUrl(finalUrl.replace(URL_PLACEHOLDER, "cancel"));
        redirectUrls.setReturnUrl(finalUrl.replace(URL_PLACEHOLDER, "confirm"));
        payment.setRedirectUrls(redirectUrls);

        experienceProfileId.ifPresent(payment::setExperienceProfileId);

        Payment createdPayment = payment.create(apiContext);


        //add 15 minutes of validity in case the paypal flow is slow
        ticketReservationRepository.updateValidity(spec.getReservationId(), DateUtils.addMinutes(reservation.getValidity(), 15));

        if(!"created".equals(createdPayment.getState())) {
            throw new Exception(createdPayment.getFailureReason());
        }

        //extract url for approval
        return createdPayment.getLinks().stream().filter(l -> "approval_url".equals(l.getRel())).findFirst().map(Links::getHref).orElseThrow(IllegalStateException::new);

    }

    private static String computeHMAC(CustomerName customerName, String email, String billingAddress, Event event) {
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, event.getPrivateKey()).hmacHex(StringUtils.trimToEmpty(customerName.getFullName()) + StringUtils.trimToEmpty(email) + StringUtils.trimToEmpty(billingAddress));
    }

    private static boolean isValidHMAC(CustomerName customerName, String email, String billingAddress, String hmac, Event event) {
        String computedHmac = computeHMAC(customerName, email, billingAddress, event);
        return MessageDigest.isEqual(hmac.getBytes(StandardCharsets.UTF_8), computedHmac.getBytes(StandardCharsets.UTF_8));
    }

    public static class HandledPayPalErrorException extends RuntimeException {
        HandledPayPalErrorException(String errorMessage) {
            super(errorMessage);
        }
    }

    private static final Set<String> MAPPED_ERROR = Set.of("FAILED_TO_CHARGE_CC", "INSUFFICIENT_FUNDS", "EXPIRED_CREDIT_CARD", "INSTRUMENT_DECLINED");

    private static Optional<String> mappedException(PayPalRESTException e) {
        //https://developer.paypal.com/docs/api/#errors
        if(e.getDetails() != null && e.getDetails().getName() != null && MAPPED_ERROR.contains(e.getDetails().getName())) {
            return Optional.of("error.STEP_2_PAYPAL_"+e.getDetails().getName());
        } else {
            return Optional.empty();
        }
    }

    private Pair<String, String> commitPayment(String reservationId, PayPalToken payPalToken, EventAndOrganizationId event) throws PayPalRESTException {

        Payment payment = new Payment().setId(payPalToken.getPaymentId());
        PaymentExecution paymentExecute = new PaymentExecution();
        paymentExecute.setPayerId(payPalToken.getPayerId());
        Payment result;
        try {
            result = payment.execute(getApiContext(event), paymentExecute);
        } catch (PayPalRESTException e) {
            mappedException(e).ifPresent(message -> {
                throw new HandledPayPalErrorException(message);
            });
            throw e;
        }


        // state can only be "created", "approved" or "failed".
        // if we are at this stage, the only possible options are approved or failed, thus it's safe to re transition the reservation to a pending status: no payment has been made!
        if(!"approved".equals(result.getState())) {
            log.warn("error in state for reservationId {}, expected 'approved' state, but got '{}', failure reason is {}", reservationId, result.getState(), result.getFailureReason());
            throw new PayPalRESTException(result.getFailureReason());
        }

        // navigate the object graph (ideally taking the first Sale object) result.getTransactions().get(0).getRelatedResources().get(0).getSale().getId()
        String captureId = result.getTransactions().stream()
            .map(Transaction::getRelatedResources)
            .flatMap(List::stream)
            .map(RelatedResources::getSale)
            .filter(Objects::nonNull)
            .map(Sale::getId)
            .filter(Objects::nonNull)
            .findFirst().orElseThrow(IllegalStateException::new);

        return Pair.of(captureId, payment.getId());
    }

    private Optional<PaymentInformation> getInfo(String paymentId, String transactionId, EventAndOrganizationId event, Supplier<String> platformFeeSupplier) {
        try {
            String refund = null;

            APIContext apiContext = getApiContext(event);

            //check for backward compatibility  reason...
            if(paymentId != null) {
                //navigate in all refund objects and sum their amount
                refund = Payment.get(apiContext, paymentId).getTransactions().stream()
                    .map(Transaction::getRelatedResources)
                    .flatMap(List::stream)
                    .filter(f -> f.getRefund() != null)
                    .map(RelatedResources::getRefund)
                    .map(Refund::getAmount)
                    .map(Amount::getTotal)
                    .map(BigDecimal::new)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).toPlainString();
                //
            }
            Capture c = Capture.get(apiContext, transactionId);
            String gatewayFee = Optional.ofNullable(c.getTransactionFee())
                .map(currency -> MonetaryUtil.formatUnit(new BigDecimal(currency.getValue()), currency.getCurrency()))
                .orElse(null);
            return Optional.of(new PaymentInformation(c.getAmount().getTotal(), refund, gatewayFee, platformFeeSupplier.get()));
        } catch (PayPalRESTException ex) {
            log.warn("Paypal: error while fetching information for payment id " + transactionId, ex);
            return Optional.empty();
        }
    }

    @Override
    public Optional<PaymentInformation> getInfo(alfio.model.transaction.Transaction transaction, Event event) {
        return getInfo(transaction.getPaymentId(), transaction.getTransactionId(), event, () -> {
            if(transaction.getPlatformFee() > 0) {
                return String.valueOf(transaction.getPlatformFee());
            }
            return FeeCalculator.getCalculator(event, configurationManager, transaction.getCurrency())
                    .apply(ticketRepository.countTicketsInReservation(transaction.getReservationId()), (long) transaction.getPriceInCents())
                    .map(String::valueOf)
                    .orElse("0");
        });
    }


    @Override
    public boolean refund(alfio.model.transaction.Transaction transaction, Event event, Integer amountToRefund) {
        Optional<Integer> amount = Optional.ofNullable(amountToRefund);
        String captureId = transaction.getTransactionId();
        try {
            APIContext apiContext = getApiContext(event);
            var transactionCurrency = transaction.getCurrency();
            String amountOrFull = amount.map(a -> MonetaryUtil.formatCents(a, transactionCurrency)).orElse("full");
            log.info("Paypal: trying to do a refund for payment {} with amount: {}", captureId, amountOrFull);
            Capture capture = Capture.get(apiContext, captureId);
            com.paypal.api.payments.RefundRequest refundRequest = new com.paypal.api.payments.RefundRequest();
            amount.ifPresent(a -> refundRequest.setAmount(new Amount(capture.getAmount().getCurrency(), formatCents(a, transactionCurrency))));
            capture.refund(apiContext, refundRequest);
            log.info("Paypal: refund for payment {} executed with success for amount: {}", captureId, amountOrFull);
            return true;
        } catch(PayPalRESTException ex) {
            log.warn("Paypal: was not able to refund payment with id " + captureId, ex);
            return false;
        }
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context) {

        var paypalConf = configurationManager.getFor(Set.of(PAYPAL_ENABLED, ConfigurationKeys.PAYPAL_CLIENT_ID, ConfigurationKeys.PAYPAL_CLIENT_SECRET),
            context.getConfigurationLevel());

        return paymentMethod == PaymentMethod.PAYPAL &&
            paypalConf.get(PAYPAL_ENABLED).getValueAsBooleanOrDefault(false)
            && paypalConf.get(ConfigurationKeys.PAYPAL_CLIENT_ID).isPresent()
            && paypalConf.get(ConfigurationKeys.PAYPAL_CLIENT_SECRET).isPresent();
    }

    @Override
    public boolean accept(alfio.model.transaction.Transaction transaction) {
        return PaymentProxy.PAYPAL == transaction.getPaymentProxy();
    }

    @Override
    public PaymentResult getToken(PaymentSpecification spec) {
        try {
            PaymentToken gatewayToken = spec.getGatewayToken();
            if(gatewayToken != null && gatewayToken.getPaymentProvider() == PaymentProxy.PAYPAL) {
                return PaymentResult.initialized(gatewayToken.getToken());
            }
            return PaymentResult.redirect( createCheckoutRequest(spec) );
        } catch (Exception e) {
            return PaymentResult.failed( ErrorsCode.STEP_2_PAYMENT_REQUEST_CREATION );
        }
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        try {
            PayPalToken gatewayToken = (PayPalToken) spec.getGatewayToken();
            if(!isValidHMAC(spec.getCustomerName(), spec.getEmail(), spec.getBillingAddress(), gatewayToken.getHmac(), spec.getEvent())) {
                return PaymentResult.failed(ErrorsCode.STEP_2_INVALID_HMAC);
            }
            Pair<String, String> captureAndPaymentId = commitPayment(spec.getReservationId(), gatewayToken, spec.getEvent());
            String captureId = captureAndPaymentId.getLeft();
            String paymentId = captureAndPaymentId.getRight();
            Supplier<String> feeSupplier = () -> FeeCalculator.getCalculator(spec.getEvent(), configurationManager, spec.getCurrencyCode())
                .apply(ticketRepository.countTicketsInReservation(spec.getReservationId()), (long) spec.getPriceWithVAT())
                .map(String::valueOf)
                .orElse("0");
            Pair<Long, Long> fees = getInfo(paymentId, captureId, spec.getEvent(), feeSupplier).map(i -> {
                Long platformFee = Optional.ofNullable(i.getPlatformFee()).map(Long::parseLong).orElse(0L);
                Long gatewayFee = Optional.ofNullable(i.getFee()).map(Long::parseLong).orElse(0L);
                return Pair.of(platformFee, gatewayFee);
            }).orElseGet(() -> Pair.of(0L, 0L));
            PaymentManagerUtils.invalidateExistingTransactions(spec.getReservationId(), transactionRepository);
            transactionRepository.insert(captureId, paymentId, spec.getReservationId(),
                ZonedDateTime.now(), spec.getPriceWithVAT(), spec.getEvent().getCurrency(), "Paypal confirmation", PaymentProxy.PAYPAL.name(),
                fees.getLeft(), fees.getRight(), alfio.model.transaction.Transaction.Status.COMPLETE, Map.of());
            return PaymentResult.successful(captureId);
        } catch (Exception e) {
            log.warn("errow while processing paypal payment: " + e.getMessage(), e);
            if(e instanceof PayPalRESTException ) {
                return PaymentResult.failed(ErrorsCode.STEP_2_PAYPAL_UNEXPECTED);
            } else if(e instanceof HandledPayPalErrorException) {
                return PaymentResult.failed(e.getMessage());
            }
            throw new IllegalStateException(e);
        }
    }

    public void saveToken(String reservationId, Event event, PayPalToken token) {
        PaymentManagerUtils.invalidateExistingTransactions(reservationId, transactionRepository);
        transactionRepository.insert(reservationId, token.getPaymentId(), reservationId,
            ZonedDateTime.now(), 0, event.getCurrency(), "Paypal token", PaymentProxy.PAYPAL.name(), 0, 0,
            alfio.model.transaction.Transaction.Status.PENDING, Map.of(PaymentManager.PAYMENT_TOKEN, json.asJsonString(token)));
    }

    @Override
    public Optional<PaymentToken> extractToken(alfio.model.transaction.Transaction transaction) {
        var jsonPaymentToken = transaction.getMetadata().getOrDefault(PaymentManager.PAYMENT_TOKEN, null);
        return Optional.ofNullable(jsonPaymentToken).map(t -> json.fromJsonString(t, PayPalToken.class));
    }

    public void removeToken(String reservationId) {
        PaymentManagerUtils.invalidateExistingTransactions(reservationId, transactionRepository, PaymentProxy.PAYPAL);
    }

}
