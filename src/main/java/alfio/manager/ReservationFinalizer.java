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

import alfio.manager.payment.PaymentSpecification;
import alfio.manager.support.FeeCalculator;
import alfio.manager.support.IncompatibleStateException;
import alfio.manager.support.RetryFinalizeReservation;
import alfio.manager.support.reservation.OrderSummaryGenerator;
import alfio.manager.support.reservation.ReservationAuditingHelper;
import alfio.manager.support.reservation.ReservationCostCalculator;
import alfio.manager.support.reservation.ReservationEmailContentHelper;
import alfio.manager.system.AdminJobManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.metadata.TicketMetadata;
import alfio.model.metadata.TicketMetadataContainer;
import alfio.model.modification.TransactionMetadataModification;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.support.UserIdAndOrganizationId;
import alfio.model.system.command.FinalizeReservation;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.Transaction;
import alfio.repository.*;
import alfio.repository.system.AdminJobQueueRepository;
import alfio.repository.user.UserRepository;
import alfio.util.ClockProvider;
import alfio.util.Json;
import alfio.util.LocaleUtil;
import alfio.util.ReservationUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.function.Function;

import static alfio.manager.system.AdminJobExecutor.JobName.RETRY_RESERVATION_CONFIRMATION;
import static alfio.model.Audit.EventType.SUBSCRIPTION_ACQUIRED;
import static alfio.model.TicketReservation.TicketReservationStatus.*;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.ReservationUtil.hasPrivacyPolicy;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.toMap;

@Component
public class ReservationFinalizer {
    private static final Logger log = LoggerFactory.getLogger(ReservationFinalizer.class);
    private final TransactionTemplate transactionTemplate;
    private final TicketReservationRepository ticketReservationRepository;
    private final UserRepository userRepository;
    private final ExtensionManager extensionManager;
    private final AuditingRepository auditingRepository;
    private final ClockProvider clockProvider;
    private final ConfigurationManager configurationManager;
    private final SubscriptionRepository subscriptionRepository;
    private final ReservationAuditingHelper auditingHelper;
    private final TicketRepository ticketRepository;
    private final ReservationEmailContentHelper reservationOperationHelper;
    private final SpecialPriceRepository specialPriceRepository;
    private final WaitingQueueManager waitingQueueManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final ReservationCostCalculator reservationCostCalculator;
    private final BillingDocumentManager billingDocumentManager;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final OrderSummaryGenerator orderSummaryGenerator;
    private final ReservationEmailContentHelper reservationHelper;
    private final TransactionRepository transactionRepository;
    private final AdminJobQueueRepository adminJobQueueRepository;
    private final PurchaseContextManager purchaseContextManager;
    private final Json json;


