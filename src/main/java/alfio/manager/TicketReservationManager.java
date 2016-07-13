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

import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.support.TemplateProcessor;
import alfio.manager.plugin.PluginManager;
import alfio.manager.support.*;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.Mailer;
import alfio.model.*;
import alfio.model.AdditionalServiceItem.AdditionalServiceItemStatus;
import alfio.model.PromoCodeDiscount.DiscountType;
import alfio.model.SpecialPrice.Status;
import alfio.model.Ticket.TicketStatus;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.modification.*;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.util.MonetaryUtil;
import alfio.util.TemplateManager;
import alfio.util.TemplateManager.TemplateOutput;
import alfio.util.Wrappers;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static alfio.model.TicketReservation.TicketReservationStatus.IN_PAYMENT;
import static alfio.model.TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.MonetaryUtil.*;
import static alfio.util.OptionalWrapper.optionally;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.time.DateUtils.addHours;
import static org.apache.commons.lang3.time.DateUtils.truncate;

@Component
@Transactional
@Log4j2
public class TicketReservationManager {
    
    private static final String STUCK_TICKETS_MSG = "there are stuck tickets for the event %s. Please check admin area.";
    private static final String STUCK_TICKETS_SUBJECT = "warning: stuck tickets found";
    public static final String NOT_YET_PAID_TRANSACTION_ID = "not-paid";

    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final TicketRepository ticketRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private final ConfigurationManager configurationManager;
    private final PaymentManager paymentManager;
    private final PromoCodeDiscountRepository promoCodeDiscountRepository;
    private final SpecialPriceRepository specialPriceRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationManager notificationManager;
    private final MessageSource messageSource;
    private final TemplateManager templateManager;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final WaitingQueueManager waitingQueueManager;
    private final PluginManager pluginManager;
    private final FileUploadManager fileUploadManager;
    private final TicketFieldRepository ticketFieldRepository;
    private final AdditionalServiceRepository additionalServiceRepository;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final AdditionalServiceTextRepository additionalServiceTextRepository;


    public static class NotEnoughTicketsException extends RuntimeException {

    }

    public static class MissingSpecialPriceTokenException extends RuntimeException {
    }

    public static class InvalidSpecialPriceTokenException extends RuntimeException {

    }

    @Data
    public static class TotalPrice {
        private final int priceWithVAT;
        private final int VAT;
        private final int discount;
        private final int discountAppliedCount;
    }

    @Autowired
    public TicketReservationManager(EventRepository eventRepository,
                                    OrganizationRepository organizationRepository,
                                    TicketRepository ticketRepository,
                                    TicketReservationRepository ticketReservationRepository,
                                    TicketCategoryRepository ticketCategoryRepository,
                                    TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository,
                                    ConfigurationManager configurationManager,
                                    PaymentManager paymentManager,
                                    PromoCodeDiscountRepository promoCodeDiscountRepository,
                                    SpecialPriceRepository specialPriceRepository,
                                    TransactionRepository transactionRepository,
                                    NotificationManager notificationManager,
                                    MessageSource messageSource,
                                    TemplateManager templateManager,
                                    PlatformTransactionManager transactionManager,
                                    WaitingQueueManager waitingQueueManager,
                                    PluginManager pluginManager,
                                    FileUploadManager fileUploadManager,
                                    TicketFieldRepository ticketFieldRepository,
                                    AdditionalServiceRepository additionalServiceRepository,
                                    AdditionalServiceItemRepository additionalServiceItemRepository,
                                    AdditionalServiceTextRepository additionalServiceTextRepository) {
        this.eventRepository = eventRepository;
        this.organizationRepository = organizationRepository;
        this.ticketRepository = ticketRepository;
        this.ticketReservationRepository = ticketReservationRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.ticketCategoryDescriptionRepository = ticketCategoryDescriptionRepository;
        this.configurationManager = configurationManager;
        this.paymentManager = paymentManager;
        this.promoCodeDiscountRepository = promoCodeDiscountRepository;
        this.specialPriceRepository = specialPriceRepository;
        this.transactionRepository = transactionRepository;
        this.notificationManager = notificationManager;
        this.messageSource = messageSource;
        this.templateManager = templateManager;
        this.waitingQueueManager = waitingQueueManager;
        this.pluginManager = pluginManager;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager, new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        this.fileUploadManager = fileUploadManager;
        this.ticketFieldRepository = ticketFieldRepository;
        this.additionalServiceRepository = additionalServiceRepository;
        this.additionalServiceItemRepository = additionalServiceItemRepository;
        this.additionalServiceTextRepository = additionalServiceTextRepository;
    }
    
    /**
     * Create a ticket reservation. It will create a reservation _only_ if it can find enough tickets. Note that it will not do date/validity validation. This must be ensured by the
     * caller.
     * 
     * @param eventId
     * @param list
     * @param reservationExpiration
     * @param forWaitingQueue
     * @return
     */
    public String createTicketReservation(int eventId,
                                          List<TicketReservationWithOptionalCodeModification> list,
                                          List<ASReservationWithOptionalCodeModification> additionalServices,
                                          Date reservationExpiration,
                                          Optional<String> specialPriceSessionId,
                                          Optional<String> promotionCodeDiscount,
                                          Locale locale,
                                          boolean forWaitingQueue) throws NotEnoughTicketsException, MissingSpecialPriceTokenException, InvalidSpecialPriceTokenException {
        String reservationId = UUID.randomUUID().toString();
        
        Optional<PromoCodeDiscount> discount = promotionCodeDiscount.flatMap((promoCodeDiscount) -> optionally(() -> promoCodeDiscountRepository.findPromoCodeInEvent(eventId, promoCodeDiscount)));
        
        ticketReservationRepository.createNewReservation(reservationId, reservationExpiration, discount.map(PromoCodeDiscount::getId).orElse(null), locale.getLanguage());
        list.forEach(t -> reserveTicketsForCategory(eventId, specialPriceSessionId, reservationId, t, locale, forWaitingQueue));
        additionalServices.forEach(as -> reserveAdditionalServicesForReservation(eventId, reservationId, as, locale));
        return reservationId;
    }

    void reserveTicketsForCategory(int eventId, Optional<String> specialPriceSessionId, String transactionId, TicketReservationWithOptionalCodeModification ticketReservation, Locale locale, boolean forWaitingQueue) {
        //first check if there is another pending special price token bound to the current sessionId
        Optional<SpecialPrice> specialPrice = fixToken(ticketReservation.getSpecialPrice(), ticketReservation.getTicketCategoryId(), eventId, specialPriceSessionId, ticketReservation);

        List<Integer> reservedForUpdate = reserveTickets(eventId, ticketReservation, forWaitingQueue ? asList(TicketStatus.RELEASED, TicketStatus.PRE_RESERVED) : singletonList(TicketStatus.FREE));
        int requested = ticketReservation.getAmount();
        if (reservedForUpdate.size() != requested) {
            throw new NotEnoughTicketsException();
        }

        if (specialPrice.isPresent()) {
            if(reservedForUpdate.size() != 1) {
                throw new NotEnoughTicketsException();
            }
            SpecialPrice sp = specialPrice.get();
            ticketRepository.reserveTicket(transactionId, reservedForUpdate.stream().findFirst().orElseThrow(IllegalStateException::new),sp.getId(), locale.getLanguage());
            specialPriceRepository.updateStatus(sp.getId(), Status.PENDING.toString(), sp.getSessionIdentifier());
        } else {
            ticketRepository.reserveTickets(transactionId, reservedForUpdate, ticketReservation.getTicketCategoryId(), locale.getLanguage());
        }
        TicketCategory category = ticketCategoryRepository.getById(ticketReservation.getTicketCategoryId(), eventId);
        ticketRepository.updateTicketPrice(reservedForUpdate, category.getId(), eventId, category.getPriceInCents());
    }

