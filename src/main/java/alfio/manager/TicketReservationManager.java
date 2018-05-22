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
import alfio.manager.support.CategoryEvaluator;
import alfio.manager.support.FeeCalculator;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.Mailer;
import alfio.model.*;
import alfio.model.AdditionalServiceItem.AdditionalServiceItemStatus;
import alfio.model.PromoCodeDiscount.DiscountType;
import alfio.model.SpecialPrice.Status;
import alfio.model.Ticket.TicketStatus;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.decorator.AdditionalServiceItemPriceContainer;
import alfio.model.decorator.AdditionalServicePriceContainer;
import alfio.model.decorator.TicketPriceContainer;
import alfio.model.modification.ASReservationWithOptionalCodeModification;
import alfio.model.modification.AdditionalServiceReservationModification;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.util.*;
import de.danielbechler.diff.ObjectDifferBuilder;
import de.danielbechler.diff.node.DiffNode;
import de.danielbechler.diff.node.Visit;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
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
import static alfio.util.MonetaryUtil.formatCents;
import static alfio.util.MonetaryUtil.unitToCents;
import static alfio.util.OptionalWrapper.optionally;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.*;
import static org.apache.commons.lang3.time.DateUtils.addHours;
import static org.apache.commons.lang3.time.DateUtils.truncate;

@Component
@Transactional
@Log4j2
public class TicketReservationManager {
    
    private static final String STUCK_TICKETS_MSG = "there are stuck tickets for the event %s. Please check admin area.";
    private static final String STUCK_TICKETS_SUBJECT = "warning: stuck tickets found";
    static final String NOT_YET_PAID_TRANSACTION_ID = "not-paid";

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
    private final TicketFieldRepository ticketFieldRepository;
    private final AdditionalServiceRepository additionalServiceRepository;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final AdditionalServiceTextRepository additionalServiceTextRepository;
    private final InvoiceSequencesRepository invoiceSequencesRepository;
    private final AuditingRepository auditingRepository;
    private final UserRepository userRepository;
    private final ExtensionManager extensionManager;
    private final TicketSearchRepository ticketSearchRepository;

    public static class NotEnoughTicketsException extends RuntimeException {

    }

    public static class MissingSpecialPriceTokenException extends RuntimeException {
    }

    public static class InvalidSpecialPriceTokenException extends RuntimeException {

    }