    public ReservationFinalizer(PlatformTransactionManager transactionManager,
                                TicketReservationRepository ticketReservationRepository,
                                UserRepository userRepository,
                                ExtensionManager extensionManager,
                                AuditingRepository auditingRepository,
                                ClockProvider clockProvider,
                                ConfigurationManager configurationManager,
                                SubscriptionRepository subscriptionRepository,
                                TicketRepository ticketRepository,
                                ReservationEmailContentHelper reservationEmailContentHelper,
                                SpecialPriceRepository specialPriceRepository,
                                WaitingQueueManager waitingQueueManager,
                                TicketCategoryRepository ticketCategoryRepository,
                                ReservationCostCalculator reservationCostCalculator,
                                BillingDocumentManager billingDocumentManager,
                                AdditionalServiceItemRepository additionalServiceItemRepository,
                                OrderSummaryGenerator orderSummaryGenerator,
                                TransactionRepository transactionRepository,
                                AdminJobQueueRepository adminJobQueueRepository,
                                PurchaseContextManager purchaseContextManager,
                                Json json) {
        this.ticketReservationRepository = ticketReservationRepository;
        this.userRepository = userRepository;
        this.extensionManager = extensionManager;
        this.auditingRepository = auditingRepository;
        this.clockProvider = clockProvider;
        this.configurationManager = configurationManager;
        this.subscriptionRepository = subscriptionRepository;
        this.ticketRepository = ticketRepository;
        this.reservationOperationHelper = reservationEmailContentHelper;
        this.specialPriceRepository = specialPriceRepository;
        this.waitingQueueManager = waitingQueueManager;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.reservationCostCalculator = reservationCostCalculator;
        this.billingDocumentManager = billingDocumentManager;
        this.additionalServiceItemRepository = additionalServiceItemRepository;
        this.transactionRepository = transactionRepository;
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate = new TransactionTemplate(transactionManager, definition);
        this.orderSummaryGenerator = orderSummaryGenerator;
        this.reservationHelper = reservationEmailContentHelper;
        this.auditingHelper = new ReservationAuditingHelper(auditingRepository);
        this.adminJobQueueRepository = adminJobQueueRepository;
        this.purchaseContextManager = purchaseContextManager;
        this.json = json;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void finalizeCommandReceived(FinalizeReservation finalizeReservation) {
        transactionTemplate.executeWithoutResult(ctx -> processFinalizeReservation(finalizeReservation, ctx, true));
    }

    public void retryFinalizeReservation(RetryFinalizeReservation retryFinalizeReservation) {
        var purchaseContextAndReservation = purchaseContextManager.getReservationWithPurchaseContext(retryFinalizeReservation.getReservationId()).orElseThrow();
        var reservation = purchaseContextAndReservation.getRight();
        var purchaseContext = purchaseContextAndReservation.getLeft();
        var costResult = reservationCostCalculator.totalReservationCostWithVAT(reservation);
        var totalPrice = costResult.getLeft();
        var orderSummary = orderSummaryGenerator.orderSummaryForReservation(reservation, purchaseContext);
        var paymentSpecification = new PaymentSpecification(reservation, totalPrice, purchaseContext, null, orderSummary, retryFinalizeReservation.isTcAccepted(), retryFinalizeReservation.isPrivacyPolicyAccepted());
        transactionTemplate.executeWithoutResult(ctx -> processFinalizeReservation(new FinalizeReservation(paymentSpecification, retryFinalizeReservation.getPaymentProxy(), retryFinalizeReservation.isSendReservationConfirmationEmail(), retryFinalizeReservation.isSendTickets(), retryFinalizeReservation.getUsername(), retryFinalizeReservation.getOriginalStatus()), ctx, false));
    }

    private void processFinalizeReservation(FinalizeReservation finalizeReservation, TransactionStatus ctx, boolean scheduleRetryOnError) {
        Object savepoint = ctx.createSavepoint();
        var spec = finalizeReservation.getPaymentSpecification();
        try {
            var reservation = ticketReservationRepository.findReservationById(spec.getReservationId());
            var totalPrice = reservationCostCalculator.totalReservationCostWithVAT(reservation);
            var metadata = ticketReservationRepository.getMetadata(reservation.getId());
            // generate invoice number
            if (reservation.getStatus() != COMPLETE && StringUtils.isBlank(reservation.getInvoiceNumber()) && !metadata.isReadyForConfirmation()) {
                boolean traceEnabled = log.isTraceEnabled();
                if (traceEnabled) {
                    log.trace("Generating invoice number for reservation {}", reservation.getId());
                }
                var invoiceNumberOptional = billingDocumentManager.generateInvoiceNumber(spec, totalPrice.getKey());
                invoiceNumberOptional.ifPresent(s -> setInvoiceNumber(spec.getReservationId(), s));
            }
            // no exceptions here. We can save the progress
            ticketReservationRepository.setMetadata(reservation.getId(), metadata.withReadyForConfirmation(true));
            ctx.releaseSavepoint(savepoint);
            savepoint = ctx.createSavepoint();

            // complete reservation
            completeReservation(finalizeReservation);
        } catch(Exception e) {
            ctx.rollbackToSavepoint(savepoint);
            if (!scheduleRetryOnError) {
                throw e;
            }
            boolean scheduled = AdminJobManager.executionScheduler(
                RETRY_RESERVATION_CONFIRMATION,
                Map.of("payload", json.asJsonString(RetryFinalizeReservation.fromFinalizeReservation(finalizeReservation))),
                ZonedDateTime.now(clockProvider.getClock()).plusSeconds(2L)
            ).apply(adminJobQueueRepository);
            if(!scheduled) {
                log.warn("Cannot schedule retry for reservation {}", spec.getReservationId());
                // throw exception only if we can't schedule the retry
                throw e;
            } else {
                log.warn("Error while confirming reservation "+ spec.getReservationId() + ". Will retry in 2s", e);
            }
        } finally {
            ctx.releaseSavepoint(savepoint);
        }
    }


    private void setInvoiceNumber(String reservationId, String invoiceNumber) {
        if (log.isTraceEnabled()) {
            log.trace("Set invoice number {} for reservation {}", invoiceNumber, reservationId);
        }
        ticketReservationRepository.setInvoiceNumber(reservationId, invoiceNumber);
    }

    private void completeReservation(FinalizeReservation finalizeReservation) {
        var spec = finalizeReservation.getPaymentSpecification();
        var paymentProxy = finalizeReservation.getPaymentProxy();
        var username = finalizeReservation.getUsername();
        var reservationId = spec.getReservationId();
        var purchaseContext = spec.getPurchaseContext();
        final TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
        if (reservation.getStatus() != FINALIZING && reservation.getStatus() != OFFLINE_FINALIZING) {
            throw new IncompatibleStateException("Status " + reservation.getStatus() + " is not compatible with finalization.");
        }
        var metadata = ticketReservationRepository.getMetadata(reservationId);
        if (!metadata.isReadyForConfirmation()) {
            throw new IncompatibleStateException("Reservation is not ready to be confirmed");
        }
        // retrieve reservation owner if username is null
        Integer userId;
        if(username != null) {
            userId = userRepository.getByUsername(username).getId();
        } else {
            userId = ticketReservationRepository.getReservationOwnerAndOrganizationId(reservationId)
                .map(UserIdAndOrganizationId::getUserId)
                .orElse(null);
        }
        ticketReservationRepository.setMetadata(reservationId, metadata.withFinalized(true));
        Locale locale = LocaleUtil.forLanguageTag(reservation.getUserLanguage());
        List<Ticket> tickets = null;
        if(paymentProxy != PaymentProxy.OFFLINE) {
            ticketReservationRepository.updateReservationStatus(reservationId, COMPLETE.name());
            tickets = acquireItems(paymentProxy, reservationId, spec.getEmail(), spec.getCustomerName(), spec.getLocale().getLanguage(), spec.getBillingAddress(), spec.getCustomerReference(), spec.getPurchaseContext(), finalizeReservation.isSendTickets());
            extensionManager.handleReservationConfirmation(reservation, ticketReservationRepository.getBillingDetailsForReservation(reservationId), spec.getPurchaseContext());
        } else {
            // if paymentProxy is offline, we set the appropriate status to wait for payment
            ticketReservationRepository.updateReservationStatus(reservationId, finalizeReservation.getOriginalStatus().name());
        }

        Date eventTime = new Date();
        auditingRepository.insert(reservationId, userId, purchaseContext, Audit.EventType.RESERVATION_COMPLETE, eventTime, Audit.EntityType.RESERVATION, reservationId);
        ticketReservationRepository.updateRegistrationTimestamp(reservationId, ZonedDateTime.now(clockProvider.withZone(spec.getPurchaseContext().getZoneId())));
        if(spec.isTcAccepted()) {
            auditingRepository.insert(reservationId, userId, purchaseContext, Audit.EventType.TERMS_CONDITION_ACCEPTED, eventTime, Audit.EntityType.RESERVATION, reservationId, singletonList(singletonMap("termsAndConditionsUrl", spec.getPurchaseContext().getTermsAndConditionsUrl())));
        }

        if(hasPrivacyPolicy(spec.getPurchaseContext()) && spec.isPrivacyAccepted()) {
            auditingRepository.insert(reservationId, userId, purchaseContext, Audit.EventType.PRIVACY_POLICY_ACCEPTED, eventTime, Audit.EntityType.RESERVATION, reservationId, singletonList(singletonMap("privacyPolicyUrl", spec.getPurchaseContext().getPrivacyPolicyUrl())));
        }

        if(finalizeReservation.isSendReservationConfirmationEmail()) {
            TicketReservation updatedReservation = ticketReservationRepository.findReservationById(reservationId);
            sendConfirmationEmailIfNecessary(updatedReservation, tickets, purchaseContext, locale, username);
            reservationOperationHelper.sendReservationCompleteEmailToOrganizer(spec.getPurchaseContext(), updatedReservation, locale, username);
        }
    }

    public void sendConfirmationEmailIfNecessary(TicketReservation ticketReservation,
                                          List<Ticket> tickets,
                                          PurchaseContext purchaseContext,
                                          Locale locale,
                                          String username) {
        if (!ticketReservationRepository.getMetadata(ticketReservation.getId()).isFinalized()) {
            throw new IncompatibleStateException("Reservation confirmed but not yet finalized");
        }
        if(purchaseContext.ofType(PurchaseContext.PurchaseContextType.event)) {
            var config = configurationManager.getFor(List.of(SEND_RESERVATION_EMAIL_IF_NECESSARY, SEND_TICKETS_AUTOMATICALLY), purchaseContext.getConfigurationLevel());
            if(ticketReservation.getSrcPriceCts() > 0
                || CollectionUtils.isEmpty(tickets) || tickets.size() > 1
                || !tickets.get(0).getEmail().equals(ticketReservation.getEmail())
                || !config.get(SEND_RESERVATION_EMAIL_IF_NECESSARY).getValueAsBooleanOrDefault()
                || !config.get(SEND_TICKETS_AUTOMATICALLY).getValueAsBooleanOrDefault()
            ) {
                reservationOperationHelper.sendConfirmationEmail(purchaseContext, ticketReservation, locale, username);
            }
        } else {
            reservationOperationHelper.sendConfirmationEmail(purchaseContext, ticketReservation, locale, username);
        }
    }

    public void acquireSpecialPriceTokens(String reservationId) {
        specialPriceRepository.updateStatusForReservation(singletonList(reservationId), SpecialPrice.Status.TAKEN.toString());
    }

    private List<Ticket> acquireItems(PaymentProxy paymentProxy, String reservationId, String email, CustomerName customerName,
                                     String userLanguage, String billingAddress, String customerReference, PurchaseContext purchaseContext, boolean sendTickets) {
        switch (purchaseContext.getType()) {
            case event -> acquireEventTickets(paymentProxy, reservationId, purchaseContext, purchaseContext.event().orElseThrow());
            case subscription -> acquireSubscription(paymentProxy, reservationId, purchaseContext, customerName, email);
            default -> throw new IllegalStateException("not supported purchase context");
        }

        acquireSpecialPriceTokens(reservationId);
        ZonedDateTime timestamp = ZonedDateTime.now(clockProvider.getClock());
        int updatedReservation = ticketReservationRepository.updateTicketReservation(reservationId, COMPLETE.toString(), email,
            customerName.getFullName(), customerName.getFirstName(), customerName.getLastName(), userLanguage, billingAddress, timestamp, paymentProxy.toString(), customerReference);

        Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got " + updatedReservation);

        waitingQueueManager.fireReservationConfirmed(reservationId);
        //we must notify the plugins about ticket assignment and send them by email
        TicketReservation reservation = findById(reservationId).orElseThrow(IllegalStateException::new);
        List<Ticket> assignedTickets = findTicketsInReservation(reservationId);
        assignedTickets.stream()
            .filter(ticket -> StringUtils.isNotBlank(ticket.getFullName()) || StringUtils.isNotBlank(ticket.getFirstName()) || StringUtils.isNotBlank(ticket.getEmail()))
            .forEach(ticket -> {
                var event = purchaseContext.event().orElseThrow();
                Locale locale = LocaleUtil.forLanguageTag(ticket.getUserLanguage());
                var additionalInfo = reservationOperationHelper.retrieveAttendeeAdditionalInfoForTicket(ticket);
                if((paymentProxy != PaymentProxy.ADMIN || sendTickets) && configurationManager.getFor(SEND_TICKETS_AUTOMATICALLY, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault()) {
                    reservationOperationHelper.sendTicketByEmail(ticket, locale, event, reservationHelper.getTicketEmailGenerator(event, reservation, locale, additionalInfo));
                }
                extensionManager.handleTicketAssignment(ticket, ticketCategoryRepository.getById(ticket.getCategoryId()), additionalInfo);
            });
        return assignedTickets;
    }

    private void acquireSubscription(PaymentProxy paymentProxy, String reservationId, PurchaseContext purchaseContext, CustomerName customerName, String email) {
        var status = paymentProxy.isDeskPaymentRequired() ? AllocationStatus.TO_BE_PAID : AllocationStatus.ACQUIRED;
        var subscriptionDescriptor = (SubscriptionDescriptor) purchaseContext;
        ZonedDateTime validityFrom = null;
        ZonedDateTime validityTo = null;
        var confirmationTimestamp = subscriptionDescriptor.now(clockProvider);
        if(subscriptionDescriptor.getValidityFrom() != null) {
            validityFrom = subscriptionDescriptor.getValidityFrom();
            validityTo   = subscriptionDescriptor.getValidityTo();
        } else if(subscriptionDescriptor.getValidityUnits() != null) {
            validityFrom = confirmationTimestamp;
            var temporalUnit = requireNonNullElse(subscriptionDescriptor.getValidityTimeUnit(), SubscriptionDescriptor.SubscriptionTimeUnit.DAYS).getTemporalUnit();
            validityTo = confirmationTimestamp.plus(subscriptionDescriptor.getValidityUnits(), temporalUnit)
                .with(ChronoField.HOUR_OF_DAY, 23)
                .with(ChronoField.MINUTE_OF_HOUR, 59)
                .with(ChronoField.SECOND_OF_MINUTE, 59);
        }
        var subscription = subscriptionRepository.findSubscriptionsByReservationId(reservationId).stream().findFirst().orElseThrow();
        var updatedSubscriptions = subscriptionRepository.confirmSubscription(reservationId,
            status,
            requireNonNullElse(subscription.getFirstName(), customerName.getFirstName()),
            requireNonNullElse(subscription.getLastName(), customerName.getLastName()),
            requireNonNullElse(subscription.getEmail(), email),
            subscriptionDescriptor.getMaxEntries(),
            validityFrom,
            validityTo,
            confirmationTimestamp,
            subscriptionDescriptor.getTimeZone());
        Validate.isTrue(updatedSubscriptions > 0, "must have updated at least one subscription");
        subscription = subscriptionRepository.findSubscriptionsByReservationId(reservationId).get(0); // at the moment it's safe because there can be only one subscription per reservation
        var subscriptionId = subscription.getId();
        auditingRepository.insert(reservationId, null, purchaseContext, SUBSCRIPTION_ACQUIRED, new Date(), Audit.EntityType.SUBSCRIPTION, subscriptionId.toString());
        extensionManager.handleSubscriptionAssignmentMetadata(subscription, subscriptionDescriptor, subscriptionRepository.getSubscriptionMetadata(subscriptionId))
            .ifPresent(metadata -> subscriptionRepository.setMetadataForSubscription(subscriptionId, metadata));
    }

    private void acquireEventTickets(PaymentProxy paymentProxy, String reservationId, PurchaseContext purchaseContext, Event event) {
        Ticket.TicketStatus ticketStatus = paymentProxy.isDeskPaymentRequired() ? Ticket.TicketStatus.TO_BE_PAID : Ticket.TicketStatus.ACQUIRED;
        AdditionalServiceItem.AdditionalServiceItemStatus asStatus = paymentProxy.isDeskPaymentRequired() ? AdditionalServiceItem.AdditionalServiceItemStatus.TO_BE_PAID : AdditionalServiceItem.AdditionalServiceItemStatus.ACQUIRED;
        Map<Integer, Ticket> preUpdateTicket = ticketRepository.findTicketsInReservation(reservationId).stream().collect(toMap(Ticket::getId, Function.identity()));
        int updatedTickets = ticketRepository.updateTicketsStatusWithReservationId(reservationId, ticketStatus.toString());
        if(!configurationManager.getFor(ENABLE_TICKET_TRANSFER, purchaseContext.getConfigurationLevel()).getValueAsBooleanOrDefault()) {
            //automatically lock assignment
            int locked = ticketRepository.forbidReassignment(preUpdateTicket.keySet());
            Validate.isTrue(updatedTickets == locked, "Expected to lock "+updatedTickets+" tickets, locked "+ locked);
            Map<Integer, Ticket> postUpdateTicket = ticketRepository.findTicketsInReservation(reservationId).stream().collect(toMap(Ticket::getId, Function.identity()));

            postUpdateTicket.forEach(
                (id, ticket) -> auditingHelper.auditUpdateTicket(preUpdateTicket.get(id), Collections.emptyMap(), ticket, Collections.emptyMap(), event.getId()));
        }
        var ticketsWithMetadataById = ticketRepository.findTicketsInReservationWithMetadata(reservationId)
            .stream().collect(toMap(twm -> twm.getTicket().getId(), Function.identity()));
        ticketsWithMetadataById.forEach((id, ticketWithMetadata) -> {
            var newMetadataOptional = extensionManager.handleTicketAssignmentMetadata(ticketWithMetadata, event);
            newMetadataOptional.ifPresent(metadata -> {
                var existingContainer = TicketMetadataContainer.copyOf(ticketWithMetadata.getMetadata());
                var general = new HashMap<>(existingContainer.getMetadataForKey(TicketMetadataContainer.GENERAL)
                    .orElseGet(TicketMetadata::empty).getAttributes());
                general.putAll(metadata.getAttributes());
                existingContainer.putMetadata(TicketMetadataContainer.GENERAL, new TicketMetadata(null, null, general));
                ticketRepository.updateTicketMetadata(id, existingContainer);
                auditingHelper.auditUpdateMetadata(reservationId, id, event.getId(), existingContainer, ticketWithMetadata.getMetadata());
            });
            auditingHelper.auditUpdateTicket(preUpdateTicket.get(id), Collections.emptyMap(), ticketWithMetadata.getTicket(), Collections.emptyMap(), event.getId());
        });
        int updatedAS = additionalServiceItemRepository.updateItemsStatusWithReservationUUID(reservationId, asStatus);
        Validate.isTrue(updatedTickets + updatedAS > 0, "no items have been updated");
    }

    public void confirmOfflinePayment(Event event,
                                      String reservationId,
                                      TransactionMetadataModification transactionMetadataModification,
                                      String username) {
        TicketReservation ticketReservation = findById(reservationId).orElseThrow(IllegalArgumentException::new);
        ticketReservationRepository.lockReservationForUpdate(reservationId);
        var metadata = ticketReservationRepository.getMetadata(reservationId);
        if (!metadata.isReadyForConfirmation()) {
            throw new IncompatibleStateException("Reservation is not ready to be confirmed");
        }
        Validate.isTrue(ticketReservation.getPaymentMethod() == PaymentProxy.OFFLINE, "invalid payment method");
        Validate.isTrue(ticketReservation.isPendingOfflinePayment(), "invalid status");


        ticketReservationRepository.confirmOfflinePayment(reservationId, COMPLETE.name(), event.now(clockProvider));

        registerAlfioTransaction(event, reservationId, transactionMetadataModification, PaymentProxy.OFFLINE);

        auditingRepository.insert(reservationId, userRepository.findIdByUserName(username).orElse(null), event.getId(), Audit.EventType.RESERVATION_OFFLINE_PAYMENT_CONFIRMED, new Date(), Audit.EntityType.RESERVATION, ticketReservation.getId());

        ticketReservationRepository.setMetadata(reservationId, metadata.withFinalized(true));
        CustomerName customerName = new CustomerName(ticketReservation.getFullName(), ticketReservation.getFirstName(), ticketReservation.getLastName(), event.mustUseFirstAndLastName());
        acquireItems(PaymentProxy.OFFLINE, reservationId, ticketReservation.getEmail(), customerName,
            ticketReservation.getUserLanguage(), ticketReservation.getBillingAddress(),
            ticketReservation.getCustomerReference(), event, true);

        Locale language = ReservationUtil.getReservationLocale(ticketReservation);
        final TicketReservation finalReservation = ticketReservationRepository.findReservationById(reservationId);
        billingDocumentManager.createBillingDocument(event, finalReservation, username, orderSummaryGenerator.orderSummaryForReservation(finalReservation, event));
        var configuration = configurationManager.getFor(EnumSet.of(DEFERRED_BANK_TRANSFER_ENABLED, DEFERRED_BANK_TRANSFER_SEND_CONFIRMATION_EMAIL), ConfigurationLevel.event(event));
        if(!configuration.get(DEFERRED_BANK_TRANSFER_ENABLED).getValueAsBooleanOrDefault() || configuration.get(DEFERRED_BANK_TRANSFER_SEND_CONFIRMATION_EMAIL).getValueAsBooleanOrDefault()) {
            reservationHelper.sendConfirmationEmail(event, findById(reservationId).orElseThrow(IllegalArgumentException::new), language, username);
        }
        extensionManager.handleReservationConfirmation(finalReservation, ticketReservationRepository.getBillingDetailsForReservation(reservationId), event);
    }

    public void registerAlfioTransaction(Event event,
                                         String reservationId,
                                         TransactionMetadataModification transactionMetadataModification,
                                         PaymentProxy paymentProxy) {
        var totalPrice = reservationCostCalculator.totalReservationCostWithVAT(reservationId).getLeft();
        int priceWithVAT = totalPrice.priceWithVAT();
        long platformFee = FeeCalculator.getCalculator(event, configurationManager, requireNonNullElse(totalPrice.currencyCode(), event.getCurrency()))
            .apply(ticketRepository.countTicketsInReservation(reservationId), (long) priceWithVAT)
            .orElse(0L);

        //FIXME we must support multiple transactions for a reservation, otherwise we can't handle properly the case of ON_SITE payments

        var transactionOptional = transactionRepository.loadOptionalByReservationId(reservationId);
        String transactionId = paymentProxy.getKey() + "-" + System.currentTimeMillis();
        var transactionTimestamp = getTransactionTimestamp(event, transactionMetadataModification);
        if(transactionOptional.isEmpty()) {
            transactionRepository.insert(transactionId, null, reservationId, transactionTimestamp,
                priceWithVAT, event.getCurrency(), "Offline payment confirmed for "+reservationId, paymentProxy.getKey(),
                platformFee, 0L, Transaction.Status.COMPLETE, buildTransactionMetadata(transactionMetadataModification));
        } else if(paymentProxy == PaymentProxy.OFFLINE) {
            var transaction = transactionOptional.get();
            transactionRepository.update(transaction.getId(), transactionId, null, transactionTimestamp,
                platformFee, 0L, Transaction.Status.COMPLETE, buildTransactionMetadata(transactionMetadataModification));
        } else {
            log.warn("ON-Site check-in: ignoring transaction registration for reservationId {}", reservationId);
        }
    }

    private ZonedDateTime getTransactionTimestamp(Event event, TransactionMetadataModification transactionMetadataModification) {
        if (transactionMetadataModification != null && transactionMetadataModification.getTimestamp() != null) {
            return transactionMetadataModification.getTimestamp().toLocalDateTime().atZone(event.getZoneId());
        }
        return event.now(clockProvider);
    }

    private Map<String, String> buildTransactionMetadata(TransactionMetadataModification transactionMetadataModification) {
        if (transactionMetadataModification != null && StringUtils.isNotBlank(transactionMetadataModification.getNotes())) {
            return Map.of(Transaction.NOTES_KEY, transactionMetadataModification.getNotes());
        }
        return Map.of();
    }

    private Optional<TicketReservation> findById(String reservationId) {
        return ticketReservationRepository.findOptionalReservationById(reservationId);
    }

    private List<Ticket> findTicketsInReservation(String reservationId) {
        return ticketRepository.findTicketsInReservation(reservationId);
    }


}