    private void reserveAdditionalServicesForReservation(int eventId, String transactionId, ASReservationWithOptionalCodeModification additionalServiceReservation, Locale locale) {
        //FIXME we don't need to apply discount codes to a donation, therefore this feature is not yet implemented.
        Optional.ofNullable(additionalServiceReservation.getAdditionalServiceId())
            .flatMap(id -> optionally(() -> additionalServiceRepository.getById(id, eventId)))
            .map(as -> Pair.of(eventRepository.findById(eventId), as))
            .ifPresent(pair -> {
                Event e = pair.getKey();
                AdditionalService as = pair.getValue();
                AdditionalService.VatType vatType = as.getVatType();
                boolean vatIncluded = vatType == AdditionalService.VatType.NONE || vatType == AdditionalService.VatType.INHERITED && e.isVatIncluded();
                IntStream.range(0, additionalServiceReservation.getQuantity())
                    .forEach(i -> {
                        BigDecimal price = as.isFixPrice() ? centsToUnit(as.getPriceInCents()) : additionalServiceReservation.getAmount();
                        int paidPrice = vatIncluded ? removeVAT(unitToCents(price), e.getVat()) : unitToCents(additionalServiceReservation.getAmount());
                        additionalServiceItemRepository.insert(UUID.randomUUID().toString(), ZonedDateTime.now(Clock.systemUTC()), transactionId, as.getId(), as.getPriceInCents(), paidPrice, AdditionalServiceItemStatus.PENDING, eventId);
                    });
            });

    }

    List<Integer> reserveTickets(int eventId, TicketReservationWithOptionalCodeModification ticketReservation, List<TicketStatus> requiredStatuses) {
        TicketCategory category = ticketCategoryRepository.getById(ticketReservation.getTicketCategoryId(), eventId);
        List<String> statusesAsString = requiredStatuses.stream().map(TicketStatus::name).collect(toList());
        if(category.isBounded()) {
            return ticketRepository.selectTicketInCategoryForUpdate(eventId, ticketReservation.getTicketCategoryId(), ticketReservation.getAmount(), statusesAsString);
        }
        return ticketRepository.selectNotAllocatedTicketsForUpdate(eventId, ticketReservation.getAmount(), statusesAsString);
    }

    Optional<SpecialPrice> fixToken(Optional<SpecialPrice> token, int ticketCategoryId, int eventId, Optional<String> specialPriceSessionId, TicketReservationWithOptionalCodeModification ticketReservation) {

        TicketCategory ticketCategory = ticketCategoryRepository.getById(ticketCategoryId, eventId);
        if(!ticketCategory.isAccessRestricted()) {
            return Optional.empty();
        }

        Optional<SpecialPrice> specialPrice = renewSpecialPrice(token, specialPriceSessionId);

        if(token.isPresent() && !specialPrice.isPresent()) {
            //there is a special price in the request but this isn't valid anymore
            throw new InvalidSpecialPriceTokenException();
        }

        boolean canAccessRestrictedCategory = specialPrice.isPresent()
                && specialPrice.get().getStatus() == SpecialPrice.Status.FREE
                && specialPrice.get().getTicketCategoryId() == ticketCategoryId;


        if (canAccessRestrictedCategory && ticketReservation.getAmount() > 1) {
            throw new NotEnoughTicketsException();
        }

        if (!canAccessRestrictedCategory && ticketCategory.isAccessRestricted()) {
            throw new MissingSpecialPriceTokenException();
        }

        return specialPrice;
    }

    public PaymentResult confirm(String gatewayToken, String payerId, Event event, String reservationId,
                                 String email, String fullName, Locale userLanguage, String billingAddress,
                                 TotalPrice reservationCost, Optional<String> specialPriceSessionId, Optional<PaymentProxy> method, boolean directTicketAssignment) {
        PaymentProxy paymentProxy = evaluatePaymentProxy(method, reservationCost);
        if(!initPaymentProcess(reservationCost, paymentProxy, reservationId, email, fullName, userLanguage, billingAddress)) {
            return PaymentResult.unsuccessful("error.STEP2_UNABLE_TO_TRANSITION");
        }
        try {
            PaymentResult paymentResult;
            ticketReservationRepository.lockReservationForUpdate(reservationId);
            if(reservationCost.getPriceWithVAT() > 0) {
                switch(paymentProxy) {
                    case STRIPE:
                        paymentResult = paymentManager.processPayment(reservationId, gatewayToken, reservationCost.getPriceWithVAT(), event, email, fullName, billingAddress);
                        if(!paymentResult.isSuccessful()) {
                            reTransitionToPending(reservationId);
                            return paymentResult;
                        }
                        break;
                    case PAYPAL:
                        paymentResult = paymentManager.processPaypalPayment(reservationId, gatewayToken, payerId, reservationCost.getPriceWithVAT(), event);
                        if(!paymentResult.isSuccessful()) {
                            reTransitionToPending(reservationId);
                            return paymentResult;
                        }
                        break;
                    case OFFLINE:
                        transitionToOfflinePayment(event, reservationId, email, fullName, billingAddress);
                        paymentResult = PaymentResult.successful(NOT_YET_PAID_TRANSACTION_ID);
                        break;
                    case ON_SITE:
                        paymentResult = PaymentResult.successful(NOT_YET_PAID_TRANSACTION_ID);
                        break;
                    default:
                        throw new IllegalArgumentException("Payment proxy "+paymentProxy+ " not recognized");
                }
            } else {
                paymentResult = PaymentResult.successful(NOT_YET_PAID_TRANSACTION_ID);
            }
            completeReservation(event.getId(), reservationId, email, fullName, userLanguage, billingAddress, specialPriceSessionId, paymentProxy, directTicketAssignment);
            return paymentResult;
        } catch(Exception ex) {
            //it is guaranteed that in this case we're dealing with "local" error (e.g. database failure),
            //thus it is safer to not rollback the reservation status
            log.error("unexpected error during payment confirmation", ex);
            return PaymentResult.unsuccessful("error.STEP2_STRIPE_unexpected");
        }

    }

    private PaymentProxy evaluatePaymentProxy(Optional<PaymentProxy> method, TotalPrice reservationCost) {
        if(method.isPresent()) {
            return method.get();
        }
        if(reservationCost.getPriceWithVAT() == 0) {
            return PaymentProxy.NONE;
        }
        return PaymentProxy.STRIPE;
    }