    public static class OfflinePaymentException extends RuntimeException {
        OfflinePaymentException(String message){ super(message); }
    }

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
                                    TicketFieldRepository ticketFieldRepository,
                                    AdditionalServiceRepository additionalServiceRepository,
                                    AdditionalServiceItemRepository additionalServiceItemRepository,
                                    AdditionalServiceTextRepository additionalServiceTextRepository,
                                    InvoiceSequencesRepository invoiceSequencesRepository,
                                    AuditingRepository auditingRepository,
                                    UserRepository userRepository,
                                    ExtensionManager extensionManager, TicketSearchRepository ticketSearchRepository) {
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
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager, new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        this.ticketFieldRepository = ticketFieldRepository;
        this.additionalServiceRepository = additionalServiceRepository;
        this.additionalServiceItemRepository = additionalServiceItemRepository;
        this.additionalServiceTextRepository = additionalServiceTextRepository;
        this.invoiceSequencesRepository = invoiceSequencesRepository;
        this.auditingRepository = auditingRepository;
        this.userRepository = userRepository;
        this.extensionManager = extensionManager;
        this.ticketSearchRepository = ticketSearchRepository;
    }
    
    /**
     * Create a ticket reservation. It will create a reservation _only_ if it can find enough tickets. Note that it will not do date/validity validation. This must be ensured by the
     * caller.
     *
     * @param event
     * @param list
     * @param reservationExpiration
     * @param forWaitingQueue
     * @return
     */
    public String createTicketReservation(Event event,
                                          List<TicketReservationWithOptionalCodeModification> list,
                                          List<ASReservationWithOptionalCodeModification> additionalServices,
                                          Date reservationExpiration,
                                          Optional<String> specialPriceSessionId,
                                          Optional<String> promotionCodeDiscount,
                                          Locale locale,
                                          boolean forWaitingQueue) throws NotEnoughTicketsException, MissingSpecialPriceTokenException, InvalidSpecialPriceTokenException {
        String reservationId = UUID.randomUUID().toString();
        
        Optional<PromoCodeDiscount> discount = promotionCodeDiscount.flatMap((promoCodeDiscount) -> promoCodeDiscountRepository.findPromoCodeInEventOrOrganization(event.getId(), promoCodeDiscount));
        
        ticketReservationRepository.createNewReservation(reservationId, ZonedDateTime.now(event.getZoneId()), reservationExpiration, discount.map(PromoCodeDiscount::getId).orElse(null), locale.getLanguage(), event.getId(), event.getVat(), event.isVatIncluded());
        list.forEach(t -> reserveTicketsForCategory(event, specialPriceSessionId, reservationId, t, locale, forWaitingQueue, discount.orElse(null)));

        int ticketCount = list
            .stream()
            .map(TicketReservationWithOptionalCodeModification::getAmount)
            .mapToInt(Integer::intValue).sum();

        // apply valid additional service with supplement policy mandatory one for ticket
        additionalServiceRepository.findAllInEventWithPolicy(event.getId(), AdditionalService.SupplementPolicy.MANDATORY_ONE_FOR_TICKET)
            .stream()
            .filter(AdditionalService::getSaleable)
            .forEach(as -> {
                AdditionalServiceReservationModification asrm = new AdditionalServiceReservationModification();
                asrm.setAdditionalServiceId(as.getId());
                asrm.setQuantity(ticketCount);
                reserveAdditionalServicesForReservation(event.getId(), reservationId, new ASReservationWithOptionalCodeModification(asrm, Optional.empty()), discount.orElse(null));
        });

        additionalServices.forEach(as -> reserveAdditionalServicesForReservation(event.getId(), reservationId, as, discount.orElse(null)));

        TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);

        OrderSummary orderSummary = orderSummaryForReservationId(reservation.getId(), event, Locale.forLanguageTag(reservation.getUserLanguage()));
        ticketReservationRepository.addReservationInvoiceOrReceiptModel(reservationId, Json.toJson(orderSummary));

        auditingRepository.insert(reservationId, null, event.getId(), Audit.EventType.RESERVATION_CREATE, new Date(), Audit.EntityType.RESERVATION, reservationId);

        return reservationId;
    }

    public Pair<List<TicketReservation>, Integer> findAllReservationsInEvent(int eventId, Integer page, String search, List<TicketReservationStatus> status) {
        final int pageSize = 50;
        int offset = page == null ? 0 : page * pageSize;
        String toSearch = StringUtils.trimToNull(search);
        toSearch = toSearch == null ? null : ("%" + toSearch + "%");
        List<String> toFilter = (status == null || status.isEmpty() ? Arrays.asList(TicketReservationStatus.values()) : status).stream().map(TicketReservationStatus::toString).collect(toList());
        List<TicketReservation> reservationsForEvent = ticketSearchRepository.findReservationsForEvent(eventId, offset, pageSize, toSearch, toFilter);
        return Pair.of(reservationsForEvent, ticketSearchRepository.countReservationsForEvent(eventId, toSearch, toFilter));
    }

    void reserveTicketsForCategory(Event event, Optional<String> specialPriceSessionId, String transactionId, TicketReservationWithOptionalCodeModification ticketReservation, Locale locale, boolean forWaitingQueue, PromoCodeDiscount discount) {
        //first check if there is another pending special price token bound to the current sessionId
        Optional<SpecialPrice> specialPrice = fixToken(ticketReservation.getSpecialPrice(), ticketReservation.getTicketCategoryId(), event.getId(), specialPriceSessionId, ticketReservation);

        List<Integer> reservedForUpdate = reserveTickets(event.getId(), ticketReservation, forWaitingQueue ? asList(TicketStatus.RELEASED, TicketStatus.PRE_RESERVED) : singletonList(TicketStatus.FREE));
        int requested = ticketReservation.getAmount();
        if (reservedForUpdate.size() != requested) {
            throw new NotEnoughTicketsException();
        }

        TicketCategory category = ticketCategoryRepository.getByIdAndActive(ticketReservation.getTicketCategoryId(), event.getId());
        if (specialPrice.isPresent()) {
            if(reservedForUpdate.size() != 1) {
                throw new NotEnoughTicketsException();
            }
            SpecialPrice sp = specialPrice.get();
            ticketRepository.reserveTicket(transactionId, reservedForUpdate.stream().findFirst().orElseThrow(IllegalStateException::new),sp.getId(), locale.getLanguage(), category.getSrcPriceCts());
            specialPriceRepository.updateStatus(sp.getId(), Status.PENDING.toString(), sp.getSessionIdentifier());
        } else {
            ticketRepository.reserveTickets(transactionId, reservedForUpdate, ticketReservation.getTicketCategoryId(), locale.getLanguage(), category.getSrcPriceCts());
        }
        Ticket ticket = ticketRepository.findById(reservedForUpdate.get(0), category.getId());
        TicketPriceContainer priceContainer = TicketPriceContainer.from(ticket, null, event, discount);
        ticketRepository.updateTicketPrice(reservedForUpdate, category.getId(), event.getId(), category.getSrcPriceCts(), MonetaryUtil.unitToCents(priceContainer.getFinalPrice()), MonetaryUtil.unitToCents(priceContainer.getVAT()), MonetaryUtil.unitToCents(priceContainer.getAppliedDiscount()));
    }

    private void reserveAdditionalServicesForReservation(int eventId, String transactionId, ASReservationWithOptionalCodeModification additionalServiceReservation, PromoCodeDiscount discount) {
        Optional.ofNullable(additionalServiceReservation.getAdditionalServiceId())
            .flatMap(id -> optionally(() -> additionalServiceRepository.getById(id, eventId)))
            .filter(as -> additionalServiceReservation.getQuantity() > 0 && (as.isFixPrice() || Optional.ofNullable(additionalServiceReservation.getAmount()).filter(a -> a.compareTo(BigDecimal.ZERO) > 0).isPresent()))
            .map(as -> Pair.of(eventRepository.findById(eventId), as))
            .ifPresent(pair -> {
                Event e = pair.getKey();
                AdditionalService as = pair.getValue();
                IntStream.range(0, additionalServiceReservation.getQuantity())
                    .forEach(i -> {
                        AdditionalServicePriceContainer pc = AdditionalServicePriceContainer.from(additionalServiceReservation.getAmount(), as, e, discount);
                        additionalServiceItemRepository.insert(UUID.randomUUID().toString(), ZonedDateTime.now(Clock.systemUTC()), transactionId,
                            as.getId(), AdditionalServiceItemStatus.PENDING, eventId, pc.getSrcPriceCts(), unitToCents(pc.getFinalPrice()), unitToCents(pc.getVAT()), unitToCents(pc.getAppliedDiscount()));
                    });
            });

    }

    List<Integer> reserveTickets(int eventId, TicketReservationWithOptionalCodeModification ticketReservation, List<TicketStatus> requiredStatuses) {
        return reserveTickets(eventId, ticketReservation.getTicketCategoryId(), ticketReservation.getAmount(), requiredStatuses);
    }

    List<Integer> reserveTickets(int eventId , int categoryId, int qty, List<TicketStatus> requiredStatuses) {
        TicketCategory category = ticketCategoryRepository.getByIdAndActive(categoryId, eventId);
        List<String> statusesAsString = requiredStatuses.stream().map(TicketStatus::name).collect(toList());
        if(category.isBounded()) {
            return ticketRepository.selectTicketInCategoryForUpdate(eventId, categoryId, qty, statusesAsString);
        }
        return ticketRepository.selectNotAllocatedTicketsForUpdate(eventId, qty, statusesAsString);
    }

    Optional<SpecialPrice> fixToken(Optional<SpecialPrice> token, int ticketCategoryId, int eventId, Optional<String> specialPriceSessionId, TicketReservationWithOptionalCodeModification ticketReservation) {

        TicketCategory ticketCategory = ticketCategoryRepository.getByIdAndActive(ticketCategoryId, eventId);
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
                                 String email, CustomerName customerName, Locale userLanguage, String billingAddress,
                                 TotalPrice reservationCost, Optional<String> specialPriceSessionId, Optional<PaymentProxy> method,
                                 boolean invoiceRequested, String vatCountryCode, String vatNr, PriceContainer.VatStatus vatStatus) {
        PaymentProxy paymentProxy = evaluatePaymentProxy(method, reservationCost);
        if(!initPaymentProcess(reservationCost, paymentProxy, reservationId, email, customerName, userLanguage, billingAddress)) {
            return PaymentResult.unsuccessful("error.STEP2_UNABLE_TO_TRANSITION");
        }
        try {
            PaymentResult paymentResult;
            ticketReservationRepository.lockReservationForUpdate(reservationId);
            if(reservationCost.getPriceWithVAT() > 0) {
                if(invoiceRequested && configurationManager.hasAllConfigurationsForInvoice(event)) {
                    int invoiceSequence = invoiceSequencesRepository.lockReservationForUpdate(event.getOrganizationId());
                    invoiceSequencesRepository.incrementSequenceFor(event.getOrganizationId());
                    String pattern = configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.INVOICE_NUMBER_PATTERN), "%d");
                    ticketReservationRepository.setInvoiceNumber(reservationId, String.format(pattern, invoiceSequence));
                }
                ticketReservationRepository.updateBillingData(vatStatus, vatNr, vatCountryCode, invoiceRequested, reservationId);

                //
                extensionManager.handleInvoiceGeneration(event, reservationId,
                    email, customerName, userLanguage, billingAddress,
                    reservationCost, invoiceRequested, vatCountryCode, vatNr, vatStatus).ifPresent(invoiceGeneration -> {
                    if (invoiceGeneration.getInvoiceNumber() != null) {
                        ticketReservationRepository.setInvoiceNumber(reservationId, invoiceGeneration.getInvoiceNumber());
                    }
                });

                //
                switch(paymentProxy) {
                    case STRIPE:
                        paymentResult = paymentManager.processStripePayment(reservationId, gatewayToken, reservationCost.getPriceWithVAT(), event, email, customerName, billingAddress);
                        if(!paymentResult.isSuccessful()) {
                            reTransitionToPending(reservationId);
                            return paymentResult;
                        }
                        break;
                    case PAYPAL:
                        paymentResult = paymentManager.processPayPalPayment(reservationId, gatewayToken, payerId, reservationCost.getPriceWithVAT(), event);
                        if(!paymentResult.isSuccessful()) {
                            reTransitionToPending(reservationId);
                            return paymentResult;
                        }
                        break;
                    case OFFLINE:
                        transitionToOfflinePayment(event, reservationId, email, customerName, billingAddress);
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
            completeReservation(event.getId(), reservationId, email, customerName, userLanguage, billingAddress, specialPriceSessionId, paymentProxy);
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

    private boolean initPaymentProcess(TotalPrice reservationCost, PaymentProxy paymentProxy, String reservationId, String email, CustomerName customerName, Locale userLanguage, String billingAddress) {
        if(reservationCost.getPriceWithVAT() > 0 && paymentProxy == PaymentProxy.STRIPE) {
            try {
                transitionToInPayment(reservationId, email, customerName, userLanguage, billingAddress);
            } catch (Exception e) {
                //unable to do the transition. Exiting.
                log.debug(String.format("unable to flag the reservation %s as IN_PAYMENT", reservationId), e);
                return false;
            }
        }
        return true;
    }

    public void confirmOfflinePayment(Event event, String reservationId, String username) {
        TicketReservation ticketReservation = findById(reservationId).orElseThrow(IllegalArgumentException::new);
        ticketReservationRepository.lockReservationForUpdate(reservationId);
        Validate.isTrue(ticketReservation.getPaymentMethod() == PaymentProxy.OFFLINE, "invalid payment method");
        Validate.isTrue(ticketReservation.getStatus() == TicketReservationStatus.OFFLINE_PAYMENT, "invalid status");


        ticketReservationRepository.confirmOfflinePayment(reservationId, TicketReservationStatus.COMPLETE.name(), ZonedDateTime.now(event.getZoneId()));

        registerAlfioTransaction(event, reservationId, PaymentProxy.OFFLINE);

        auditingRepository.insert(reservationId, userRepository.findIdByUserName(username).orElse(null), event.getId(), Audit.EventType.RESERVATION_OFFLINE_PAYMENT_CONFIRMED, new Date(), Audit.EntityType.RESERVATION, ticketReservation.getId());

        CustomerName customerName = new CustomerName(ticketReservation.getFullName(), ticketReservation.getFirstName(), ticketReservation.getLastName(), event);
        acquireItems(TicketStatus.ACQUIRED, AdditionalServiceItemStatus.ACQUIRED, PaymentProxy.OFFLINE, reservationId, ticketReservation.getEmail(), customerName, ticketReservation.getUserLanguage(), ticketReservation.getBillingAddress(), event.getId());

        Locale language = findReservationLanguage(reservationId);

        sendConfirmationEmail(event, findById(reservationId).orElseThrow(IllegalArgumentException::new), language);

        final TicketReservation finalReservation = ticketReservationRepository.findReservationById(reservationId);
        extensionManager.handleReservationConfirmation(finalReservation, event.getId());
    }

    void registerAlfioTransaction(Event event, String reservationId, PaymentProxy paymentProxy) {
        int priceWithVAT = totalReservationCostWithVAT(reservationId).getPriceWithVAT();
        Long platformFee = FeeCalculator.getCalculator(event, configurationManager)
            .apply(ticketRepository.countTicketsInReservation(reservationId), (long) priceWithVAT)
            .orElse(0L);

        //FIXME we must support multiple transactions for a reservation, otherwise we can't handle properly the case of ON_SITE payments

        if(paymentProxy != PaymentProxy.ON_SITE || !transactionRepository.loadOptionalByReservationId(reservationId).isPresent()) {
            String transactionId = paymentProxy.getKey() + "-" + System.currentTimeMillis();
            transactionRepository.insert(transactionId, null, reservationId, ZonedDateTime.now(event.getZoneId()),
                priceWithVAT, event.getCurrency(), "Offline payment confirmed for "+reservationId, paymentProxy.getKey(), platformFee, 0L);
        } else {
            log.warn("ON-Site check-in: ignoring transaction registration for reservationId {}", reservationId);
        }

    }


    public void sendConfirmationEmail(Event event, TicketReservation ticketReservation, Locale language) {
        String reservationId = ticketReservation.getId();

        OrderSummary summary = orderSummaryForReservationId(reservationId, event, language);

        Map<String, Object> reservationEmailModel = prepareModelForReservationEmail(event, ticketReservation);
        List<Mailer.Attachment> attachments = new ArrayList<>(1);
        if(!summary.getNotYetPaid() && !summary.getFree()) {
            Map<String, String> model = new HashMap<>();
            model.put("reservationId", reservationId);
            model.put("eventId", Integer.toString(event.getId()));
            model.put("language", Json.toJson(language));
            model.put("reservationEmailModel", Json.toJson(reservationEmailModel));
            if(ticketReservation.getHasInvoiceNumber()) {
                attachments.add(new Mailer.Attachment("invoice.pdf", null, "application/pdf", model, Mailer.AttachmentIdentifier.INVOICE_PDF));
            } else {
                attachments.add(new Mailer.Attachment("receipt.pdf", null, "application/pdf", model, Mailer.AttachmentIdentifier.RECEIPT_PDF));
            }

        }

        notificationManager.sendSimpleEmail(event, ticketReservation.getEmail(), messageSource.getMessage("reservation-email-subject",
                new Object[]{getShortReservationID(event, reservationId), event.getDisplayName()}, language),
            () -> templateManager.renderTemplate(event, TemplateResource.CONFIRMATION_EMAIL, reservationEmailModel, language), attachments);
    }

    private Locale findReservationLanguage(String reservationId) {
        return ticketReservationRepository.findOptionalReservationById(reservationId).map(TicketReservation::getUserLanguage).map(Locale::forLanguageTag).orElse(Locale.ENGLISH);
    }

    public void deleteOfflinePayment(Event event, String reservationId, boolean expired) {
        TicketReservation reservation = findById(reservationId).orElseThrow(IllegalArgumentException::new);
        Validate.isTrue(reservation.getStatus() == OFFLINE_PAYMENT, "Invalid reservation status");
        Map<String, Object> emailModel = prepareModelForReservationEmail(event, reservation);
        Locale reservationLanguage = findReservationLanguage(reservationId);
        String subject = messageSource.getMessage("reservation-email-expired-subject", new Object[]{getShortReservationID(event, reservationId), event.getDisplayName()}, reservationLanguage);
        cancelReservation(reservationId, expired);
        notificationManager.sendSimpleEmail(event, reservation.getEmail(), subject,
            () ->  templateManager.renderTemplate(event, TemplateResource.OFFLINE_RESERVATION_EXPIRED_EMAIL, emailModel, reservationLanguage)
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> prepareModelForReservationEmail(Event event, TicketReservation reservation, Optional<String> vat, OrderSummary summary) {
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        String reservationUrl = reservationUrl(reservation.getId());
        String reservationShortID = getShortReservationID(event, reservation.getId());
        Optional<String> invoiceAddress = configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.INVOICE_ADDRESS));
        Optional<String> bankAccountNr = configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.BANK_ACCOUNT_NR));
        Optional<String> bankAccountOwner = configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.BANK_ACCOUNT_OWNER));
        Map<Integer, List<Ticket>> ticketsByCategory = ticketRepository.findTicketsInReservation(reservation.getId())
            .stream()
            .collect(groupingBy(Ticket::getCategoryId));
        List<TicketWithCategory> ticketsWithCategory = ticketCategoryRepository.findByIds(ticketsByCategory.keySet())
            .stream()
            .flatMap(tc -> ticketsByCategory.get(tc.getId()).stream().map(t -> new TicketWithCategory(t, tc)))
            .collect(Collectors.toList());
        return TemplateResource.prepareModelForConfirmationEmail(organization, event, reservation, vat, ticketsWithCategory, summary, reservationUrl, reservationShortID, invoiceAddress, bankAccountNr, bankAccountOwner);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> prepareModelForReservationEmail(Event event, TicketReservation reservation) {
        Optional<String> vat = getVAT(event);
        OrderSummary summary = orderSummaryForReservationId(reservation.getId(), event, Locale.forLanguageTag(reservation.getUserLanguage()));
        return prepareModelForReservationEmail(event, reservation, vat, summary);
    }

    private void transitionToInPayment(String reservationId, String email, CustomerName customerName, Locale userLanguage, String billingAddress) {
        requiresNewTransactionTemplate.execute(status -> {
            int updatedReservation = ticketReservationRepository.updateTicketReservation(reservationId, IN_PAYMENT.toString(), email,
                customerName.getFullName(), customerName.getFirstName(), customerName.getLastName(), userLanguage.getLanguage(), billingAddress, null, PaymentProxy.STRIPE.toString());
            Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got " + updatedReservation);
            return null;
        });
    }

    private void transitionToOfflinePayment(Event event, String reservationId, String email, CustomerName customerName, String billingAddress) {
        ZonedDateTime deadline = getOfflinePaymentDeadline(event, configurationManager);
        int updatedReservation = ticketReservationRepository.postponePayment(reservationId, Date.from(deadline.toInstant()), email,
            customerName.getFullName(), customerName.getFirstName(), customerName.getLastName(), billingAddress);
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
        return now.plusDays(waitingPeriod).with(WorkingDaysAdjusters.workingDaysAtNoon()).truncatedTo(ChronoUnit.HOURS);
    }

    public static int getOfflinePaymentWaitingPeriod(Event event, ConfigurationManager configurationManager) {
        ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
        ZonedDateTime eventBegin = event.getBegin();
        int daysToBegin = (int) ChronoUnit.DAYS.between(now.toLocalDate(), eventBegin.toLocalDate());
        if (daysToBegin < 0) {
            throw new OfflinePaymentException("Cannot confirm an offline reservation after event start");
        }
        int waitingPeriod = configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), OFFLINE_PAYMENT_DAYS), 5);
        return Math.min(daysToBegin, waitingPeriod);
    }

    public static boolean hasValidOfflinePaymentWaitingPeriod(Event event, ConfigurationManager configurationManager) {
        try {
            return getOfflinePaymentWaitingPeriod(event, configurationManager) >= 0;
        } catch (OfflinePaymentException e) {
            return false;
        }
    }


    /**
     * ValidPaymentMethod should be configured in organisation and event. And if even already started then event should not have PaymentProxy.OFFLINE as only payment method
     *
     * @param paymentMethod
     * @param event
     * @param configurationManager
     * @return
     */
    public static boolean isValidPaymentMethod(PaymentManager.PaymentMethod paymentMethod, Event event, ConfigurationManager configurationManager) {
        return paymentMethod.isActive() && event.getAllowedPaymentProxies().contains(paymentMethod.getPaymentProxy()) && (!paymentMethod.getPaymentProxy().equals(PaymentProxy.OFFLINE) || hasValidOfflinePaymentWaitingPeriod(event, configurationManager));
    }

    private void reTransitionToPending(String reservationId) {
        int updatedReservation = ticketReservationRepository.updateReservationStatus(reservationId, TicketReservationStatus.PENDING.toString());
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
     */
    void completeReservation(int eventId, String reservationId, String email, CustomerName customerName,
                                     Locale userLanguage, String billingAddress, Optional<String> specialPriceSessionId,
                                     PaymentProxy paymentProxy) {
        if(paymentProxy != PaymentProxy.OFFLINE) {
            TicketStatus ticketStatus = paymentProxy.isDeskPaymentRequired() ? TicketStatus.TO_BE_PAID : TicketStatus.ACQUIRED;
            AdditionalServiceItemStatus asStatus = paymentProxy.isDeskPaymentRequired() ? AdditionalServiceItemStatus.TO_BE_PAID : AdditionalServiceItemStatus.ACQUIRED;
            acquireItems(ticketStatus, asStatus, paymentProxy, reservationId, email, customerName, userLanguage.getLanguage(), billingAddress, eventId);
            final TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
            extensionManager.handleReservationConfirmation(reservation, eventId);
            //cleanup unused special price codes...
            specialPriceSessionId.ifPresent(specialPriceRepository::unbindFromSession);
        }

        auditingRepository.insert(reservationId, null, eventId, Audit.EventType.RESERVATION_COMPLETE, new Date(), Audit.EntityType.RESERVATION, reservationId);
    }

    private void acquireItems(TicketStatus ticketStatus, AdditionalServiceItemStatus asStatus, PaymentProxy paymentProxy, String reservationId, String email, CustomerName customerName, String userLanguage, String billingAddress, int eventId) {
        Map<Integer, Ticket> preUpdateTicket = ticketRepository.findTicketsInReservation(reservationId).stream().collect(toMap(Ticket::getId, Function.identity()));
        int updatedTickets = ticketRepository.updateTicketsStatusWithReservationId(reservationId, ticketStatus.toString());
        Map<Integer, Ticket> postUpdateTicket = ticketRepository.findTicketsInReservation(reservationId).stream().collect(toMap(Ticket::getId, Function.identity()));

        postUpdateTicket.forEach((id, ticket) -> {
            auditUpdateTicket(preUpdateTicket.get(id), Collections.emptyMap(), ticket, Collections.emptyMap(), eventId);
        });

        int updatedAS = additionalServiceItemRepository.updateItemsStatusWithReservationUUID(reservationId, asStatus);
        Validate.isTrue(updatedTickets + updatedAS > 0, "no items have been updated");
        specialPriceRepository.updateStatusForReservation(singletonList(reservationId), Status.TAKEN.toString());
        ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));
        int updatedReservation = ticketReservationRepository.updateTicketReservation(reservationId, TicketReservationStatus.COMPLETE.toString(), email,
            customerName.getFullName(), customerName.getFirstName(), customerName.getLastName(), userLanguage, billingAddress, timestamp, paymentProxy.toString());
        Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got " + updatedReservation);
        waitingQueueManager.fireReservationConfirmed(reservationId);
        if(paymentProxy == PaymentProxy.PAYPAL || paymentProxy == PaymentProxy.ADMIN) {
            //we must notify the plugins about ticket assignment and send them by email
            Event event = eventRepository.findByReservationId(reservationId);
            TicketReservation reservation = findById(reservationId).orElseThrow(IllegalStateException::new);
            findTicketsInReservation(reservationId).stream()
                .filter(ticket -> StringUtils.isNotBlank(ticket.getFullName()) || StringUtils.isNotBlank(ticket.getFirstName()) || StringUtils.isNotBlank(ticket.getEmail()))
                .forEach(ticket -> {
                    Locale locale = Locale.forLanguageTag(ticket.getUserLanguage());
                    if(paymentProxy == PaymentProxy.PAYPAL) {
                        sendTicketByEmail(ticket, locale, event, getTicketEmailGenerator(event, reservation, locale));
                    }
                    extensionManager.handleTicketAssignment(ticket);
                });

        }
    }

    PartialTicketTextGenerator getTicketEmailGenerator(Event event, TicketReservation reservation, Locale locale) {
        return (t) -> {
            Map<String, Object> model = new HashMap<>();
            model.put("organization", organizationRepository.getById(event.getOrganizationId()));
            model.put("event", event);
            model.put("ticketReservation", reservation);
            model.put("ticketUrl", ticketUpdateUrl(event, t.getUuid()));
            model.put("ticket", t);
            TicketCategory category = ticketCategoryRepository.getById(t.getCategoryId());
            TemplateResource.fillTicketValidity(event, category, model);
            model.put("googleCalendarUrl", EventUtil.getGoogleCalendarURL(event, category, null));
            return templateManager.renderTemplate(event, TemplateResource.TICKET_EMAIL, model, locale);
        };
    }

    @Transactional
    void cleanupExpiredReservations(Date expirationDate) {
        List<String> expiredReservationIds = ticketReservationRepository.findExpiredReservation(expirationDate);
        if(expiredReservationIds.isEmpty()) {
            return;
        }
        
        specialPriceRepository.resetToFreeAndCleanupForReservation(expiredReservationIds);
        ticketRepository.resetCategoryIdForUnboundedCategories(expiredReservationIds);
        ticketFieldRepository.deleteAllValuesForReservations(expiredReservationIds);
        ticketRepository.freeFromReservation(expiredReservationIds);
        waitingQueueManager.cleanExpiredReservations(expiredReservationIds);

        //
        Map<Integer, List<ReservationIdAndEventId>> reservationIdsByEvent = ticketReservationRepository
            .getReservationIdAndEventId(expiredReservationIds)
            .stream()
            .collect(Collectors.groupingBy(ReservationIdAndEventId::getEventId));
        reservationIdsByEvent.forEach((eventId, reservations) -> {
            Event event = eventRepository.findById(eventId);
            extensionManager.handleReservationsExpiredForEvent(event, reservations.stream().map(ReservationIdAndEventId::getId).collect(Collectors.toList()));
        });
        //
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
        List<String> stuckReservations = ticketReservationRepository.findStuckReservations(expirationDate);
        if(!stuckReservations.isEmpty()) {
            ticketReservationRepository.updateReservationsStatus(stuckReservations, TicketReservationStatus.STUCK.name());

            Map<Integer, List<ReservationIdAndEventId>> reservationsGroupedByEvent = ticketReservationRepository
                .getReservationIdAndEventId(stuckReservations)
                .stream()
                .collect(Collectors.groupingBy(ReservationIdAndEventId::getEventId));

            reservationsGroupedByEvent.forEach((eventId, reservationIds) -> {
                Event event = eventRepository.findById(eventId);
                Organization organization = organizationRepository.getById(event.getOrganizationId());
                notificationManager.sendSimpleEmail(event, organization.getEmail(),
                    STUCK_TICKETS_SUBJECT,  () -> String.format(STUCK_TICKETS_MSG, event.getShortName()));

                extensionManager.handleStuckReservations(event, reservationIds.stream().map(ReservationIdAndEventId::getId).collect(toList()));
            });
        }
    }

    private static TotalPrice totalReservationCostWithVAT(PromoCodeDiscount promoCodeDiscount,
                                                          Event event,
                                                          PriceContainer.VatStatus reservationVatStatus,
                                                          List<Ticket> tickets,
                                                          Stream<Pair<AdditionalService, List<AdditionalServiceItem>>> additionalServiceItems) {

        List<TicketPriceContainer> ticketPrices = tickets.stream().map(t -> TicketPriceContainer.from(t, reservationVatStatus, event, promoCodeDiscount)).collect(toList());
        BigDecimal totalVAT = ticketPrices.stream().map(TicketPriceContainer::getVAT).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDiscount = ticketPrices.stream().map(TicketPriceContainer::getAppliedDiscount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNET = ticketPrices.stream().map(TicketPriceContainer::getFinalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        int discountedTickets = (int) ticketPrices.stream().filter(t -> t.getAppliedDiscount().compareTo(BigDecimal.ZERO) > 0).count();
        int discountAppliedCount = discountedTickets <= 1 || promoCodeDiscount.getDiscountType() == DiscountType.FIXED_AMOUNT ? discountedTickets : 1;

        List<AdditionalServiceItemPriceContainer> asPrices = additionalServiceItems
            .flatMap(generateASIPriceContainers(event, null))
            .collect(toList());

        BigDecimal asTotalVAT = asPrices.stream().map(AdditionalServiceItemPriceContainer::getVAT).reduce(BigDecimal.ZERO, BigDecimal::add);
        //FIXME discount is not applied to donations, as it wouldn't make sense. Must be implemented for #111
        BigDecimal asTotalNET = asPrices.stream().map(AdditionalServiceItemPriceContainer::getFinalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TotalPrice(unitToCents(totalNET.add(asTotalNET)), unitToCents(totalVAT.add(asTotalVAT)), -(MonetaryUtil.unitToCents(totalDiscount)), discountAppliedCount);
    }

    private static Function<Pair<AdditionalService, List<AdditionalServiceItem>>, Stream<? extends AdditionalServiceItemPriceContainer>> generateASIPriceContainers(Event event, PromoCodeDiscount discount) {
        return p -> p.getValue().stream().map(asi -> AdditionalServiceItemPriceContainer.from(asi, p.getKey(), event, discount));
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

        return totalReservationCostWithVAT(promoCodeDiscount.orElse(null), event, reservation.getVatStatus(), tickets, collectAdditionalServiceItems(reservationId, event));
    }

    private String formatPromoCode(PromoCodeDiscount promoCodeDiscount, List<Ticket> tickets) {

        List<Ticket> filteredTickets = tickets.stream().filter(ticket -> promoCodeDiscount.getCategories().contains(ticket.getCategoryId())).collect(toList());

        if (promoCodeDiscount.getCategories().isEmpty() || filteredTickets.isEmpty()) {
            return promoCodeDiscount.getPromoCode();
        }

        String formattedDiscountedCategories = filteredTickets.stream()
            .map(Ticket::getCategoryId)
            .collect(toSet())
            .stream()
            .map(categoryId -> ticketCategoryRepository.getByIdAndActive(categoryId, promoCodeDiscount.getEventId()).getName())
            .collect(Collectors.joining(", ", "(", ")"));


        return promoCodeDiscount.getPromoCode() + " " + formattedDiscountedCategories;
    }

    public OrderSummary orderSummaryForReservationId(String reservationId, Event event, Locale locale) {
        TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
        TotalPrice reservationCost = totalReservationCostWithVAT(reservationId);
        PromoCodeDiscount discount = Optional.ofNullable(reservation.getPromoCodeDiscountId()).map(promoCodeDiscountRepository::findById).orElse(null);
        //
        boolean free = reservationCost.getPriceWithVAT() == 0;
        String vat = getVAT(event).orElse(null);
        String refundedAmount = null;

        boolean hasRefund = auditingRepository.countAuditsOfTypeForReservation(reservationId, Audit.EventType.REFUND) > 0;

        if(hasRefund) {
            refundedAmount = paymentManager.getInfo(reservation, event).getPaymentInformation().getRefundedAmount();
        }

        return new OrderSummary(reservationCost,
                extractSummary(reservationId, reservation.getVatStatus(), event, locale, discount, reservationCost), free,
                formatCents(reservationCost.getPriceWithVAT()), formatCents(reservationCost.getVAT()),
                reservation.getStatus() == TicketReservationStatus.OFFLINE_PAYMENT,
                reservation.getPaymentMethod() == PaymentProxy.ON_SITE, vat, reservation.getVatStatus(), refundedAmount);
    }
    
    List<SummaryRow> extractSummary(String reservationId, PriceContainer.VatStatus reservationVatStatus,
                                    Event event, Locale locale, PromoCodeDiscount promoCodeDiscount, TotalPrice reservationCost) {
        List<SummaryRow> summary = new ArrayList<>();
        List<TicketPriceContainer> tickets = ticketRepository.findTicketsInReservation(reservationId).stream()
            .map(t -> TicketPriceContainer.from(t, reservationVatStatus, event, promoCodeDiscount)).collect(toList());
        tickets.stream()
            .collect(Collectors.groupingBy(TicketPriceContainer::getCategoryId))
            .forEach((categoryId, ticketsByCategory) -> {
                final int subTotal = ticketsByCategory.stream().mapToInt(TicketPriceContainer::getSummarySrcPriceCts).sum();
                final int subTotalBeforeVat = ticketsByCategory.stream().mapToInt(TicketPriceContainer::getSummaryPriceBeforeVatCts).sum();
                TicketPriceContainer firstTicket = ticketsByCategory.get(0);
                final int ticketPriceCts = firstTicket.getSummarySrcPriceCts();
                final int priceBeforeVat = firstTicket.getSummaryPriceBeforeVatCts();
                String categoryName = ticketCategoryRepository.getByIdAndActive(categoryId, event.getId()).getName();
                summary.add(new SummaryRow(categoryName, formatCents(ticketPriceCts), formatCents(priceBeforeVat), ticketsByCategory.size(), formatCents(subTotal), formatCents(subTotalBeforeVat), subTotal, SummaryRow.SummaryType.TICKET));
            });

        summary.addAll(collectAdditionalServiceItems(reservationId, event)
            .map(entry -> {
                String language = locale.getLanguage();
                AdditionalServiceText title = additionalServiceTextRepository.findBestMatchByLocaleAndType(entry.getKey().getId(), language, AdditionalServiceText.TextType.TITLE);
                if(!title.getLocale().equals(language) || title.getId() == -1) {
                    log.debug("additional service {}: title not found for locale {}", title.getAdditionalServiceId(), language);
                }
                List<AdditionalServiceItemPriceContainer> prices = generateASIPriceContainers(event, null).apply(entry).collect(toList());
                AdditionalServiceItemPriceContainer first = prices.get(0);
                final int subtotal = prices.stream().mapToInt(AdditionalServiceItemPriceContainer::getSrcPriceCts).sum();
                final int subtotalBeforeVat = prices.stream().mapToInt(AdditionalServiceItemPriceContainer::getSummaryPriceBeforeVatCts).sum();
                return new SummaryRow(title.getValue(), formatCents(first.getSrcPriceCts()), formatCents(first.getSummaryPriceBeforeVatCts()), prices.size(), formatCents(subtotal), formatCents(subtotalBeforeVat), subtotal, SummaryRow.SummaryType.ADDITIONAL_SERVICE);
            }).collect(Collectors.toList()));

        Optional.ofNullable(promoCodeDiscount).ifPresent(promo -> {
            String formattedSingleAmount = "-" + (promo.getDiscountType() == DiscountType.FIXED_AMOUNT ? formatCents(promo.getDiscountAmount()) : (promo.getDiscountAmount()+"%"));
            summary.add(new SummaryRow(formatPromoCode(promo, ticketRepository.findTicketsInReservation(reservationId)),
                formattedSingleAmount,
                formattedSingleAmount,
                reservationCost.getDiscountAppliedCount(),
                formatCents(reservationCost.getDiscount()), formatCents(reservationCost.getDiscount()), reservationCost.getDiscount(), SummaryRow.SummaryType.PROMOTION_CODE));
        });
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

    String reservationUrl(String reservationId) {
        return reservationUrl(reservationId, eventRepository.findByReservationId(reservationId));
    }

    public String reservationUrl(String reservationId, Event event) {
        TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
        return StringUtils.removeEnd(configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.BASE_URL)), "/")
                + "/event/" + event.getShortName() + "/reservation/" + reservationId + "?lang="+reservation.getUserLanguage();
    }

    String ticketUrl(Event event, String ticketId) {
        Ticket ticket = ticketRepository.findByUUID(ticketId);
        return StringUtils.removeEnd(configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.BASE_URL)), "/")
                + "/event/" + event.getShortName() + "/ticket/" + ticketId + "?lang=" + ticket.getUserLanguage();
    }

    public String ticketUpdateUrl(Event event, String ticketId) {
        Ticket ticket = ticketRepository.findByUUID(ticketId);
        return StringUtils.removeEnd(configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.BASE_URL)), "/")
            + "/event/" + event.getShortName() + "/ticket/" + ticketId + "/update?lang="+ticket.getUserLanguage();
    }

    public int maxAmountOfTicketsForCategory(int organizationId, int eventId, int ticketCategoryId) {
        return configurationManager.getIntConfigValue(Configuration.from(organizationId, eventId, ticketCategoryId, ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), 5);
    }
    
    public Optional<TicketReservation> findById(String reservationId) {
        return ticketReservationRepository.findOptionalReservationById(reservationId);
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
        specialPriceRepository.resetToFreeAndCleanupForReservation(reservationIdsToRemove);
        ticketRepository.resetCategoryIdForUnboundedCategories(reservationIdsToRemove);
        ticketFieldRepository.deleteAllValuesForReservations(reservationIdsToRemove);
        Event event = eventRepository.findByReservationId(reservationId);
        int updatedAS = additionalServiceItemRepository.updateItemsStatusWithReservationUUID(reservationId, expired ? AdditionalServiceItemStatus.EXPIRED : AdditionalServiceItemStatus.CANCELLED);
        int updatedTickets = ticketRepository.findTicketsInReservation(reservationId).stream().mapToInt(t -> ticketRepository.releaseExpiredTicket(reservationId, event.getId(), t.getId())).sum();
        Validate.isTrue(updatedTickets  + updatedAS > 0, "no items have been updated");
        waitingQueueManager.fireReservationExpired(reservationId);
        deleteReservation(event, reservationId, expired);
        auditingRepository.insert(reservationId, null, event.getId(), expired ? Audit.EventType.CANCEL_RESERVATION_EXPIRED : Audit.EventType.CANCEL_RESERVATION, new Date(), Audit.EntityType.RESERVATION, reservationId);
    }

    private void deleteReservation(Event event, String reservationIdToRemove, boolean expired) {
        //handle removal of ticket
        List<String> wrappedReservationIdToRemove = Collections.singletonList(reservationIdToRemove);
        waitingQueueManager.cleanExpiredReservations(wrappedReservationIdToRemove);
        //
        if(expired) {
            extensionManager.handleReservationsExpiredForEvent(event, wrappedReservationIdToRemove);
        } else {
            extensionManager.handleReservationsCancelledForEvent(event, wrappedReservationIdToRemove);
        }
        //
        int removedReservation = ticketReservationRepository.remove(wrappedReservationIdToRemove);
        Validate.isTrue(removedReservation == 1, "expected exactly one removed reservation, got " + removedReservation);
    }

    public Optional<SpecialPrice> getSpecialPriceByCode(String code) {
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
            return getSpecialPriceByCode(price.getCode());
        } else if(price.getStatus() == Status.PENDING) {
            Optional<Ticket> optionalTicket = ticketRepository.findBySpecialPriceId(price.getId());
            if(optionalTicket.isPresent()) {
                cancelPendingReservation(optionalTicket.get().getTicketsReservationId(), false);
                return getSpecialPriceByCode(price.getCode());
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
                                  Optional<UserDetails> userDetails) {

        Ticket preUpdateTicket = ticketRepository.findByUUID(ticket.getUuid());
        Map<String, String> preUpdateTicketFields = ticketFieldRepository.findAllByTicketId(ticket.getId()).stream().collect(Collectors.toMap(TicketFieldValue::getName, TicketFieldValue::getValue));

        String newEmail = updateTicketOwner.getEmail().trim();
        CustomerName customerName = new CustomerName(updateTicketOwner.getFullName(), updateTicketOwner.getFirstName(), updateTicketOwner.getLastName(), event);
        ticketRepository.updateTicketOwner(ticket.getUuid(), newEmail, customerName.getFullName(), customerName.getFirstName(), customerName.getLastName());

        //
        Locale userLocale = Optional.ofNullable(StringUtils.trimToNull(updateTicketOwner.getUserLanguage())).map(Locale::forLanguageTag).orElse(locale);

        ticketRepository.updateOptionalTicketInfo(ticket.getUuid(), userLocale.getLanguage());
        ticketFieldRepository.updateOrInsert(updateTicketOwner.getAdditional(), ticket.getId(), event.getId());

        Ticket newTicket = ticketRepository.findByUUID(ticket.getUuid());
        if (newTicket.getStatus() == TicketStatus.ACQUIRED
            && (!StringUtils.equalsIgnoreCase(newEmail, ticket.getEmail()) || !StringUtils.equalsIgnoreCase(customerName.getFullName(), ticket.getFullName()))) {
            sendTicketByEmail(newTicket, userLocale, event, confirmationTextBuilder);
        }

        boolean admin = isAdmin(userDetails);

        if (!admin && StringUtils.isNotBlank(ticket.getEmail()) && !StringUtils.equalsIgnoreCase(newEmail, ticket.getEmail()) && ticket.getStatus() == TicketStatus.ACQUIRED) {
            Locale oldUserLocale = Locale.forLanguageTag(ticket.getUserLanguage());
            String subject = messageSource.getMessage("ticket-has-changed-owner-subject", new Object[] {event.getDisplayName()}, oldUserLocale);
            notificationManager.sendSimpleEmail(event, ticket.getEmail(), subject, () -> ownerChangeTextBuilder.generate(newTicket));
            if(event.getBegin().isBefore(ZonedDateTime.now(event.getZoneId()))) {
                Organization organization = organizationRepository.getById(event.getOrganizationId());
                notificationManager.sendSimpleEmail(event, organization.getEmail(), "WARNING: Ticket has been reassigned after event start", () -> ownerChangeTextBuilder.generate(newTicket));
            }
        }

        if(admin) {
            TicketReservation reservation = findById(ticket.getTicketsReservationId()).orElseThrow(IllegalStateException::new);
            //if the current user is admin, then it would be good to update also the name of the Reservation Owner
            String username = userDetails.get().getUsername();
            log.warn("Reservation {}: forced assignee replacement old: {} new: {}", reservation.getId(), reservation.getFullName(), username);
            ticketReservationRepository.updateAssignee(reservation.getId(), username);
        }
        extensionManager.handleTicketAssignment(newTicket);



        Ticket postUpdateTicket = ticketRepository.findByUUID(ticket.getUuid());
        Map<String, String> postUpdateTicketFields = ticketFieldRepository.findAllByTicketId(ticket.getId()).stream().collect(Collectors.toMap(TicketFieldValue::getName, TicketFieldValue::getValue));

        auditUpdateTicket(preUpdateTicket, preUpdateTicketFields, postUpdateTicket, postUpdateTicketFields, event.getId());
    }

    private void auditUpdateTicket(Ticket preUpdateTicket, Map<String, String> preUpdateTicketFields, Ticket postUpdateTicket, Map<String, String> postUpdateTicketFields, int eventId) {
        DiffNode diffTicket = ObjectDifferBuilder.buildDefault().compare(postUpdateTicket, preUpdateTicket);
        DiffNode diffTicketFields = ObjectDifferBuilder.buildDefault().compare(postUpdateTicketFields, preUpdateTicketFields);
        FieldChangesSaver diffTicketVisitor = new FieldChangesSaver(preUpdateTicket, postUpdateTicket);
        FieldChangesSaver diffTicketFieldsVisitor = new FieldChangesSaver(preUpdateTicketFields, postUpdateTicketFields);
        diffTicket.visit(diffTicketVisitor);
        diffTicketFields.visit(diffTicketFieldsVisitor);

        List<Map<String, Object>> changes = new ArrayList<>(diffTicketVisitor.changes);
        changes.addAll(diffTicketFieldsVisitor.changes);

        auditingRepository.insert(preUpdateTicket.getTicketsReservationId(), null, eventId,
            Audit.EventType.UPDATE_TICKET, new Date(), Audit.EntityType.TICKET, Integer.toString(preUpdateTicket.getId()), changes);
    }


    private static class FieldChangesSaver implements DiffNode.Visitor {

        private final Object preBase;
        private final Object postBase;

        private final List<Map<String, Object>> changes = new ArrayList<>();


        FieldChangesSaver(Object preBase, Object postBase) {
            this.preBase = preBase;
            this.postBase = postBase;
        }

        @Override
        public void node(DiffNode node, Visit visit) {
            if(node.hasChanges() && node.getState() != DiffNode.State.UNTOUCHED && !node.isRootNode()) {
                Object baseValue = node.canonicalGet(preBase);
                Object workingValue = node.canonicalGet(postBase);
                HashMap<String, Object> change = new HashMap<>();
                change.put("propertyName", node.getPath().toString());
                change.put("state", node.getState());
                change.put("oldValue", baseValue);
                change.put("newValue", workingValue);
                changes.add(change);
            }
        }
    }

    private boolean isAdmin(Optional<UserDetails> userDetails) {
        return userDetails.flatMap(u -> u.getAuthorities().stream().map(a -> Role.fromRoleName(a.getAuthority())).filter(Role.ADMIN::equals).findFirst()).isPresent();
    }

    void sendTicketByEmail(Ticket ticket, Locale locale, Event event, PartialTicketTextGenerator confirmationTextBuilder) {
        try {
            TicketReservation reservation = ticketReservationRepository.findReservationById(ticket.getTicketsReservationId());
            TicketCategory ticketCategory = ticketCategoryRepository.getByIdAndActive(ticket.getCategoryId(), event.getId());
            notificationManager.sendTicketByEmail(ticket, event, locale, confirmationTextBuilder, reservation, ticketCategory);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<Triple<Event, TicketReservation, Ticket>> fetchComplete(String eventName, String ticketIdentifier) {
        return ticketRepository.findOptionalByUUID(ticketIdentifier)
            .flatMap(ticket -> from(eventName, ticket.getTicketsReservationId(), ticketIdentifier)
                .flatMap((triple) -> {
                    if(triple.getMiddle().getStatus() == TicketReservationStatus.COMPLETE) {
                        return Optional.of(triple);
                    } else {
                        return Optional.empty();
                    }
            }));
    }

    /**
     * Return a fully present triple only if the values are present (obviously) and the the reservation has a COMPLETE status and the ticket is considered assigned.
     *
     * @param eventName
     * @param ticketIdentifier
     * @return
     */
    public Optional<Triple<Event, TicketReservation, Ticket>> fetchCompleteAndAssigned(String eventName, String ticketIdentifier) {
        return fetchComplete(eventName, ticketIdentifier).flatMap((t) -> {
            if (t.getRight().getAssigned()) {
                return Optional.of(t);
            } else {
                return Optional.empty();
            }
        });
    }

    void sendReminderForOfflinePayments() {
        Date expiration = truncate(addHours(new Date(), configurationManager.getIntConfigValue(Configuration.getSystemConfiguration(OFFLINE_REMINDER_HOURS), 24)), Calendar.DATE);
        ticketReservationRepository.findAllOfflinePaymentReservationForNotification(expiration).stream()
                .map(reservation -> {
                    Optional<Ticket> ticket = ticketRepository.findFirstTicketInReservation(reservation.getId());
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
                    Locale locale = p.getRight();
                    ticketReservationRepository.flagAsOfflinePaymentReminderSent(reservation.getId());
                    notificationManager.sendSimpleEmail(event, reservation.getEmail(), messageSource.getMessage("reservation.reminder.mail.subject", new Object[]{getShortReservationID(event, reservation.getId())}, locale), () -> templateManager.renderTemplate(event, TemplateResource.REMINDER_EMAIL, model, locale));
                });
    }

    //called each hour
    void sendReminderForOfflinePaymentsToEventManagers() {
        eventRepository.findAllActives(ZonedDateTime.now(Clock.systemUTC())).stream().filter(event -> {
            ZonedDateTime dateTimeForEvent = ZonedDateTime.now(event.getZoneId());
            return dateTimeForEvent.truncatedTo(ChronoUnit.HOURS).getHour() == 5; //only for the events at 5:00 local time
        }).forEachOrdered(event -> {
            ZonedDateTime dateTimeForEvent = ZonedDateTime.now(event.getZoneId()).truncatedTo(ChronoUnit.DAYS).plusDays(1);
            List<TicketReservationInfo> reservations = ticketReservationRepository.findAllOfflinePaymentReservationWithExpirationBefore(dateTimeForEvent, event.getId());
            log.info("for event {} there are {} pending offline payments to handle", event.getId(), reservations.size());
            if(!reservations.isEmpty()) {
                Organization organization = organizationRepository.getById(event.getOrganizationId());
                List<String> cc = notificationManager.getCCForEventOrganizer(event);
                String subject = String.format("There are %d pending offline payments that will expire in event: %s", reservations.size(), event.getDisplayName());
                String baseUrl = configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), BASE_URL));
                Map<String, Object> model = TemplateResource.prepareModelForOfflineReservationExpiringEmailForOrganizer(event, reservations, baseUrl);
                notificationManager.sendSimpleEmail(event, organization.getEmail(), cc, subject, () ->
                    templateManager.renderTemplate(event, TemplateResource.OFFLINE_RESERVATION_EXPIRING_EMAIL_FOR_ORGANIZER, model, Locale.ENGLISH));
                extensionManager.handleOfflineReservationsWillExpire(event, reservations);
            }
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
                .filter(e -> configurationManager.getBooleanConfigValue(Configuration.from(e.getOrganizationId(), e.getId(), OPTIONAL_DATA_REMINDER_ENABLED), true))
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
                        Map<String, Object> model = TemplateResource.prepareModelForReminderTicketAdditionalInfo(organizationRepository.getById(event.getOrganizationId()), event, t, ticketUpdateUrl(event, t.getUuid()));
                        Locale locale = Optional.ofNullable(t.getUserLanguage()).map(Locale::forLanguageTag).orElseGet(() -> findReservationLanguage(t.getTicketsReservationId()));
                        notificationManager.sendSimpleEmail(event, t.getEmail(), messageSource.getMessage("reminder.ticket-additional-info.subject", new Object[]{event.getDisplayName()}, locale), () -> templateManager.renderTemplate(event, TemplateResource.REMINDER_TICKET_ADDITIONAL_INFO, model, locale));
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
                            ticketReservationRepository.updateLatestReminderTimestamp(reservation.getId(), ZonedDateTime.now(eventZoneId));
                            Locale locale = findReservationLanguage(reservation.getId());
                            notificationManager.sendSimpleEmail(event, reservation.getEmail(), messageSource.getMessage("reminder.ticket-not-assigned.subject", new Object[]{event.getDisplayName()}, locale), () -> templateManager.renderTemplate(event, TemplateResource.REMINDER_TICKETS_ASSIGNMENT_EMAIL, model, locale));
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
            return ticketRepository.countFreeTickets(event.getId(), category.getId());
        }
        return ticketRepository.countFreeTicketsForUnbounded(event.getId());
    }

    public void releaseTicket(Event event, TicketReservation ticketReservation, final Ticket ticket) {
        TicketCategory category = ticketCategoryRepository.getByIdAndActive(ticket.getCategoryId(), event.getId());
        if(!CategoryEvaluator.isTicketCancellationAvailable(ticketCategoryRepository, ticket)) {
            throw new IllegalStateException("Cannot release reserved tickets");
        }
        String reservationId = ticketReservation.getId();
        //#365 - reset UUID when releasing a ticket
        int result = ticketRepository.releaseTicket(reservationId, UUID.randomUUID().toString(), event.getId(), ticket.getId());
        Validate.isTrue(result == 1, String.format("Expected 1 row to be updated, got %d", result));
        if(category.isAccessRestricted() || !category.isBounded()) {
            ticketRepository.unbindTicketsFromCategory(event.getId(), category.getId(), singletonList(ticket.getId()));
        }
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        Map<String, Object> model = TemplateResource.buildModelForTicketHasBeenCancelled(organization, event, ticket);
        Locale locale = Locale.forLanguageTag(Optional.ofNullable(ticket.getUserLanguage()).orElse("en"));
        notificationManager.sendSimpleEmail(event, ticket.getEmail(), messageSource.getMessage("email-ticket-released.subject",
                new Object[]{event.getDisplayName()}, locale),
                () -> templateManager.renderTemplate(event, TemplateResource.TICKET_HAS_BEEN_CANCELLED, model, locale));

        String ticketCategoryDescription = ticketCategoryDescriptionRepository.findByTicketCategoryIdAndLocale(category.getId(), ticket.getUserLanguage()).orElse("");

        List<AdditionalServiceItem> additionalServiceItems = additionalServiceItemRepository.findByReservationUuid(reservationId);
        Map<String, Object> adminModel = TemplateResource.buildModelForTicketHasBeenCancelledAdmin(organization, event, ticket,
            ticketCategoryDescription, additionalServiceItems, asi -> additionalServiceTextRepository.findByLocaleAndType(asi.getAdditionalServiceId(), locale.getLanguage(), AdditionalServiceText.TextType.TITLE));
        notificationManager.sendSimpleEmail(event, organization.getEmail(), messageSource.getMessage("email-ticket-released.admin.subject", new Object[]{ticket.getId(), event.getDisplayName()}, locale),
            () -> templateManager.renderTemplate(event, TemplateResource.TICKET_HAS_BEEN_CANCELLED_ADMIN, adminModel, locale));

        int deletedValues = ticketFieldRepository.deleteAllValuesForTicket(ticket.getId());
        log.debug("deleting {} field values for ticket {}", deletedValues, ticket.getId());

        auditingRepository.insert(reservationId, null, event.getId(), Audit.EventType.CANCEL_TICKET, new Date(), Audit.EntityType.TICKET, Integer.toString(ticket.getId()));

        if(ticketRepository.countTicketsInReservation(reservationId) == 0 && !transactionRepository.loadOptionalByReservationId(reservationId).isPresent()) {
            deleteReservation(event, reservationId, false);
            auditingRepository.insert(reservationId, null, event.getId(), Audit.EventType.CANCEL_RESERVATION, new Date(), Audit.EntityType.RESERVATION, reservationId);
        } else {
            extensionManager.handleTicketCancelledForEvent(event, Collections.singletonList(ticket.getUuid()));
        }
    }

    public int getReservationTimeout(Event event) {
        return configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), RESERVATION_TIMEOUT), 25);
    }

    public void validateAndConfirmOfflinePayment(String reservationId, Event event, BigDecimal paidAmount, String username) {
        TicketReservation reservation = findByPartialID(reservationId);
        Optional<OrderSummary> optionalOrderSummary = optionally(() -> orderSummaryForReservationId(reservation.getId(), event, Locale.forLanguageTag(reservation.getUserLanguage())));
        Validate.isTrue(optionalOrderSummary.isPresent(), "Reservation not found");
        OrderSummary orderSummary = optionalOrderSummary.get();
        Validate.isTrue(MonetaryUtil.centsToUnit(orderSummary.getOriginalTotalPrice().getPriceWithVAT()).compareTo(paidAmount) == 0, "paid price differs from due price");
        confirmOfflinePayment(event, reservation.getId(), username);
    }

    private List<Pair<TicketReservation, OrderSummary>> fetchWaitingForPayment(int eventId, Event event, Locale locale) {
        return ticketReservationRepository.findAllReservationsWaitingForPaymentInEventId(eventId).stream()
            .map(id -> Pair.of(ticketReservationRepository.findReservationById(id), orderSummaryForReservationId(id, event, locale)))
            .collect(Collectors.toList());
    }

    public List<Pair<TicketReservation, OrderSummary>> getPendingPayments(Event event) {
        return fetchWaitingForPayment(event.getId(), event, Locale.ENGLISH);
    }

    public Integer getPendingPaymentsCount(int eventId) {
        return ticketReservationRepository.findAllReservationsWaitingForPaymentCountInEventId(eventId);
    }

    public List<TicketReservation> findAllInvoices(int eventId) {
        return ticketReservationRepository.findAllReservationsWithInvoices(eventId);
    }

    public Integer countInvoices(int eventId) {
        return ticketReservationRepository.countInvoices(eventId);
    }


    public boolean hasPaidSupplements(String reservationId) {
        return additionalServiceItemRepository.hasPaidSupplements(reservationId);
    }

    void revertTicketsToFreeIfAccessRestricted(int eventId) {
        List<Integer> restrictedCategories = ticketCategoryRepository.findByEventId(eventId).stream()
            .filter(TicketCategory::isAccessRestricted)
            .map(TicketCategory::getId)
            .collect(toList());
        if(!restrictedCategories.isEmpty()) {
            int count = ticketRepository.revertToFreeForRestrictedCategories(eventId, restrictedCategories);
            if(count > 0) {
                log.debug("reverted {} tickets for categories {}", count, restrictedCategories);
            }
        }
    }
}