    private boolean initPaymentProcess(TotalPrice reservationCost, PaymentProxy paymentProxy, String reservationId, String email, String fullName, Locale userLanguage, String billingAddress) {
        if(reservationCost.getPriceWithVAT() > 0 && paymentProxy == PaymentProxy.STRIPE) {
            try {
                transitionToInPayment(reservationId, email, fullName, userLanguage, billingAddress);
            } catch (Exception e) {
                //unable to do the transition. Exiting.
                log.debug(String.format("unable to flag the reservation %s as IN_PAYMENT", reservationId), e);
                return false;
            }
        }
        return true;
    }

    public void confirmOfflinePayment(Event event, String reservationId) {
        TicketReservation ticketReservation = findById(reservationId).orElseThrow(IllegalArgumentException::new);
        ticketReservationRepository.lockReservationForUpdate(reservationId);
        Validate.isTrue(ticketReservation.getPaymentMethod() == PaymentProxy.OFFLINE, "invalid payment method");
        Validate.isTrue(ticketReservation.getStatus() == TicketReservationStatus.OFFLINE_PAYMENT, "invalid status");
        ticketReservationRepository.confirmOfflinePayment(reservationId, TicketReservationStatus.COMPLETE.name(), ZonedDateTime.now());
        acquireItems(TicketStatus.ACQUIRED, AdditionalServiceItemStatus.ACQUIRED, PaymentProxy.OFFLINE, reservationId, ticketReservation.getEmail(), ticketReservation.getFullName(), ticketReservation.getUserLanguage(), ticketReservation.getBillingAddress());

        Locale language = findReservationLanguage(reservationId);

        sendConfirmationEmail(event, findById(reservationId).orElseThrow(IllegalArgumentException::new), language);

        pluginManager.handleReservationConfirmation(ticketReservationRepository.findReservationById(reservationId), event.getId());
    }

    public void sendConfirmationEmail(Event event, TicketReservation ticketReservation, Locale language) {
        String reservationId = ticketReservation.getId();

        OrderSummary summary = orderSummaryForReservationId(reservationId, event, language);

        Map<String, Object> reservationEmailModel = prepareModelForReservationEmail(event, ticketReservation);
        ZonedDateTime confirmationTimestamp = Optional.ofNullable(ticketReservation.getConfirmationTimestamp()).orElseGet(ZonedDateTime::now);
        reservationEmailModel.put("confirmationDate", confirmationTimestamp.withZoneSameInstant(event.getZoneId()));
        List<Mailer.Attachment> attachments = new ArrayList<>(1);
        if(!summary.getNotYetPaid()) {
            Optional<byte[]> receipt = TemplateProcessor.buildReceiptPdf(event, fileUploadManager, language, templateManager, reservationEmailModel);
            if(!receipt.isPresent()) {
                log.warn("was not able to generate the bill for reservation id " + reservationId + " for locale " + language);
            }
            receipt.ifPresent(data -> {
                attachments.add(new Mailer.Attachment("receipt.pdf", data, "application/pdf"));
            });

        }

        notificationManager.sendSimpleEmail(event, ticketReservation.getEmail(), messageSource.getMessage("reservation-email-subject",
                new Object[]{getShortReservationID(event, reservationId), event.getDisplayName()}, language),
            () -> templateManager.renderClassPathResource("/alfio/templates/confirmation-email-txt.ms", reservationEmailModel, language, TemplateOutput.TEXT), attachments);
    }

    private Locale findReservationLanguage(String reservationId) {
        return Optional.ofNullable(ticketReservationRepository.findReservationById(reservationId).getUserLanguage()).map(Locale::forLanguageTag).orElse(Locale.ENGLISH);
    }

    public void deleteOfflinePayment(Event event, String reservationId, boolean expired) {
        TicketReservation reservation = findById(reservationId).orElseThrow(IllegalArgumentException::new);
        Validate.isTrue(reservation.getStatus() == OFFLINE_PAYMENT, "Invalid reservation status");
        Map<String, Object> emailModel = prepareModelForReservationEmail(event, reservation);
        Locale reservationLanguage = findReservationLanguage(reservationId);
        String subject = messageSource.getMessage("reservation-email-expired-subject", new Object[]{getShortReservationID(event, reservationId), event.getDisplayName()}, reservationLanguage);
        cancelReservation(reservationId, expired);
        notificationManager.sendSimpleEmail(event, reservation.getEmail(), subject, () -> {
            return templateManager.renderClassPathResource("/alfio/templates/offline-reservation-expired-email-txt.ms", emailModel, reservationLanguage, TemplateOutput.TEXT);
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> prepareModelForReservationEmail(Event event, TicketReservation reservation) {
        Map<String, Object> model = new HashMap<>();
        model.put("organization", organizationRepository.getById(event.getOrganizationId()));
        model.put("event", event);
        model.put("ticketReservation", reservation);

        Optional<String> vat = getVAT(event);

        model.put("hasVat", vat.isPresent());
        model.put("vatNr", vat.orElse(""));

        OrderSummary orderSummary = orderSummaryForReservationId(reservation.getId(), event, Locale.forLanguageTag(reservation.getUserLanguage()));
        model.put("tickets", findTicketsInReservation(reservation.getId()));
        model.put("orderSummary", orderSummary);
        model.put("reservationUrl", reservationUrl(reservation.getId()));
        return model;
    }

    private void transitionToInPayment(String reservationId, String email, String fullName, Locale userLanguage, String billingAddress) {
        requiresNewTransactionTemplate.execute(status -> {
            int updatedReservation = ticketReservationRepository.updateTicketReservation(reservationId, IN_PAYMENT.toString(), email, fullName, userLanguage.getLanguage(), billingAddress, null, PaymentProxy.STRIPE.toString());
            Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got " + updatedReservation);
            return null;
        });
    }

    private void transitionToOfflinePayment(Event event, String reservationId, String email, String fullName, String billingAddress) {
        ZonedDateTime deadline = getOfflinePaymentDeadline(event, configurationManager);
        int updatedReservation = ticketReservationRepository.postponePayment(reservationId, Date.from(deadline.toInstant()), email, fullName, billingAddress);
        Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got " + updatedReservation);
    }

    public static ZonedDateTime getOfflinePaymentDeadline(Event event, ConfigurationManager configurationManager) {
        ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
        int waitingPeriod = getOfflinePaymentWaitingPeriod(event, configurationManager);
        if(waitingPeriod == 0) {
            log.warn("accepting offline payments the same day is a very bad practice and should be avoided. Please set cash payment as payment method next time");
            //if today is the event start date, then we add a couple of hours.
            //TODO Maybe should we avoid this wrong behavior upfront, in the admin area?
            return now.plusHours(2);
        }
        return now.plusDays(waitingPeriod).truncatedTo(ChronoUnit.HALF_DAYS);
    }

    public static int getOfflinePaymentWaitingPeriod(Event event, ConfigurationManager configurationManager) {
        ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
        ZonedDateTime eventBegin = event.getBegin();
        int daysToBegin = (int) ChronoUnit.DAYS.between(now.toLocalDate(), eventBegin.toLocalDate());
        Validate.isTrue(daysToBegin >= 0, "Cannot confirm an offline reservation after event start");
        int waitingPeriod = configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), OFFLINE_PAYMENT_DAYS), 5);
        return Math.min(daysToBegin, waitingPeriod);
    }

    private void reTransitionToPending(String reservationId) {
        int updatedReservation = ticketReservationRepository.updateTicketStatus(reservationId, TicketReservationStatus.PENDING.toString());
        Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got "+updatedReservation);
    }
    
    //check internal consistency between the 3 values
    public Optional<Triple<Event, TicketReservation, Ticket>> from(String eventName, String reservationId, String ticketIdentifier) {
        return optionally(() -> Triple.of(eventRepository.findByShortName(eventName), 
                ticketReservationRepository.findReservationById(reservationId), 
                ticketRepository.findByUUID(ticketIdentifier))).flatMap((x) -> {
                    
                    Ticket t = x.getRight();
                    Event e = x.getLeft();
                    TicketReservation tr = x.getMiddle();
                    
                    if(tr.getId().equals(t.getTicketsReservationId()) && e.getId() == t.getEventId()) {
                        return Optional.of(x);
                    } else {
                        return Optional.empty();
                    }
                    
                });
    }

    /**
     * Set the tickets attached to the reservation to the ACQUIRED state and the ticket reservation to the COMPLETE state. Additionally it will save email/fullName/billingaddress/userLanguage.
     * @param reservationId
     * @param email
     * @param fullName
     * @param billingAddress
     * @param specialPriceSessionId
     */
    private void completeReservation(int eventId, String reservationId, String email, String fullName, Locale userLanguage, String billingAddress, Optional<String> specialPriceSessionId, PaymentProxy paymentProxy, boolean directAssignment) {
        if(paymentProxy != PaymentProxy.OFFLINE) {
            TicketStatus ticketStatus = paymentProxy.isDeskPaymentRequired() ? TicketStatus.TO_BE_PAID : TicketStatus.ACQUIRED;
            AdditionalServiceItemStatus asStatus = paymentProxy.isDeskPaymentRequired() ? AdditionalServiceItemStatus.TO_BE_PAID : AdditionalServiceItemStatus.ACQUIRED;
            acquireItems(ticketStatus, asStatus, paymentProxy, reservationId, email, fullName, userLanguage.getLanguage(), billingAddress);
            pluginManager.handleReservationConfirmation(ticketReservationRepository.findReservationById(reservationId), eventId);
        }
        //cleanup unused special price codes...
        specialPriceSessionId.ifPresent(specialPriceRepository::unbindFromSession);
        ticketReservationRepository.updateDirectAssignmentFlag(reservationId, directAssignment);
    }

    private void acquireItems(TicketStatus ticketStatus, AdditionalServiceItemStatus asStatus, PaymentProxy paymentProxy, String reservationId, String email, String fullName, String userLanguage, String billingAddress) {
        int updatedTickets = ticketRepository.updateTicketsStatusWithReservationId(reservationId, ticketStatus.toString());
        int updatedAS = additionalServiceItemRepository.updateItemsStatusWithReservationUUID(reservationId, asStatus);
        Validate.isTrue(updatedTickets + updatedAS > 0, "no items have been updated");
        specialPriceRepository.updateStatusForReservation(singletonList(reservationId), Status.TAKEN.toString());
        ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));
        int updatedReservation = ticketReservationRepository.updateTicketReservation(reservationId, TicketReservationStatus.COMPLETE.toString(), email, fullName, userLanguage, billingAddress, timestamp, paymentProxy.toString());
        Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got " + updatedReservation);
        waitingQueueManager.fireReservationConfirmed(reservationId);
    }

    void cleanupExpiredReservations(Date expirationDate) {
        List<String> expiredReservationIds = ticketReservationRepository.findExpiredReservation(expirationDate);
        if(expiredReservationIds.isEmpty()) {
            return;
        }
        
        specialPriceRepository.updateStatusForReservation(expiredReservationIds, Status.FREE.toString());
        ticketRepository.resetCategoryIdForUnboundedCategories(expiredReservationIds);
        ticketRepository.freeFromReservation(expiredReservationIds);
        waitingQueueManager.cleanExpiredReservations(expiredReservationIds);
        ticketReservationRepository.remove(expiredReservationIds);
    }

    void cleanupExpiredOfflineReservations(Date expirationDate) {
        ticketReservationRepository.findExpiredOfflineReservations(expirationDate).forEach(this::cleanupOfflinePayment);
    }

    private void cleanupOfflinePayment(String reservationId) {
        try {
            requiresNewTransactionTemplate.execute((tc) -> {
                deleteOfflinePayment(eventRepository.findByReservationId(reservationId), reservationId, true);
                return null;
            });
        } catch (Exception e) {
            log.error("error during reservation cleanup (id "+reservationId+")", e);
        }
    }

    /**
     * Finds all the reservations that are "stuck" in payment status.
     * This could happen when there is an internal error after a successful credit card charge.
     *
     * @param expirationDate expiration date
     */
    public void markExpiredInPaymentReservationAsStuck(Date expirationDate) {
        final List<String> stuckReservations = ticketReservationRepository.findStuckReservations(expirationDate);
        stuckReservations.forEach(reservationId -> ticketReservationRepository.updateTicketStatus(reservationId, TicketReservationStatus.STUCK.name()));
        stuckReservations.stream()
                .map(id -> ticketRepository.findTicketsInReservation(id).stream().findFirst())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .mapToInt(Ticket::getEventId)
                .distinct()
                .mapToObj(eventRepository::findById)
                .map(e -> Pair.of(e, organizationRepository.getById(e.getOrganizationId())))
                .forEach(pair -> notificationManager.sendSimpleEmail(pair.getLeft(), pair.getRight().getEmail(),
                                STUCK_TICKETS_SUBJECT,
                                () -> String.format(STUCK_TICKETS_MSG, pair.getLeft().getShortName()))
                );
    }

    public List<TicketWithStatistic> loadModifiedTickets(int eventId, int categoryId) {
        Event event = eventRepository.findById(eventId);
        return ticketRepository.findAllModifiedTickets(eventId, categoryId).stream()
                .map(t -> new TicketWithStatistic(t, ticketReservationRepository.findReservationById(t.getTicketsReservationId()),
                        event.getZoneId(), optionally(() -> transactionRepository.loadByReservationId(t.getTicketsReservationId()))))
                .sorted()
                .collect(Collectors.toList());
    }

    private int totalFrom(List<Ticket> tickets, Event event) {
        return tickets.stream().mapToInt(Ticket::getPaidPriceInCents).sum();
    }

    /**
     * Get the total cost with VAT if it's not included in the ticket price.
     * 
     * @param reservationId
     * @return
     */
    public TotalPrice totalReservationCostWithVAT(String reservationId) {
        TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
        
        Optional<PromoCodeDiscount> promoCodeDiscount = Optional.ofNullable(reservation.getPromoCodeDiscountId()).map(promoCodeDiscountRepository::findById);
        
        Event event = eventRepository.findByReservationId(reservationId);
        List<Ticket> tickets = ticketRepository.findTicketsInReservation(reservationId);
        int net = totalFrom(tickets, event);
        int vat = totalVat(tickets, event.getVat());
        final int amountToBeDiscounted;
        final int discountAppliedCount;
        
        
        //TODO cleanup/refactor
        //take in account the discount code
        if (promoCodeDiscount.isPresent()) {
            PromoCodeDiscount discount = promoCodeDiscount.get();
            
            if(discount.getDiscountType() == DiscountType.FIXED_AMOUNT) {
                //we apply the fixed discount for each paid ticket
                discountAppliedCount = ((int) tickets.stream().filter(t -> t.getPaidPriceInCents() > 0).count());
                amountToBeDiscounted =  discountAppliedCount * discount.getDiscountAmount();
            } else {
                amountToBeDiscounted = MonetaryUtil.calcPercentage(event.isVatIncluded() ? net + vat : net, new BigDecimal(discount.getDiscountAmount()));
                discountAppliedCount = 1;
            }
            
            // recalc the net and vat
            if(event.isVatIncluded()) {
                int finalPrice = Math.max(net + vat - amountToBeDiscounted, 0);
                net = MonetaryUtil.removeVAT(finalPrice, event.getVat());
                vat = finalPrice - net;
            } else {
                net = Math.max(net - amountToBeDiscounted, 0);
                vat = MonetaryUtil.calcVat(net, event.getVat());
            }
        } else {
            amountToBeDiscounted = 0;
            discountAppliedCount = 0;
        }

        //FIXME discount is not applied to donations, as it wouldn't make sense. Must be implemented for #111
        Price additionalServicesPrice = collectAdditionalServiceItems(reservationId, event)
            .map(calculateAdditionalServicePrice(event))
            .reduce(new Price(0,0), Price::sum);
        Price finalPrice = additionalServicesPrice.sum(new Price(net, vat));
        return new TotalPrice(finalPrice.getTotal(), finalPrice.vat, -amountToBeDiscounted, discountAppliedCount);
    }

    private Function<Pair<AdditionalService, List<AdditionalServiceItem>>, Price> calculateAdditionalServicePrice(Event event) {
        return p -> {
            AdditionalService as = p.getLeft();
            AdditionalService.VatType vatType = as.getVatType();
            BigDecimal vat = vatType == AdditionalService.VatType.INHERITED ? event.getVat() : BigDecimal.ZERO;
            List<AdditionalServiceItem> items = p.getRight();
            return new Price(items.stream().mapToInt(AdditionalServiceItem::getPaidPriceInCents).sum(), items.stream().mapToInt(i -> MonetaryUtil.calcVat(i.getPaidPriceInCents(), vat)).sum());
        };
    }
    

    private int totalVat(List<Ticket> tickets, BigDecimal vat) {
        return tickets.stream().mapToInt(Ticket::getPaidPriceInCents).map(p -> MonetaryUtil.calcVat(p, vat)).sum();
    }

    public OrderSummary orderSummaryForReservationId(String reservationId, Event event, Locale locale) {
        TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
        TotalPrice reservationCost = totalReservationCostWithVAT(reservationId);
        
        List<SummaryRow> summary = extractSummary(reservationId, event, locale);
        
        //
        boolean free = reservationCost.getPriceWithVAT() == 0;
        
        Optional<PromoCodeDiscount> promoCodeDiscount = Optional.ofNullable(reservation.getPromoCodeDiscountId()).map(promoCodeDiscountRepository::findById);
        
        //add the discount summary row only if it has an effect
        if(reservationCost.getDiscount() != 0) {
            promoCodeDiscount.ifPresent((promo) -> {
                String formattedSingleAmount = "-" + (promo.getDiscountType() == DiscountType.FIXED_AMOUNT ? formatCents(promo.getDiscountAmount()) : (promo.getDiscountAmount()+"%"));
                summary.add(new SummaryRow(promo.getPromoCode(),
                        formattedSingleAmount,
                        reservationCost.discountAppliedCount, 
                        formatCents(reservationCost.discount), reservationCost.discount, SummaryRow.SummaryType.PROMOTION_CODE));
            });
        }
                
        
        return new OrderSummary(reservationCost,
                summary, free,
                formatCents(reservationCost.getPriceWithVAT()), formatCents(reservationCost.getVAT()),
                reservation.getStatus() == TicketReservationStatus.OFFLINE_PAYMENT,
                reservation.getPaymentMethod() == PaymentProxy.ON_SITE);
    }
    
    private List<SummaryRow> extractSummary(String reservationId, Event event, Locale locale) {
        List<SummaryRow> summary = new ArrayList<>();
        List<Ticket> tickets = ticketRepository.findTicketsInReservation(reservationId);
        tickets.stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).forEach((categoryId, ticketsByCategory) -> {
            int paidPriceInCents = ticketsByCategory.get(0).getPaidPriceInCents();
            if(event.isVatIncluded()) {
                paidPriceInCents = addVAT(paidPriceInCents, event.getVat());
            }
            String categoryName = ticketCategoryRepository.getById(categoryId, event.getId()).getName();
            final int subTotal = paidPriceInCents * ticketsByCategory.size();
            summary.add(new SummaryRow(categoryName, formatCents(paidPriceInCents), ticketsByCategory.size(), formatCents(subTotal), subTotal, SummaryRow.SummaryType.TICKET));
        });
        summary.addAll(collectAdditionalServiceItems(reservationId, event)
            .flatMap(entry -> {
                AdditionalServiceText title = additionalServiceTextRepository.findByLocaleAndType(entry.getKey().getId(), locale.getLanguage(), AdditionalServiceText.AdditionalServiceDescriptionType.TITLE);
                return entry.getValue().stream().map(item -> new SummaryRow(title.getValue(), MonetaryUtil.formatCents(item.getPaidPriceInCents()), 1, MonetaryUtil.formatCents(item.getPaidPriceInCents()), item.getPaidPriceInCents(), SummaryRow.SummaryType.ADDITIONAL_SERVICE));
            }).collect(Collectors.toList()));
        return summary;
    }

    private Stream<Pair<AdditionalService, List<AdditionalServiceItem>>> collectAdditionalServiceItems(String reservationId, Event event) {
        return additionalServiceItemRepository.findByReservationUuid(reservationId)
            .stream()
            .collect(Collectors.groupingBy(AdditionalServiceItem::getAdditionalServiceId))
            .entrySet()
            .stream()
            .map(entry -> Pair.of(additionalServiceRepository.getById(entry.getKey(), event.getId()), entry.getValue()));
    }

    public String reservationUrl(String reservationId) {
        return reservationUrl(reservationId, eventRepository.findByReservationId(reservationId));
    }

    public String reservationUrl(String reservationId, Event event) {
        TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
        return StringUtils.removeEnd(configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.BASE_URL)), "/")
                + "/event/" + event.getShortName() + "/reservation/" + reservationId + "?lang="+reservation.getUserLanguage();
    }

    public String ticketUrl(String reservationId, Event event, String ticketId) {
        Ticket ticket = ticketRepository.findByUUID(ticketId);
        return StringUtils.removeEnd(configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.BASE_URL)), "/")
                + "/event/" + event.getShortName() + "/reservation/" + reservationId+ "/" + ticketId + "?lang=" + ticket.getUserLanguage();
    }

    public String ticketUpdateUrl(String reservationId, Event event, String ticketId) {
        Ticket ticket = ticketRepository.findByUUID(ticketId);
        return StringUtils.removeEnd(configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.BASE_URL)), "/")
            + "/event/" + event.getShortName() + "/reservation/" + reservationId+ "/ticket/" + ticketId + "/update?lang="+ticket.getUserLanguage();
    }


    public int maxAmountOfTickets(Event event) {
        return configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), 5);
    }

    public int maxAmountOfTicketsForCategory(int organizationId, int eventId, int ticketCategoryId) {
        return configurationManager.getIntConfigValue(Configuration.from(organizationId, eventId, ticketCategoryId, ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), 5);
    }
    
    public Optional<TicketReservation> findById(String reservationId) {
        return optionally(() -> ticketReservationRepository.findReservationById(reservationId));
    }

    private Optional<TicketReservation> findByIdForNotification(String reservationId, ZoneId eventZoneId, int quietPeriod) {
        return findById(reservationId).filter(notificationNotSent(eventZoneId, quietPeriod));
    }

    private static Predicate<TicketReservation> notificationNotSent(ZoneId eventZoneId, int quietPeriod) {
        return r -> r.latestNotificationTimestamp(eventZoneId)
                .map(t -> t.truncatedTo(ChronoUnit.DAYS).plusDays(quietPeriod).isBefore(ZonedDateTime.now(eventZoneId).truncatedTo(ChronoUnit.DAYS)))
                .orElse(true);
    }

    public void cancelPendingReservation(String reservationId, boolean expired) {
        Validate.isTrue(ticketReservationRepository.findReservationById(reservationId).getStatus() == TicketReservationStatus.PENDING, "status is not PENDING");
        cancelReservation(reservationId, expired);
    }

    private void cancelReservation(String reservationId, boolean expired) {
        List<String> reservationIdsToRemove = singletonList(reservationId);
        specialPriceRepository.updateStatusForReservation(reservationIdsToRemove, Status.FREE.toString());
        int updatedAS = additionalServiceItemRepository.updateItemsStatusWithReservationUUID(reservationId, expired ? AdditionalServiceItemStatus.EXPIRED : AdditionalServiceItemStatus.CANCELLED);
        int updatedTickets = ticketRepository.freeFromReservation(reservationIdsToRemove);
        Validate.isTrue(updatedTickets + updatedAS > 0, "no items have been updated");
        waitingQueueManager.fireReservationExpired(reservationId);
        deleteReservations(reservationIdsToRemove);
    }

    private void deleteReservations(List<String> reservationIdsToRemove) {
        int removedReservation = ticketReservationRepository.remove(reservationIdsToRemove);
        Validate.isTrue(removedReservation == 1, "expected exactly one removed reservation, got " + removedReservation);
    }

    public SpecialPrice getSpecialPriceByCode(String code) {
        return specialPriceRepository.getByCode(code);
    }

    public Optional<SpecialPrice> renewSpecialPrice(Optional<SpecialPrice> specialPrice, Optional<String> specialPriceSessionId) {
        Validate.isTrue(specialPrice.isPresent(), "special price is not present");

        SpecialPrice price = specialPrice.get();

        if(!specialPriceSessionId.isPresent()) {
            log.warn("cannot renew special price {}: session identifier not found or not matching", price.getCode());
            return Optional.empty();
        }

        if(price.getStatus() == Status.PENDING && !StringUtils.equals(price.getSessionIdentifier(), specialPriceSessionId.get())) {
            log.warn("cannot renew special price {}: session identifier not found or not matching", price.getCode());
            return Optional.empty();
        }

        if(price.getStatus() == Status.FREE) {
            specialPriceRepository.bindToSession(price.getId(), specialPriceSessionId.get());
            return Optional.of(getSpecialPriceByCode(price.getCode()));
        } else if(price.getStatus() == Status.PENDING) {
            Optional<Ticket> optionalTicket = optionally(() -> ticketRepository.findBySpecialPriceId(price.getId()));
            if(optionalTicket.isPresent()) {
                cancelPendingReservation(optionalTicket.get().getTicketsReservationId(), false);
                return Optional.of(getSpecialPriceByCode(price.getCode()));
            }
        }

        return specialPrice;
    }

    public List<Ticket> findTicketsInReservation(String reservationId) {
        return ticketRepository.findTicketsInReservation(reservationId);
    }

    public List<Triple<AdditionalService, List<AdditionalServiceText>, AdditionalServiceItem>> findAdditionalServicesInReservation(String reservationId) {
        return additionalServiceItemRepository.findByReservationUuid(reservationId).stream()
            .map(asi -> Triple.of(additionalServiceRepository.getById(asi.getAdditionalServiceId(), asi.getEventId()), additionalServiceTextRepository.findAllByAdditionalServiceId(asi.getAdditionalServiceId()), asi))
            .collect(Collectors.toList());
    }

    public Optional<String> getVAT(Event event) {
        return configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.VAT_NR));
    }

    public void updateTicketOwner(Ticket ticket,
                                  Locale locale,
                                  Event event,
                                  UpdateTicketOwnerForm updateTicketOwner,
                                  PartialTicketTextGenerator confirmationTextBuilder,
                                  PartialTicketTextGenerator ownerChangeTextBuilder,
                                  PartialTicketPDFGenerator pdfTemplateGenerator,
                                  Optional<UserDetails> userDetails) {

        String newEmail = updateTicketOwner.getEmail().trim();
        String newFullName = updateTicketOwner.getFullName().trim();
        ticketRepository.updateTicketOwner(ticket.getUuid(), newEmail, newFullName);
        //
        Locale userLocale = Optional.ofNullable(StringUtils.trimToNull(updateTicketOwner.getUserLanguage())).map(Locale::forLanguageTag).orElse(locale);

        ticketRepository.updateOptionalTicketInfo(ticket.getUuid(), userLocale.getLanguage());
        ticketFieldRepository.updateOrInsert(updateTicketOwner.getAdditional(), ticket, event);

        Ticket newTicket = ticketRepository.findByUUID(ticket.getUuid());
        if (!StringUtils.equalsIgnoreCase(newEmail, ticket.getEmail()) || !StringUtils.equalsIgnoreCase(newFullName, ticket.getFullName())) {
            sendTicketByEmail(newTicket, userLocale, event, confirmationTextBuilder, pdfTemplateGenerator);
        }

        boolean admin = isAdmin(userDetails);

        if (!admin && StringUtils.isNotBlank(ticket.getEmail()) && !StringUtils.equalsIgnoreCase(newEmail, ticket.getEmail())) {
            Locale oldUserLocale = Locale.forLanguageTag(ticket.getUserLanguage());
            String subject = messageSource.getMessage("ticket-has-changed-owner-subject", new Object[] {event.getDisplayName()}, oldUserLocale);
            notificationManager.sendSimpleEmail(event, ticket.getEmail(), subject, () -> ownerChangeTextBuilder.generate(newTicket));
        }

        if(admin) {
            TicketReservation reservation = findById(ticket.getTicketsReservationId()).orElseThrow(IllegalStateException::new);
            //if the current user is admin, then it would be good to update also the name of the Reservation Owner
            String username = userDetails.get().getUsername();
            log.warn("Reservation {}: forced assignee replacement old: {} new: {}", reservation.getId(), reservation.getFullName(), username);
            ticketReservationRepository.updateAssignee(reservation.getId(), username);
        }
        pluginManager.handleTicketAssignment(newTicket);
    }

    private boolean isAdmin(Optional<UserDetails> userDetails) {
        return userDetails.flatMap(u -> u.getAuthorities().stream().map(a -> Role.fromRoleName(a.getAuthority())).filter(Role.ADMIN::equals).findFirst()).isPresent();
    }

    private void sendTicketByEmail(Ticket ticket, Locale locale, Event event, PartialTicketTextGenerator confirmationTextBuilder, PartialTicketPDFGenerator pdfTemplateGenerator) {
        try {
            notificationManager.sendTicketByEmail(ticket, event, locale, confirmationTextBuilder, pdfTemplateGenerator);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<Triple<Event, TicketReservation, Ticket>> fetchComplete(String eventName, String reservationId, String ticketIdentifier) {
        return from(eventName, reservationId, ticketIdentifier).flatMap((t) -> {
            if(t.getMiddle().getStatus() == TicketReservationStatus.COMPLETE) {
                return Optional.of(t);
            } else {
                return Optional.empty();
            }
        });
    }

    /**
     * Return a fully present triple only if the values are present (obviously) and the the reservation has a COMPLETE status and the ticket is considered assigned.
     *
     * @param eventName
     * @param reservationId
     * @param ticketIdentifier
     * @return
     */
    public Optional<Triple<Event, TicketReservation, Ticket>> fetchCompleteAndAssigned(String eventName, String reservationId, String ticketIdentifier) {
        return fetchComplete(eventName, reservationId, ticketIdentifier).flatMap((t) -> {
            if (t.getRight().getAssigned()) {
                return Optional.of(t);
            } else {
                return Optional.empty();
            }
        });
    }

    private List<Pair<TicketReservation, OrderSummary>> fetchWaitingForPayment(List<String> reservationIds, Event event, Locale locale) {
        return ticketReservationRepository.findAllReservationsWaitingForPayment().stream()
                    .filter(reservationIds::contains)
                    .map(id -> Pair.of(ticketReservationRepository.findReservationById(id), orderSummaryForReservationId(id, event, locale)))
                    .collect(Collectors.toList());
    }

    void sendReminderForOfflinePayments() {
        Date expiration = truncate(addHours(new Date(), configurationManager.getIntConfigValue(Configuration.getSystemConfiguration(OFFLINE_REMINDER_HOURS), 24)), Calendar.DATE);
        ticketReservationRepository.findAllOfflinePaymentReservationForNotification(expiration).stream()
                .map(reservation -> {
                    Optional<Ticket> ticket = ticketRepository.findTicketsInReservation(reservation.getId()).stream().findFirst();
                    Optional<Event> event = ticket.map(t -> eventRepository.findById(t.getEventId()));
                    Optional<Locale> locale = ticket.map(t -> Locale.forLanguageTag(t.getUserLanguage()));
                    return Triple.of(reservation, event, locale);
                })
                .filter(p -> p.getMiddle().isPresent())
                .filter(p -> {
                    Event event = p.getMiddle().get();
                    return truncate(addHours(new Date(), configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), OFFLINE_REMINDER_HOURS), 24)), Calendar.DATE).compareTo(p.getLeft().getValidity()) >= 0;
                })
                .map(p -> Triple.of(p.getLeft(), p.getMiddle().get(), p.getRight().get()))
                .forEach(p -> {
                    TicketReservation reservation = p.getLeft();
                    Event event = p.getMiddle();
                    Map<String, Object> model = prepareModelForReservationEmail(event, reservation);
                    model.put("expirationDate", ZonedDateTime.ofInstant(reservation.getValidity().toInstant(), event.getZoneId()));
                    Locale locale = p.getRight();
                    ticketReservationRepository.flagAsOfflinePaymentReminderSent(reservation.getId());
                    notificationManager.sendSimpleEmail(event, reservation.getEmail(), messageSource.getMessage("reservation.reminder.mail.subject", new Object[]{getShortReservationID(event, reservation.getId())}, locale), () -> templateManager.renderClassPathResource("/alfio/templates/reminder-email-txt.ms", model, locale, TemplateOutput.TEXT));
                });
    }

    void sendReminderForTicketAssignment() {
        getNotifiableEventsStream()
                .map(e -> Pair.of(e, ticketRepository.findAllReservationsConfirmedButNotAssigned(e.getId())))
                .filter(p -> !p.getRight().isEmpty())
                .forEach(p -> Wrappers.voidTransactionWrapper(this::sendAssignmentReminder, p));
    }

    void sendReminderForOptionalData() {
        getNotifiableEventsStream()
                .filter(e -> ticketFieldRepository.countAdditionalFieldsForEvent(e.getId()) > 0)
                .map(e -> Pair.of(e, ticketRepository.findAllAssignedButNotYetNotified(e.getId())))
                .filter(p -> !p.getRight().isEmpty())
                .forEach(p -> Wrappers.voidTransactionWrapper(this::sendOptionalDataReminder, p));
    }

    private void sendOptionalDataReminder(Pair<Event, List<Ticket>> eventAndTickets) {
        requiresNewTransactionTemplate.execute(ts -> {
            Event event = eventAndTickets.getLeft();
            int daysBeforeStart = configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.ASSIGNMENT_REMINDER_START), 10);
            List<Ticket> tickets = eventAndTickets.getRight().stream().filter(t -> !ticketFieldRepository.hasOptionalData(t.getId())).collect(toList());
            Set<String> notYetNotifiedReservations = tickets.stream().map(Ticket::getTicketsReservationId).distinct().filter(rid -> findByIdForNotification(rid, event.getZoneId(), daysBeforeStart).isPresent()).collect(toSet());
            tickets.stream()
                    .filter(t -> notYetNotifiedReservations.contains(t.getTicketsReservationId()))
                    .forEach(t -> {
                        int result = ticketRepository.flagTicketAsReminderSent(t.getId());
                        Validate.isTrue(result == 1);
                        Map<String, Object> model = new HashMap<>();
                        model.put("event", event);
                        model.put("fullName", t.getFullName());
                        model.put("organization", organizationRepository.getById(event.getOrganizationId()));
                        model.put("ticketURL", ticketUpdateUrl(t.getTicketsReservationId(), event, t.getUuid()));
                        Locale locale = Optional.ofNullable(t.getUserLanguage()).map(Locale::forLanguageTag).orElseGet(() -> findReservationLanguage(t.getTicketsReservationId()));
                        notificationManager.sendSimpleEmail(event, t.getEmail(), messageSource.getMessage("reminder.ticket-additional-info.subject", new Object[]{event.getDisplayName()}, locale), () -> templateManager.renderClassPathResource("/alfio/templates/reminder-ticket-additional-info.ms", model, locale, TemplateOutput.TEXT));
                    });
            return null;
        });
    }

    Stream<Event> getNotifiableEventsStream() {
        return eventRepository.findAll().stream()
                .filter(e -> {
                    int daysBeforeStart = configurationManager.getIntConfigValue(Configuration.from(e.getOrganizationId(), e.getId(), ConfigurationKeys.ASSIGNMENT_REMINDER_START), 10);
                    //we don't want to define events SO far away, don't we?
                    int days = (int) ChronoUnit.DAYS.between(ZonedDateTime.now(e.getZoneId()).toLocalDate(), e.getBegin().toLocalDate());
                    return days > 0 && days <= daysBeforeStart;
                });
    }

    private void sendAssignmentReminder(Pair<Event, List<String>> p) {
        try {
            requiresNewTransactionTemplate.execute(status -> {
                Event event = p.getLeft();
                ZoneId eventZoneId = event.getZoneId();
                int quietPeriod = configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.ASSIGNMENT_REMINDER_INTERVAL), 3);
                p.getRight().stream()
                        .map(id -> findByIdForNotification(id, eventZoneId, quietPeriod))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .forEach(reservation -> {
                            Map<String, Object> model = prepareModelForReservationEmail(event, reservation);
                            model.put("reservationShortID", getShortReservationID(event, reservation.getId()));
                            ticketReservationRepository.updateLatestReminderTimestamp(reservation.getId(), ZonedDateTime.now(eventZoneId));
                            Locale locale = findReservationLanguage(reservation.getId());
                            notificationManager.sendSimpleEmail(event, reservation.getEmail(), messageSource.getMessage("reminder.ticket-not-assigned.subject", new Object[]{event.getDisplayName()}, locale), () -> templateManager.renderClassPathResource("/alfio/templates/reminder-tickets-assignment-email-txt.ms", model, locale, TemplateOutput.TEXT));
                        });
                return null;
            });
        } catch (Exception ex) {
            log.warn("cannot send reminder message", ex);
        }
    }

    public TicketReservation findByPartialID(String reservationId) {
        Validate.notBlank(reservationId, "invalid reservationId");
        Validate.matchesPattern(reservationId, "^[^%]*$", "invalid character found");
        List<TicketReservation> results = ticketReservationRepository.findByPartialID(StringUtils.trimToEmpty(reservationId).toLowerCase() + "%");
        Validate.isTrue(results.size() > 0, "reservation not found");
        Validate.isTrue(results.size() == 1, "multiple results found. Try handling this reservation manually.");
        return results.get(0);
    }

    public String getShortReservationID(Event event, String reservationId) {
        return configurationManager.getShortReservationID(event, reservationId);
    }

    public int countAvailableTickets(Event event, TicketCategory category) {
        if(category.isBounded()) {
            return ticketRepository.countNotSoldTickets(event.getId(), category.getId());
        }
        return ticketRepository.countNotSoldTicketsForUnbounded(event.getId());
    }

    public void releaseTicket(Event event, TicketReservation ticketReservation, Ticket ticket) {
        TicketCategory category = ticketCategoryRepository.getById(ticket.getCategoryId(), event.getId());
        if(!CategoryEvaluator.isTicketCancellationAvailable(ticketCategoryRepository, ticket)) {
            throw new IllegalStateException("Cannot release reserved tickets");
        }
        int result = ticketRepository.releaseTicket(ticketReservation.getId(), event.getId(), ticket.getId());
        Validate.isTrue(result == 1, String.format("Expected 1 row to be updated, got %d", result));
        if(category.isAccessRestricted()) {
            ticketRepository.unbindTicketsFromCategory(event.getId(), category.getId(), singletonList(ticket.getId()));
        }
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        Map<String, Object> model = new HashMap<>();
        model.put("eventName", event.getDisplayName());
        model.put("ticket", ticket);
        model.put("organization", organization);
        Locale locale = Locale.forLanguageTag(Optional.ofNullable(ticket.getUserLanguage()).orElse("en"));
        notificationManager.sendSimpleEmail(event, ticket.getEmail(), messageSource.getMessage("email-ticket-released.subject",
                new Object[]{event.getDisplayName()}, locale),
                () -> templateManager.renderClassPathResource("/alfio/templates/ticket-has-been-cancelled-txt.ms", model, locale, TemplateOutput.TEXT));

        String ticketCategoryDescription = ticketCategoryDescriptionRepository.findByTicketCategoryIdAndLocale(category.getId(), ticket.getUserLanguage()).orElse("");

        String adminTemplate = messageSource.getMessage("email-ticket-released.admin.text",
                new Object[] {ticket.getId(), ticket.getUuid(), ticket.getFullName(), ticket.getEmail(), ticketCategoryDescription, category.getId()}, Locale.ENGLISH);
        notificationManager.sendSimpleEmail(event, organization.getEmail(), messageSource.getMessage("email-ticket-released.admin.subject",
                new Object[]{ticket.getId(), event.getDisplayName()}, locale),
                () -> templateManager.renderString(adminTemplate, model, Locale.ENGLISH, TemplateOutput.TEXT));
        if(ticketRepository.countTicketsInReservation(ticketReservation.getId()) == 0) {
            deleteReservations(singletonList(ticketReservation.getId()));
        }
    }

    public int getReservationTimeout(Event event) {
        return configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), RESERVATION_TIMEOUT), 25);
    }

    public void validateAndConfirmOfflinePayment(String reservationId, Event event, BigDecimal paidAmount) {
        TicketReservation reservation = findByPartialID(reservationId);
        Optional<OrderSummary> optionalOrderSummary = optionally(() -> orderSummaryForReservationId(reservation.getId(), event, Locale.forLanguageTag(reservation.getUserLanguage())));
        Validate.isTrue(optionalOrderSummary.isPresent(), "Reservation not found");
        OrderSummary orderSummary = optionalOrderSummary.get();
        Validate.isTrue(MonetaryUtil.centsToUnit(orderSummary.getOriginalTotalPrice().getPriceWithVAT()).compareTo(paidAmount) == 0, "paid price differs from due price");
        confirmOfflinePayment(event, reservation.getId());
    }

    public List<Pair<TicketReservation, OrderSummary>> getPendingPayments(EventWithStatistics eventWithStatistics) {
        Event event = eventWithStatistics.getEvent();

        List<TicketCategoryWithStatistic> categories = eventWithStatistics.getTicketCategories();
        if(categories.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> reservationIds = ticketRepository.findPendingTicketsInCategories(categories.stream().map(TicketCategoryWithStatistic::getId).collect(toList()))
                .stream()
                .map(Ticket::getTicketsReservationId)
                .distinct()
                .collect(toList());
        return fetchWaitingForPayment(reservationIds, event, Locale.ENGLISH);
    }

    @Data
    private class Price {
        private final int net;
        private final int vat;

        Price sum(Price price) {
            return new Price(net + price.net, vat + price.vat);
        }

        int getTotal() {
            return net + vat;
        }
    }
}
