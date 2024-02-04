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

import alfio.controller.support.TemplateProcessor;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.support.DuplicateReferenceException;
import alfio.manager.support.IncompatibleStateException;
import alfio.manager.support.reservation.ReservationEmailContentHelper;
import alfio.manager.system.ReservationPriceCalculator;
import alfio.model.*;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.decorator.TicketPriceContainer;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.metadata.TicketMetadata;
import alfio.model.metadata.TicketMetadataContainer;
import alfio.model.modification.AdminReservationModification;
import alfio.model.modification.AdminReservationModification.*;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.result.Result.ResultStatus;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.Transaction;
import alfio.model.user.Organization;
import alfio.model.user.User;
import alfio.repository.*;
import alfio.repository.user.UserRepository;
import alfio.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.ObjectError;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.Audit.EntityType.RESERVATION;
import static alfio.model.Audit.EntityType.TICKET;
import static alfio.model.Audit.EventType.CANCEL_TICKET;
import static alfio.model.Audit.EventType.UPDATE_TICKET;
import static alfio.model.modification.DateTimeModification.fromZonedDateTime;
import static alfio.util.EventUtil.generateEmptyTickets;
import static alfio.util.MonetaryUtil.unitToCents;
import static alfio.util.Wrappers.optionally;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.*;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Component
@Log4j2
@RequiredArgsConstructor
public class AdminReservationManager {

    private static final ErrorCode ERROR_CANNOT_CANCEL_CHECKED_IN_TICKETS = ErrorCode.custom("remove-reservation.failed", "This reservation contains checked-in tickets. Unable to cancel it.");
    private final PurchaseContextManager purchaseContextManager;
    private final EventManager eventManager;
    private final TicketReservationManager ticketReservationManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final SpecialPriceRepository specialPriceRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final EventRepository eventRepository;
    private final PlatformTransactionManager transactionManager;
    private final SpecialPriceTokenGenerator specialPriceTokenGenerator;
    private final PurchaseContextFieldRepository purchaseContextFieldRepository;
    private final PaymentManager paymentManager;
    private final NotificationManager notificationManager;
    private final MessageSourceManager messageSourceManager;
    private final TemplateManager templateManager;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final AuditingRepository auditingRepository;
    private final UserRepository userRepository;
    private final ExtensionManager extensionManager;
    private final BillingDocumentRepository billingDocumentRepository;
    private final FileUploadManager fileUploadManager;
    private final PromoCodeDiscountRepository promoCodeDiscountRepository;
    private final AdditionalServiceRepository additionalServiceRepository;
    private final BillingDocumentManager billingDocumentManager;
    private final ClockProvider clockProvider;
    private final SubscriptionRepository subscriptionRepository;
    private final ReservationEmailContentHelper reservationEmailContentHelper;
    private final TransactionRepository transactionRepository;
    private final AccessService accessService;

    //the following methods have an explicit transaction handling, therefore the @Transactional annotation is not helpful here
    Result<Triple<TicketReservation, List<Ticket>, PurchaseContext>> confirmReservation(PurchaseContextType purchaseContextType,
                                                                                        String eventName,
                                                                                        String reservationId,
                                                                                        String username,
                                                                                        Notification notification,
                                                                                        TransactionDetails transactionDetails,
                                                                                        UUID subscriptionId) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        TransactionTemplate template = new TransactionTemplate(transactionManager, definition);
        Result<String> result = template.execute(status -> {
            try {
                Result<String> confirmationResult = purchaseContextManager.findBy(purchaseContextType, eventName)
                    .map(purchaseContext -> ticketReservationRepository.findOptionalReservationById(reservationId)
                        .filter(r -> r.getStatus() == TicketReservationStatus.PENDING || r.getStatus() == TicketReservationStatus.STUCK)
                        .map(r -> performConfirmation(reservationId, purchaseContext, r, notification, transactionDetails, username, subscriptionId))
                        .orElseGet(() -> Result.error(ErrorCode.ReservationError.UPDATE_FAILED))
                    ).orElseGet(() -> Result.error(ErrorCode.ReservationError.NOT_FOUND));
                if(!confirmationResult.isSuccess()) {
                    log.debug("Reservation confirmation failed for eventName: {} reservationId: {}, username: {}", eventName, reservationId, username);
                    status.setRollbackOnly();
                }
                return confirmationResult;
            } catch (Exception e) {
                log.error("Error during confirmation of reservation eventName: {} reservationId: {}, username: {}", eventName, reservationId, username);
                status.setRollbackOnly();
                return Result.error(singletonList(ErrorCode.custom("", e.getMessage())));
            }
        });
        return requireNonNull(result).flatMap(this::loadReservation);
    }
    public Result<Triple<TicketReservation, List<Ticket>, PurchaseContext>> confirmReservation(PurchaseContextType purchaseContextType,
                                                                                               String eventName,
                                                                                               String reservationId,
                                                                                               String username,
                                                                                               Notification notification) {
        return confirmReservation(purchaseContextType, eventName, reservationId, username, notification, TransactionDetails.admin(), null);
    }

    public Result<Triple<TicketReservation, List<Ticket>, PurchaseContext>> confirmReservation(String reservationId,
                                                                                               Principal principal,
                                                                                               TransactionDetails transaction,
                                                                                               Notification notification) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        TransactionTemplate template = new TransactionTemplate(transactionManager, definition);
        return template.execute(t -> {
            var purchaseContextOptional = purchaseContextManager.findByReservationId(reservationId);
            if (purchaseContextOptional.isEmpty()) {
                return Result.error(ErrorCode.ReservationError.NOT_FOUND);
            }
            var purchaseContext = purchaseContextOptional.get();
            accessService.checkOrganizationOwnership(principal, purchaseContext.getOrganizationId());
            return confirmReservation(purchaseContext.getType(), purchaseContext.getPublicIdentifier(), reservationId, principal.getName(), notification, transaction, null);
        });
    }

    public Result<Boolean> updateReservation(PurchaseContextType purchaseContextType, String publicIdentifier, String reservationId, AdminReservationModification adminReservationModification, String username) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        TransactionTemplate template = new TransactionTemplate(transactionManager, definition);
        return template.execute(status -> {
            try {
                Result<Boolean> result = purchaseContextManager.findBy(purchaseContextType, publicIdentifier)
                    .map(event -> ticketReservationRepository.findOptionalReservationById(reservationId)
                        .map(r -> performUpdate(reservationId, event, r, adminReservationModification, username))
                        .orElseGet(() -> Result.error(ErrorCode.ReservationError.UPDATE_FAILED))
                    ).orElseGet(() -> Result.error(ErrorCode.ReservationError.NOT_FOUND));
                if(!result.isSuccess()) {
                    log.debug("Application error detected eventName: {} reservationId: {}, username: {}, reservation: {}", publicIdentifier, reservationId, username, AdminReservationModification.summary(adminReservationModification));
                    status.setRollbackOnly();
                }
                return result;
            } catch (Exception e) {
                log.error("Error during update of reservation eventName: {} reservationId: {}, username: {}, reservation: {}", publicIdentifier, reservationId, username, AdminReservationModification.summary(adminReservationModification));
                status.setRollbackOnly();
                return Result.error(singletonList(ErrorCode.custom("", e.getMessage())));
            }
        });
    }

    public Result<Pair<TicketReservation, List<Ticket>>> createReservation(AdminReservationModification input, String eventName, String username) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_NESTED);
        TransactionTemplate template = new TransactionTemplate(transactionManager, definition);
        return template.execute(status -> {
            var savepoint = status.createSavepoint();
            try {
                Result<Pair<TicketReservation, List<Ticket>>> result = eventRepository.findOptionalByShortNameForUpdate(eventName)
                    .map(e -> validateTickets(input, e))
                    .map(r -> r.flatMap(p -> transactionalCreateReservation(p.getRight(), p.getLeft(), username)))
                    .orElse(Result.error(ErrorCode.EventError.NOT_FOUND));
                if (!result.isSuccess()) {
                    log.warn("Error during update of reservation eventName: {}, username: {}, reservation: {}", eventName, username, AdminReservationModification.summary(input));
                    status.rollbackToSavepoint(savepoint);
                }
                return result;
            } catch (Exception e) {
                log.error("Error during update of reservation eventName: {}, username: {}, reservation: {}", eventName, username, AdminReservationModification.summary(input));
                log.debug("Error detail:", e);
                status.rollbackToSavepoint(savepoint);
                return Result.error(singletonList(ErrorCode.custom(e instanceof DuplicateReferenceException ? "duplicate-reference" : "", e.getMessage())));
            }
        });
    }

    //end - the public / package protected methods below must be annotated with @Transactional

    @Transactional
    public Result<Boolean> notifyAttendees(String eventName, String reservationId, List<Integer> ids, String username) {
        return getEventTicketReservationPair(PurchaseContextType.event, eventName, reservationId, username)
            .map(pair -> {
                Event event = pair.getLeft().event().orElseThrow();
                TicketReservation reservation = pair.getRight();
                sendTicketToAttendees(event, reservation, t -> t.getAssigned() && ids.contains(t.getId()));
                return Result.success(true);
            }).orElseGet(() -> Result.error(ErrorCode.EventError.NOT_FOUND));
    }

    @Transactional
    public Result<Boolean> notify(PurchaseContextType purchaseContextType, String publicIdentifier, String reservationId, AdminReservationModification arm, String username) {
        Notification notification = arm.getNotification();
        return getEventTicketReservationPair(purchaseContextType, publicIdentifier, reservationId, username)
            .map(pair -> {
                var purchaseContext = pair.getLeft();
                TicketReservation reservation = pair.getRight();
                if(notification.isCustomer()) {
                    ticketReservationManager.sendConfirmationEmail(purchaseContext, reservation, LocaleUtil.forLanguageTag(reservation.getUserLanguage()), username);
                }
                if(notification.isAttendees() && purchaseContextType == PurchaseContextType.event) {
                    sendTicketToAttendees(purchaseContext.event().orElseThrow(), reservation, Ticket::getAssigned);
                }
                return Result.success(true);
            }).orElseGet(() -> Result.error(ErrorCode.EventError.NOT_FOUND));

    }

    private Optional<Pair<PurchaseContext, TicketReservation>> getEventTicketReservationPair(PurchaseContextType purchaseContextType, String publicIdentifier, String reservationId, String username) {
        return purchaseContextManager.findBy(purchaseContextType, publicIdentifier)
            .flatMap(ev -> ticketReservationRepository.findOptionalReservationById(reservationId).map(r -> Pair.of(ev, r)));
    }

    private void sendTicketToAttendees(Event event, TicketReservation reservation, Predicate<Ticket> matcher) {
        ticketRepository.findTicketsInReservation(reservation.getId())
            .stream()
            .filter(matcher)
            .forEach(t -> {
                Locale locale = LocaleUtil.forLanguageTag(t.getUserLanguage());
                var additionalInfo = ticketReservationManager.retrieveAttendeeAdditionalInfoForTicket(t);
                reservationEmailContentHelper.sendTicketByEmail(t, locale, event, ticketReservationManager.getTicketEmailGenerator(event, reservation, locale, additionalInfo));
            });
    }

    private Result<Boolean> performUpdate(String reservationId, PurchaseContext purchaseContext, TicketReservation r, AdminReservationModification arm, String username) {
        billingDocumentManager.ensureBillingDocumentIsPresent(purchaseContext, r, username, () -> ticketReservationManager.orderSummaryForReservationId(reservationId, purchaseContext));
        ticketReservationRepository.updateValidity(reservationId, Date.from(arm.getExpiration().toZonedDateTime(purchaseContext.getZoneId()).toInstant()));
        if(arm.isUpdateContactData()) {
            AdminReservationModification.CustomerData customerData = arm.getCustomerData();
            ticketReservationRepository.updateTicketReservation(reservationId, r.getStatus().name(), customerData.getEmailAddress(),
                customerData.getFullName(), customerData.getFirstName(), customerData.getLastName(), customerData.getUserLanguage(),
                customerData.getBillingAddress(), r.getConfirmationTimestamp(),
                Optional.ofNullable(r.getPaymentMethod()).map(PaymentProxy::name).orElse(null), customerData.getCustomerReference());

            if(StringUtils.isNotBlank(customerData.getVatNr()) || StringUtils.isNotBlank(customerData.getVatCountryCode())) {
                ticketReservationRepository.updateBillingData(r.getVatStatus(), r.getSrcPriceCts(), r.getFinalPriceCts(), r.getVatCts(),
                    r.getDiscountCts(), r.getCurrencyCode(), customerData.getVatNr(), customerData.getVatCountryCode(),
                    r.isInvoiceRequested(), reservationId);
            }

            ticketReservationRepository.updateInvoicingAdditionalInformation(reservationId, Json.toJson(arm.getCustomerData().getInvoicingAdditionalInfo()));

        }

        if(arm.isUpdateAdvancedBillingOptions() && purchaseContext.getVatStatus() != PriceContainer.VatStatus.NONE) {
            boolean vatApplicationRequested = arm.getAdvancedBillingOptions().isVatApplied();
            PriceContainer.VatStatus newVatStatus;
            if(vatApplicationRequested) {
                newVatStatus = purchaseContext.getVatStatus();
            } else {
                newVatStatus = purchaseContext.getVatStatus() == PriceContainer.VatStatus.INCLUDED ? PriceContainer.VatStatus.INCLUDED_EXEMPT : PriceContainer.VatStatus.NOT_INCLUDED_EXEMPT;
            }

            if(newVatStatus != ObjectUtils.firstNonNull(r.getVatStatus(), purchaseContext.getVatStatus())) {
                auditingRepository.insert(reservationId, userRepository.getByUsername(username).getId(), purchaseContext, Audit.EventType.FORCE_VAT_APPLICATION, new Date(), Audit.EntityType.RESERVATION, reservationId, singletonList(singletonMap("vatStatus", newVatStatus)));
                ticketReservationRepository.addReservationInvoiceOrReceiptModel(reservationId, null);
                var newPrice = ticketReservationManager.totalReservationCostWithVAT(r.withVatStatus(newVatStatus)).getLeft();
                ticketReservationRepository.resetVat(reservationId, r.isInvoiceRequested(), newVatStatus, r.getSrcPriceCts(), newPrice.getPriceWithVAT(),
                    newPrice.getVAT(), Math.abs(newPrice.getDiscount()), r.getCurrencyCode());
            }
        }

        Date d = new Date();
        arm.getTicketsInfo().stream()
            .filter(TicketsInfo::isUpdateAttendees)
            .flatMap(ti -> ti.getAttendees().stream())
            .forEach(a -> {
                String email = trimToNull(a.getEmailAddress());
                String firstName = trimToNull(a.getFirstName());
                String lastName = trimToNull(a.getLastName());
                String fullName = trimToNull(a.getFullName());
                ticketRepository.updateTicketOwnerById(a.getTicketId(), email, fullName, firstName, lastName);
                Integer userId = userRepository.findByUsername(username).map(User::getId).orElse(null);
                Map<String, Object> modifications = new HashMap<>();
                modifications.put("firstName", firstName);
                modifications.put("lastName", lastName);
                modifications.put("fullName", fullName);
                auditingRepository.insert(reservationId, userId, purchaseContext, UPDATE_TICKET, d, TICKET, Integer.toString(a.getTicketId()), singletonList(modifications));
            });

        if(purchaseContext.getType() == PurchaseContextType.subscription && arm.getSubscriptionDetails() != null) {
            var subscriptionDetailsModification = arm.getSubscriptionDetails();
            // update subscription details
            var subscription = subscriptionRepository.findFirstSubscriptionByReservationIdForUpdate(reservationId).orElseThrow();
            subscriptionRepository.updateSubscription(subscription.getId(),
                firstNonBlank(subscriptionDetailsModification.getFirstName(), subscription.getFirstName()),
                firstNonBlank(subscriptionDetailsModification.getLastName(), subscription.getLastName()),
                firstNonBlank(subscriptionDetailsModification.getEmail(), subscription.getEmail()),
                requireNonNullElse(subscriptionDetailsModification.getMaxAllowed(), subscription.getMaxEntries()),
                subscriptionDetailsModification.getValidityFrom() != null ? subscriptionDetailsModification.getValidityFrom().toZonedDateTime(subscription.getZoneId()) : null,
                subscriptionDetailsModification.getValidityTo() != null ? subscriptionDetailsModification.getValidityTo().toZonedDateTime(subscription.getZoneId()) : null
            );
        }

        return Result.success(true);
    }

    @Transactional
    public Result<Triple<TicketReservation, List<Ticket>, PurchaseContext>> loadReservation(PurchaseContextType purchaseContextType,
                                                                                            String publicIdentifier,
                                                                                            String reservationId,
                                                                                            String username) {
        return purchaseContextManager.findBy(purchaseContextType, publicIdentifier).map(r -> loadReservation(reservationId))
            .orElseGet(() -> Result.error(ErrorCode.ReservationError.NOT_FOUND));
    }

    @Transactional
    public List<Integer> getTicketIdsWithAdditionalData(PurchaseContextType purchaseContextType, String publicIdentifier, String reservationId) {
        if(purchaseContextType != PurchaseContextType.event) {
            return List.of();
        }
        return ticketRepository.findTicketsWithAdditionalData(reservationId, publicIdentifier);
    }

    @Transactional
    public Optional<Pair<Event, Ticket>> loadFullTicketInfo(String reservationId, String eventShortName, String ticketUUID) {
        return purchaseContextManager.findBy(PurchaseContextType.event, eventShortName)
            .flatMap(event -> ticketRepository.findOptionalByUUID(ticketUUID)
                .filter(ticket -> reservationId.equals(ticket.getTicketsReservationId()))
                .map(ticket -> Pair.of((Event) event, ticket)));
    }


    private Result<Triple<TicketReservation, List<Ticket>, PurchaseContext>> loadReservation(String reservationId) {
        return ticketReservationRepository.findOptionalReservationById(reservationId)
            .map(r -> Triple.of(r, ticketRepository.findTicketsInReservation(reservationId), purchaseContextManager.findByReservationId(reservationId).orElseThrow()))
            .map(Result::success)
            .orElseGet(() -> Result.error(ErrorCode.ReservationError.NOT_FOUND));
    }

    private Result<String> performConfirmation(String reservationId,
                                               PurchaseContext purchaseContext,
                                               TicketReservation original,
                                               Notification notification,
                                               TransactionDetails transactionDetails,
                                               String username,
                                               UUID subscriptionId) {
        try {

            var reservation = original;

            if (subscriptionId != null && purchaseContext.ofType(PurchaseContextType.event)) {
                if (reservation.getVatStatus() == null && purchaseContext.getVatStatus() != null) {
                    // set default vatStatus if not present
                    ticketReservationRepository.updateVatStatus(reservationId, purchaseContext.getVatStatus());
                    reservation = ticketReservationManager.findById(reservationId).orElseThrow();
                }
                var subscriptionDetails = subscriptionRepository.findSubscriptionById(subscriptionId);
                var bindingResult = new MapBindingResult(new HashMap<>(), "");
                boolean result = ticketReservationManager.validateAndApplySubscriptionCode(purchaseContext,
                    reservation,
                    Optional.of(subscriptionId),
                    subscriptionId.toString(),
                    subscriptionDetails.getEmail(),
                    bindingResult);
                if (!result) {
                    var message = bindingResult.getGlobalErrors().stream()
                        .findFirst()
                        .map(DefaultMessageSourceResolvable::getCode)
                        .orElse("Unknown error");
                    log.warn("Unable to apply subscription {}: {}", subscriptionId,
                        bindingResult.getAllErrors().stream().map(ObjectError::getCode).collect(joining(", ")));
                    return Result.error(ErrorCode.custom(message, String.format("Cannot assign subscription %s to Reservation %s", subscriptionId, reservationId)));
                }

                reservation = ticketReservationManager.findById(reservationId).orElseThrow();
            }

            PaymentSpecification spec = new PaymentSpecification(reservationId,
                null,
                reservation.getFinalPriceCts(),
                purchaseContext,
                reservation.getEmail(),
                new CustomerName(reservation.getFullName(), reservation.getFirstName(), reservation.getLastName(), purchaseContext.mustUseFirstAndLastName()),
                reservation.getBillingAddress(),
                reservation.getCustomerReference(),
                LocaleUtil.forLanguageTag(reservation.getUserLanguage()),
                false,
                false,
                null,
                reservation.getVatCountryCode(),
                reservation.getVatNr(),
                reservation.getVatStatus(),
                false,
                false);

            if (transactionDetails.getPaymentProvider() != PaymentProxy.ADMIN) {
                var timestamp = Objects.requireNonNullElseGet(transactionDetails.getTimestamp(), () -> LocalDateTime.now(clockProvider.getClock())).atZone(purchaseContext.getZoneId());
                if (transactionRepository.transactionExists(reservationId)) {
                    paymentManager.updateTransactionDetails(reservationId, transactionDetails.getNotes(), timestamp, null);
                } else {
                    var paidAmount = Objects.requireNonNullElse(transactionDetails.getPaidAmount(), BigDecimal.ZERO);
                    transactionRepository.insert(
                        StringUtils.trimToEmpty(transactionDetails.getId()),
                        "",
                        reservationId,
                        timestamp,
                        MonetaryUtil.unitToCents(paidAmount, reservation.getCurrencyCode()),
                        reservation.getCurrencyCode(),
                        "",
                        transactionDetails.getPaymentProvider().name(),
                        0L,
                        0L,
                        Transaction.Status.COMPLETE,
                        Map.of(Transaction.NOTES_KEY, StringUtils.trimToEmpty(transactionDetails.getNotes()))
                    );
                }
            }

            ticketReservationManager.completeReservation(spec,
                transactionDetails.getPaymentProvider(),
                notification.isCustomer(),
                notification.isAttendees(),
                username);

            return Result.success(reservationId);
        } catch(Exception e) {
            return Result.error(ErrorCode.ReservationError.UPDATE_FAILED);
        }
    }

    @Transactional
    Result<Pair<Event, AdminReservationModification>> validateTickets(AdminReservationModification input, Event event) {
        Set<String> keys = input.getTicketsInfo().stream().flatMap(ti -> ti.getAttendees().stream())
            .flatMap(a -> a.getAdditionalInfo().keySet().stream())
            .map(String::toLowerCase)
            .collect(toSet());

        if(keys.isEmpty()) {
            return Result.success(Pair.of(event, input));
        }

        List<String> existing = purchaseContextFieldRepository.getExistingFields(event.getId(), keys);
        if(existing.size() == keys.size()) {
            return Result.success(Pair.of(event, input));
        }

        return Result.error(keys.stream()
                     .filter(k -> !existing.contains(k))
                     .map(k -> ErrorCode.custom("error.notfound."+k, k+" not found"))
                     .collect(toList()));
    }

    private Result<Pair<TicketReservation, List<Ticket>>> transactionalCreateReservation(AdminReservationModification input, Event event, String username) {
        return optionally(() -> {
                eventManager.checkOwnership(event, username, event.getOrganizationId());
                return event;
            }).map(e -> processReservation(input, username, e))
            .orElseGet(() -> Result.error(singletonList(ErrorCode.EventError.NOT_FOUND)));
    }

    private Result<Pair<TicketReservation, List<Ticket>>> processReservation(AdminReservationModification input, String username, Event event) {
        return input.getTicketsInfo().stream()
            .map(ti -> checkCategoryCapacity(ti, event, input, username))
            .reduce((r1, r2) -> reduceResults(r1, r2, this::joinData))
            .map(r -> createReservation(r, event, input))
            .orElseGet(() -> Result.error(singletonList(ErrorCode.custom("", "something went wrong..."))));
    }

    private List<TicketsInfo> joinData(List<TicketsInfo> t1, List<TicketsInfo> t2) {
        List<TicketsInfo> join = new ArrayList<>();
        join.addAll(t1);
        join.addAll(t2);
        return join;
    }

    private Result<Pair<TicketReservation, List<Ticket>>> createReservation(Result<List<TicketsInfo>> input, Event event, AdminReservationModification arm) {
        final TicketsInfo empty = new TicketsInfo(null, null, false, false);
        return input.flatMap(t -> {
            String reservationId = UUID.randomUUID().toString();
            Date validity = Date.from(arm.getExpiration().toZonedDateTime(event.getZoneId()).toInstant());
            ticketReservationRepository.createNewReservation(reservationId, event.now(clockProvider), validity, null,
                arm.getLanguage(), event.getId(), event.getVat(), event.isVatIncluded(), event.getCurrency(), event.getOrganizationId(), null);
            AdminReservationModification.CustomerData customerData = arm.getCustomerData();
            ticketReservationRepository.updateTicketReservation(reservationId, TicketReservationStatus.PENDING.name(), customerData.getEmailAddress(),
                customerData.getFullName(), customerData.getFirstName(), customerData.getLastName(), arm.getLanguage(),
                customerData.getBillingAddress(), null, null, customerData.getCustomerReference());

            Result<List<Ticket>> result = flattenTicketsInfo(event, empty, t)
                .map(pair -> reserveForTicketsInfo(event, arm, reservationId, pair))
                .reduce(this::reduceReservationResults)
                .orElseGet(() -> Result.error(ErrorCode.custom("", "unknown error")));

            return result.map(list -> Pair.of(ticketReservationRepository.findReservationById(reservationId), list));
        });
    }

    private Result<List<Ticket>> reserveForTicketsInfo(Event event, AdminReservationModification arm, String reservationId, Pair<TicketCategory, TicketsInfo> pair) {
        TicketCategory category = pair.getLeft();
        TicketsInfo ticketsInfo = pair.getRight();
        int categoryId = category.getId();
        List<Attendee> attendees = ticketsInfo.getAttendees();
        List<Integer> reservedForUpdate = ticketReservationManager.reserveTickets(event.getId(), categoryId, attendees.size(), singletonList(Ticket.TicketStatus.FREE));
        if (reservedForUpdate.isEmpty()|| reservedForUpdate.size() != attendees.size()) {
            return Result.error(ErrorCode.CategoryError.NOT_ENOUGH_SEATS);
        }
        var currencyCode = category.getCurrencyCode();
        ticketRepository.reserveTickets(reservationId, reservedForUpdate, category, arm.getLanguage(), event.getVatStatus(), i -> null);
        Ticket ticket = ticketRepository.findById(reservedForUpdate.get(0), categoryId);
        TicketPriceContainer priceContainer = TicketPriceContainer.from(ticket, null, event.getVat(), event.getVatStatus(), null);
        ticketRepository.updateTicketPrice(reservedForUpdate,
            categoryId,
            event.getId(),
            category.getSrcPriceCts(),
            unitToCents(priceContainer.getFinalPrice(), currencyCode),
            unitToCents(priceContainer.getVAT(), currencyCode),
            unitToCents(priceContainer.getAppliedDiscount(), currencyCode),
            currencyCode,
            priceContainer.getVatStatus());
        List<SpecialPrice> codes = category.isAccessRestricted() ? bindSpecialPriceTokens(categoryId, attendees) : Collections.emptyList();
        if(category.isAccessRestricted() && codes.size() < attendees.size()) {
            return Result.error(ErrorCode.CategoryError.NOT_ENOUGH_SEATS);
        }
        assignTickets(event, attendees, categoryId, reservedForUpdate, codes, reservationId, arm.getLanguage(), category.getSrcPriceCts());
        List<Ticket> tickets = reservedForUpdate.stream().map(id -> ticketRepository.findById(id, categoryId)).collect(toList());
        return Result.success(tickets);
    }

    private Result<List<Ticket>> reduceReservationResults(Result<List<Ticket>> r1, Result<List<Ticket>> r2) {
        return reduceResults(r1, r2, this::joinCreateReservationResults);
    }

    private List<Ticket> joinCreateReservationResults(List<Ticket> r1, List<Ticket> r2) {
        List<Ticket> data = new ArrayList<>(r1);
        data.addAll(r2);
        return data;
    }

    private <T> Result<T> reduceResults(Result<T> r1, Result<T> r2, BinaryOperator<T> processData) {
        boolean successful = r1.isSuccess() && r2.isSuccess();
        ResultStatus global = r1.isSuccess() ? r2.getStatus() : r1.getStatus();
        List<ErrorCode> errors = new ArrayList<>();
        if(!successful) {
            errors.addAll(r1.getErrors());
            errors.addAll(r2.getErrors());
            return new Result<>(global, null, errors);
        } else {
            return new Result<>(global, processData.apply(r1.getData(), r2.getData()), errors);
        }
    }

    private Stream<Pair<TicketCategory, TicketsInfo>> flattenTicketsInfo(Event event, TicketsInfo empty, List<TicketsInfo> t) {
        return t.stream()
            .collect(groupingBy(ti -> ti.getCategory().getExistingCategoryId()))
            .entrySet()
            .stream()
            .map(entry -> {
                TicketsInfo ticketsInfo = entry.getValue()
                    .stream()
                    .reduce((ti1, ti2) -> {
                        List<Attendee> attendees = new ArrayList<>(ti1.getAttendees());
                        attendees.addAll(ti2.getAttendees());
                        return new TicketsInfo(ti1.getCategory(), attendees, ti1.isAddSeatsIfNotAvailable() && ti2.isAddSeatsIfNotAvailable(), ti1.isUpdateAttendees() && ti2.isUpdateAttendees());
                    }).orElse(empty);
                return Pair.of(ticketCategoryRepository.getByIdAndActive(entry.getKey(), event.getId()), ticketsInfo);
            });
    }

    private List<SpecialPrice> bindSpecialPriceTokens(int categoryId, List<Attendee> attendees) {
        specialPriceTokenGenerator.generatePendingCodesForCategory(categoryId);
        List<SpecialPrice> codes = specialPriceRepository.findActiveNotAssignedByCategoryId(categoryId, attendees.size());

        codes.forEach(c -> specialPriceRepository.updateStatus(c.getId(), SpecialPrice.Status.PENDING.toString(), null, null));
        return codes;
    }

    private void assignTickets(Event event,
                               List<Attendee> attendees,
                               int categoryId,
                               List<Integer> reservedForUpdate,
                               List<SpecialPrice> codes,
                               String reservationId,
                               String userLanguage,
                               int srcPriceCts) {

        Optional<Iterator<SpecialPrice>> specialPriceIterator = Optional.of(codes).filter(c -> !c.isEmpty()).map(Collection::iterator);
        for(int i=0; i<reservedForUpdate.size(); i++) {
            Attendee attendee = attendees.get(i);
            Integer ticketId = reservedForUpdate.get(i);
            if(!attendee.isEmpty()) {
                ticketRepository.updateTicketOwnerById(ticketId, attendee.getEmailAddress(), attendee.getFullName(), attendee.getFirstName(), attendee.getLastName());
                if(StringUtils.isNotBlank(attendee.getReference()) || attendee.isReassignmentForbidden()) {
                    updateExtRefAndLocking(categoryId, attendee, ticketId);
                }
                if(!attendee.getAdditionalInfo().isEmpty()) {
                    purchaseContextFieldRepository.updateOrInsert(attendee.getAdditionalInfo(), event, ticketId, null);
                }
                if (!attendee.getMetadata().isEmpty()) {
                    var ticketMetadata = new TicketMetadata(null, null, attendee.getMetadata());
                    ticketRepository.updateTicketMetadata(ticketId, TicketMetadataContainer.fromMetadata(ticketMetadata));
                }
            }
            specialPriceIterator.map(Iterator::next)
                .ifPresent(code -> ticketRepository.reserveTicket(reservationId, ticketId, code.getId(), userLanguage, srcPriceCts, event.getCurrency(), event.getVatStatus(), null));
        }
    }

    private void updateExtRefAndLocking(int categoryId, Attendee attendee, Integer ticketId) {
        try {
            ticketRepository.updateExternalReferenceAndLocking(ticketId, categoryId, StringUtils.trimToNull(attendee.getReference()), attendee.isReassignmentForbidden());
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate found for external reference: "+attendee.getReference()+" and ticketID: " + ticketId);
            throw new DuplicateReferenceException("Duplicated Reference: "+attendee.getReference(), ex);
        }
    }

    private Result<List<TicketsInfo>> checkCategoryCapacity(TicketsInfo ti, Event event, AdminReservationModification reservation, String username) {
        Result<TicketCategory> ticketCategoryResult = ti.getCategory().isExisting() ? checkExistingCategory(ti, event, username) : createCategory(ti, event, reservation, username);
        return ticketCategoryResult
            .map(tc -> List.of(new TicketsInfo(new Category(tc.getId(), tc.getName(), tc.getPrice(), tc.getTicketAccessType()), ti.getAttendees(), ti.isAddSeatsIfNotAvailable(), ti.isUpdateAttendees())));
    }

    private Result<TicketCategory> createCategory(TicketsInfo ti, Event event, AdminReservationModification reservation, String username) {
        Category category = ti.getCategory();
        List<Attendee> attendees = ti.getAttendees();
        DateTimeModification inception = fromZonedDateTime(event.now(clockProvider));

        int tickets = attendees.size();
        var accessType = event.getFormat() != Event.EventFormat.HYBRID ? TicketCategory.TicketAccessType.INHERIT : Objects.requireNonNull(category.getTicketAccessType());
        TicketCategoryModification tcm = new TicketCategoryModification(category.getExistingCategoryId(), category.getName(), accessType, tickets,
            inception, reservation.getExpiration(), Collections.emptyMap(), category.getPrice(), true, "",
            true, null, null, null, null, null, 0, null, null,
            AlfioMetadata.empty());
        int notAllocated = getNotAllocatedTickets(event);
        int missingTickets = Math.max(tickets - notAllocated, 0);
        Event modified = increaseSeatsIfNeeded(ti, event, missingTickets, event);
        return eventManager.insertCategory(modified, tcm, username).map(id -> ticketCategoryRepository.getByIdAndActive(id, event.getId()));
    }

    private Event increaseSeatsIfNeeded(TicketsInfo ti, Event event, int missingTickets, Event modified) {
        if(missingTickets > 0 && ti.isAddSeatsIfNotAvailable()) {
            int newTotal = eventRepository.countExistingTickets(event.getId()) + missingTickets;
            extensionManager.handleEventSeatsUpdateValidation(event, newTotal);
            createMissingTickets(event, missingTickets);
            //update seats and reload event
            log.debug("adding {} extra seats to the event", missingTickets);
            eventRepository.updateAvailableSeats(event.getId(), newTotal);
            modified = eventRepository.findById(event.getId());
        }
        return modified;
    }

    private int getNotAllocatedTickets(EventAndOrganizationId event) {
        return ticketRepository.countFreeTicketsForUnbounded(event.getId());
    }

    private Result<TicketCategory> checkExistingCategory(TicketsInfo ti, Event event, String username) {
        Category category = ti.getCategory();
        List<Attendee> attendees = ti.getAttendees();
        int tickets = attendees.size();
        int eventId = event.getId();
        TicketCategory existing = ticketCategoryRepository.getByIdAndActive(category.getExistingCategoryId(), eventId);
        int existingCategoryId = existing.getId();
        int freeTicketsInCategory = ticketRepository.countFreeTickets(eventId, existingCategoryId);
        int notAllocated = getNotAllocatedTickets(event);
        int missingTickets = Math.max(tickets - (freeTicketsInCategory + notAllocated), 0);
        Event modified = increaseSeatsIfNeeded(ti, event, missingTickets, event);
        if(freeTicketsInCategory < tickets && existing.isBounded()) {
            int maxTickets = existing.getMaxTickets() + (tickets - freeTicketsInCategory);
            TicketCategoryModification tcm = new TicketCategoryModification(existingCategoryId, existing.getName(), existing.getTicketAccessType(), maxTickets,
                fromZonedDateTime(existing.getInception(modified.getZoneId())), fromZonedDateTime(existing.getExpiration(event.getZoneId())),
                Collections.emptyMap(), existing.getPrice(), existing.isAccessRestricted(), "", true, existing.getCode(),
                fromZonedDateTime(existing.getValidCheckInFrom(modified.getZoneId())),
                fromZonedDateTime(existing.getValidCheckInTo(modified.getZoneId())),
                fromZonedDateTime(existing.getTicketValidityStart(modified.getZoneId())),
                fromZonedDateTime(existing.getTicketValidityEnd(modified.getZoneId())), 0,
                existing.getTicketCheckInStrategy(), null, AlfioMetadata.empty());
            return eventManager.updateCategory(existingCategoryId, modified, tcm, username, true);
        }
        return Result.success(existing);
    }

    private void createMissingTickets(Event event, int tickets) {
        final MapSqlParameterSource[] params = generateEmptyTickets(event, Date.from(event.now(clockProvider).toInstant()), tickets, Ticket.TicketStatus.FREE).toArray(MapSqlParameterSource[]::new);
        ticketRepository.bulkTicketInitialization(params);
    }

    @Transactional
    public Optional<Ticket> findTicketWithReservationId(String ticketUUID, String eventSlug, String username) {
        return eventManager.getOptionalEventAndOrganizationIdByName(eventSlug, username)
            .flatMap(eventAndOrganizationId -> {
                var ticket = ticketRepository.findByUUID(ticketUUID);
                // check that ticket belongs to the event
                if (ticket.getEventId() != eventAndOrganizationId.getId() || StringUtils.isEmpty(ticket.getTicketsReservationId())) {
                    return Optional.empty();
                } else {
                    return Optional.of(ticket);
                }
            });
    }

    @Transactional
    public Result<Boolean> removeTickets(String publicIdentifier, String reservationId, List<Integer> ticketIds, List<Integer> toRefund, boolean notify, boolean creditNoteRequested, String username) {
        return loadReservation(PurchaseContextType.event, publicIdentifier, reservationId, username).map(res -> {
            Event e = res.getRight().event().orElseThrow();
            TicketReservation reservation = res.getLeft();
            List<Ticket> tickets = res.getMiddle();
            Map<Integer, Ticket> ticketsById = tickets.stream().collect(Collectors.toMap(Ticket::getId, Function.identity()));
            Set<Integer> ticketIdsInReservation = tickets.stream().map(Ticket::getId).collect(toSet());
            // ensure that all the tickets ids are present in tickets
            Assert.isTrue(ticketIdsInReservation.containsAll(ticketIds), "Some ticket ids are not contained in the reservation");
            Assert.isTrue(ticketIdsInReservation.containsAll(toRefund), "Some ticket ids to refund are not contained in the reservation");
            if (!ticketsStatusIsCompatibleWithCancellation(tickets.stream().filter(t -> ticketIds.contains(t.getId())))) {
                throw new IncompatibleStateException("Cannot remove checked-in tickets");
            }
            //

            handleTicketsRefund(toRefund, e, reservation, ticketsById, username);

            boolean removeReservation = tickets.size() - ticketIds.size() <= 0;
            boolean issueCreditNote = (creditNoteRequested &&
                // if payment method supports refund we require that the user has selected at least one ticket
                (!toRefund.isEmpty() || !reservation.getPaymentMethod().isSupportRefund())
            );
            removeTicketsFromReservation(reservation, e, ticketIds, notify, username, removeReservation, issueCreditNote);
            //

            if(removeReservation) {
                markAsCancelled(reservation, username, e);
                additionalServiceItemRepository.updateItemsStatusWithReservationUUID(e.getId(), reservation.getId(), AdditionalServiceItem.AdditionalServiceItemStatus.CANCELLED);
            } else {
                // recalculate totals
                var totalPrice = ticketReservationManager.totalReservationCostWithVAT(reservationId).getLeft();
                var currencyCode = totalPrice.getCurrencyCode();
                var updatedTickets = ticketRepository.findTicketsInReservation(reservationId);
                var discount = reservation.getPromoCodeDiscountId() != null ? promoCodeDiscountRepository.findById(reservation.getPromoCodeDiscountId()) : null;
                List<AdditionalServiceItem> additionalServiceItems = additionalServiceItemRepository.findByReservationUuid(e.getId(), reservationId);
                var calculator = new ReservationPriceCalculator(reservation, discount, updatedTickets, additionalServiceItems, additionalServiceRepository.loadAllForEvent(e.getId()), e, List.of(), Optional.empty());
                ticketReservationRepository.updateBillingData(calculator.getVatStatus(),
                    calculator.getSrcPriceCts(), unitToCents(calculator.getFinalPrice(), currencyCode), unitToCents(calculator.getVAT(), currencyCode),
                    unitToCents(calculator.getAppliedDiscount(), currencyCode), calculator.getCurrencyCode(), reservation.getVatNr(), reservation.getVatCountryCode(),
                    reservation.isInvoiceRequested(), reservationId);
            }
            return issueCreditNote;
        });
    }

    @Transactional
    public Optional<Pair<SubscriptionDescriptor, String>> findReservationIdForSubscription(String subscriptionDescriptorId, UUID subscriptionId, Principal principal) {
        return purchaseContextManager.findBy(PurchaseContextType.subscription, subscriptionDescriptorId)
                .flatMap(purchaseContext -> {
                    accessService.checkOrganizationOwnership(principal, purchaseContext.getOrganizationId());
                    var subscription = subscriptionRepository.findSubscriptionById(subscriptionId);
                    var descriptor = (SubscriptionDescriptor) purchaseContext;
                    if (subscription.getSubscriptionDescriptorId().equals(descriptor.getId()) && StringUtils.isNotBlank(subscription.getReservationId())) {
                        return Optional.of(Pair.of(descriptor, subscription.getReservationId()));
                    }
                    return Optional.empty();
                });
    }

    @Transactional
    public Result<Boolean> removeSubscription(SubscriptionDescriptor descriptor, String reservationId, UUID subscriptionId, String username) {
        int result = subscriptionRepository.cancelSubscription(reservationId, subscriptionId, descriptor.getId());
        markAsCancelled(ticketReservationManager.findById(reservationId).orElseThrow(), username, descriptor);
        return result == 1 ? Result.success(true) : Result.error(ErrorCode.custom("cannot-cancel-subscription", "Cannot cancel subscription"));
    }

    @Transactional(readOnly = true)
    public Result<List<Audit>> getAudit(PurchaseContextType purchaseContextType, String publicIdentifier, String reservationId, String username) {
        // FIXME modify query in order to validate if reservation is present for the PurchaseContext
        return Result.success(auditingRepository.findAllForReservation(reservationId));
    }

    @Transactional(readOnly = true)
    public Result<List<BillingDocument>> getBillingDocuments(String eventName, String reservationId, String username) {
        // FIXME modify query in order to validate if reservation is present for the PurchaseContext
        return Result.success(billingDocumentRepository.findAllByReservationId(reservationId));
    }

    @Transactional(readOnly = true)
    public Result<Pair<BillingDocument, byte[]>> getSingleBillingDocumentAsPdf(PurchaseContextType purchaseContextType, String publicIdentifier, String reservationId, long documentId, String username) {
        // FIXME modify query in order to validate if reservation is present for the PurchaseContext
        return loadReservation(purchaseContextType, publicIdentifier, reservationId, username)
            .map(res -> {
                BillingDocument billingDocument = billingDocumentRepository.findByIdAndReservationId(documentId, reservationId).orElseThrow(IllegalArgumentException::new);
                Function<Map<String, Object>, Optional<byte[]>> pdfGenerator = model -> TemplateProcessor.buildBillingDocumentPdf(billingDocument.getType(), res.getRight(), fileUploadManager, LocaleUtil.forLanguageTag(res.getLeft().getUserLanguage()), templateManager, model, extensionManager);
                Map<String, Object> billingModel = billingDocument.getModel();
                return Pair.of(billingDocument, pdfGenerator.apply(billingModel).orElse(null));
            });
    }

    @Transactional
    public Result<Boolean> invalidateBillingDocument(String reservationId, long documentId, String username) {
        return updateBillingDocumentStatus(reservationId, documentId, username, BillingDocument.Status.NOT_VALID, Audit.EventType.BILLING_DOCUMENT_INVALIDATED);
    }

    @Transactional
    public Result<Boolean> restoreBillingDocument(String reservationId, long documentId, String username) {
        return updateBillingDocumentStatus(reservationId, documentId, username, BillingDocument.Status.VALID, Audit.EventType.BILLING_DOCUMENT_RESTORED);
    }

    private Result<Boolean> updateBillingDocumentStatus(String reservationId, long documentId, String username, BillingDocument.Status status, Audit.EventType eventType) {
        return loadReservation(reservationId).map(res -> {
            Integer userId = userRepository.findIdByUserName(username).orElse(null);
            auditingRepository.insert(reservationId, userId, res.getRight(), eventType, new Date(), RESERVATION, String.valueOf(documentId));
            return billingDocumentRepository.updateStatus(documentId, status, reservationId) == 1;
        });


    }

    @Transactional
    public Result<TransactionAndPaymentInfo> getPaymentInfo(String reservationId) {
        return loadReservation(reservationId)
            .map(res -> paymentManager.getInfo(res.getLeft(), res.getRight()));
    }

    @Transactional
    public Result<Boolean> removeReservation(PurchaseContextType purchaseContextType, String eventName, String reservationId, boolean refund, boolean notify, boolean creditNoteRequested, String username) {
        return loadReservation(purchaseContextType, eventName, reservationId, username)
            .flatMap(result -> new Result.Builder<Pair<PurchaseContext, TicketReservation>>()
                .checkPrecondition(() -> ticketsStatusIsCompatibleWithCancellation(result.getMiddle()), ERROR_CANNOT_CANCEL_CHECKED_IN_TICKETS)
                .buildAndEvaluate(() -> {
                    var reservation = result.getLeft();
                    var refundErrorCode = refundIfRequested(reservation, result.getRight(), username, refund);
                    if(refundErrorCode != null) {
                        return Result.error(refundErrorCode);
                    }
                    if(creditNoteRequested && reservation.getHasInvoiceNumber()) {
                        ticketReservationManager.issueCreditNoteForReservation(result.getRight(), reservation, username, false);
                    }
                    // setting refund to false because we've already done it
                    return removeReservation(result, false, notify, username, true, false);
                }))
            .map(pair -> {
                var purchaseContext = pair.getLeft();
                var ticketReservation = pair.getRight();
                if(!creditNoteRequested || !ticketReservation.getHasInvoiceNumber()) {
                    markAsCancelled(ticketReservation, username, purchaseContext);
                }
                if(purchaseContext.ofType(PurchaseContextType.subscription)) {
                    subscriptionRepository.cancelSubscriptions(reservationId);
                }
                return true;
            });
    }

    @Transactional
    public void creditReservation(PurchaseContextType purchaseContextType, String publicIdentifier, String reservationId, boolean refund, boolean notify, String username) {
        loadReservation(purchaseContextType, publicIdentifier, reservationId, username)
            .ifSuccess(res -> {
                if (res.getLeft().getStatus() == TicketReservationStatus.OFFLINE_PAYMENT) {
                    ticketReservationManager.deleteOfflinePayment(res.getRight().event().orElseThrow(), reservationId, false, true, notify, username);
                } else {
                    removeReservation(res, refund, notify, username, false, true);
                }
            });
    }

    private Result<Pair<PurchaseContext, TicketReservation>> removeReservation(Triple<TicketReservation, List<Ticket>, PurchaseContext> triple,
                                                                               boolean refund,
                                                                               boolean notify,
                                                                               String username,
                                                                               boolean removeReservation,
                                                                               boolean issueCreditNote) {
        return new Result.Builder<Triple<TicketReservation, List<Ticket>, PurchaseContext>>()
            .checkPrecondition(() -> ticketsStatusIsCompatibleWithCancellation(triple.getMiddle()), ERROR_CANNOT_CANCEL_CHECKED_IN_TICKETS)
            .buildAndEvaluate(() -> {
                var pc = triple.getRight();
                var reservation = triple.getLeft();
                var refundErrorCode = refundIfRequested(reservation, pc, username, refund);
                if(refundErrorCode != null) {
                    return Result.error(refundErrorCode);
                }
                return Result.success(triple);
            }).map(t -> {
                var purchaseContext = t.getRight();
                TicketReservation reservation = t.getLeft();
                List<Ticket> tickets = t.getMiddle();
                specialPriceRepository.resetToFreeAndCleanupForReservation(List.of(reservation.getId()));
                if(purchaseContext.ofType(PurchaseContextType.event)) {
                    var event = (Event) purchaseContext;
                    removeTicketsFromReservation(reservation, event, tickets.stream().map(Ticket::getId).collect(toList()), notify, username, removeReservation, issueCreditNote);
                    additionalServiceItemRepository.updateItemsStatusWithReservationUUID(event.getId(), reservation.getId(), AdditionalServiceItem.AdditionalServiceItemStatus.CANCELLED);
                }
                return Pair.of(purchaseContext, reservation);
            });
    }

    private ErrorCode refundIfRequested(TicketReservation reservation, PurchaseContext purchaseContext, String username, boolean requested) {
        if(requested && reservation.getPaymentMethod() != null && reservation.getPaymentMethod().isSupportRefund()) {
            //fully refund
            boolean refundResult = paymentManager.refund(reservation, purchaseContext, null, username);
            if(!refundResult) {
                return ErrorCode.custom("refund.failed", "Cannot perform refund");
            }
        }
        return null;
    }

    private boolean ticketsStatusIsCompatibleWithCancellation(List<Ticket> tickets) {
        return ticketsStatusIsCompatibleWithCancellation(tickets.stream());
    }

    private boolean ticketsStatusIsCompatibleWithCancellation(Stream<Ticket> ticketsStream) {
        return ticketsStream.noneMatch(c -> c.getStatus() == Ticket.TicketStatus.CHECKED_IN);
    }

    @Transactional
    public Result<Boolean> refund(PurchaseContextType purchaseContextType, String publicIdentifier, String reservationId, BigDecimal refundAmount, String username) {
        return loadReservation(purchaseContextType, publicIdentifier, reservationId, username).map(res -> {
            var reservation = res.getLeft();
            var purchaseContext = res.getRight();
            if(reservation.getHasInvoiceNumber()) {
                ticketReservationManager.issueCreditNoteForRefund(purchaseContext, reservation, refundAmount, username);
            }
            return reservation.getPaymentMethod() != null
                && reservation.getPaymentMethod().isSupportRefund()
                && paymentManager.refund(reservation, purchaseContext, unitToCents(refundAmount, reservation.getCurrencyCode()), username);
        });
    }

    @Transactional
    public Result<Boolean> regenerateBillingDocument(PurchaseContextType purchaseContextType, String publicIdentifier, String reservationId, String username) {
        return loadReservation(purchaseContextType, publicIdentifier, reservationId, username).map(res -> {
            var event = res.getRight();
            var reservation = res.getLeft();
            var billingDocument = billingDocumentManager.createBillingDocument(event, reservation, username, ticketReservationManager.orderSummaryForReservation(reservation, event));
            if(billingDocument.getType() != BillingDocument.Type.CREDIT_NOTE) {
                billingDocumentRepository.invalidateAllPreviousDocumentsOfType(billingDocument.getType(), billingDocument.getId(), reservationId);
            }
            return true;
        });
    }


    @Transactional
    public Result<List<LightweightMailMessage>> getEmailsForReservation(PurchaseContextType purchaseContextType, String publicIdentifier, String reservationId, String username) {
        return loadReservation(purchaseContextType, publicIdentifier, reservationId, username)
            .map(res -> notificationManager.loadAllMessagesForReservationId(res.getRight(), reservationId));
    }

    private void removeTicketsFromReservation(TicketReservation reservation,
                                              Event purchaseContext,
                                              List<Integer> ticketIds,
                                              boolean notify,
                                              String username,
                                              boolean removeReservation,
                                              boolean issueCreditNote) {
        String reservationId = reservation.getId();
        if(!ticketIds.isEmpty() && issueCreditNote && reservation.getHasInvoiceNumber()) {
            ticketReservationManager.issuePartialCreditNoteForReservation(purchaseContext, reservation, username, ticketIds);
        }
        if(notify && !ticketIds.isEmpty()) {
            Organization o = eventManager.loadOrganizer(purchaseContext, username);
            ticketRepository.findByIds(ticketIds).forEach(t -> {
                if(StringUtils.isNotBlank(t.getEmail())) {
                    sendTicketHasBeenRemoved(purchaseContext, o, t);
                }
            });
        }

        if(!issueCreditNote || !reservation.getHasInvoiceNumber()) {
            billingDocumentManager.ensureBillingDocumentIsPresent(purchaseContext, reservation, username, () -> ticketReservationManager.orderSummaryForReservation(reservation, purchaseContext));
        }

        Integer userId = userRepository.findIdByUserName(username).orElse(null);
        Date date = new Date();

        ticketIds.forEach(id -> auditingRepository.insert(reservationId, userId, purchaseContext.getId(), CANCEL_TICKET, date, TICKET, id.toString()));

        ticketRepository.resetCategoryIdForUnboundedCategoriesWithTicketIds(ticketIds);
        purchaseContextFieldRepository.deleteAllValuesForTicketIds(ticketIds);
        specialPriceRepository.resetToFreeAndCleanupForTickets(ticketIds);

        List<String> reservationIds = ticketRepository.findReservationIds(ticketIds);
        List<String> ticketUUIDs = ticketRepository.findUUIDs(ticketIds);
        int[] results = ticketRepository.batchReleaseTickets(reservationId, ticketIds, purchaseContext);
        Validate.isTrue(Arrays.stream(results).sum() == ticketIds.size(), "Failed to update tickets");
        if(!removeReservation) {
            extensionManager.handleTicketCancelledForEvent(purchaseContext, ticketUUIDs);
        } else {
            extensionManager.handleReservationsCancelledForEvent(purchaseContext, reservationIds);
        }
    }

    private void sendTicketHasBeenRemoved(Event event, Organization organization, Ticket ticket) {
        Map<String, Object> model = TemplateResource.buildModelForTicketHasBeenCancelled(organization, event, ticket);
        Locale locale = LocaleUtil.forLanguageTag(Optional.ofNullable(ticket.getUserLanguage()).orElse("en"));
        notificationManager.sendSimpleEmail(event, ticket.getTicketsReservationId(), ticket.getEmail(),
            messageSourceManager.getMessageSourceFor(event).getMessage("email-ticket-released.subject",
            new Object[]{event.getDisplayName()}, locale),
            () -> templateManager.renderTemplate(event, TemplateResource.TICKET_HAS_BEEN_CANCELLED, model, locale));
    }

    private void markAsCancelled(TicketReservation ticketReservation, String username, PurchaseContext purchaseContext) {
        markAsCancelled(ticketReservation.getId(), username, purchaseContext);
    }

    private void markAsCancelled(String reservationId, String username, PurchaseContext purchaseContext) {
        ticketReservationRepository.updateReservationStatus(reservationId, TicketReservationStatus.CANCELLED.toString());
        auditingRepository.insert(reservationId, userRepository.nullSafeFindIdByUserName(username).orElse(null),
            purchaseContext, Audit.EventType.CANCEL_RESERVATION, new Date(), Audit.EntityType.RESERVATION, reservationId);
    }

    private void handleTicketsRefund(List<Integer> toRefund, Event e, TicketReservation reservation, Map<Integer, Ticket> ticketsById, String username) {
        if(reservation.getPaymentMethod() == null || !reservation.getPaymentMethod().isSupportRefund()) {
            return;
        }
        // refund each selected ticket
        for(Integer toRefundId : toRefund) {
            int toBeRefunded = ticketsById.get(toRefundId).getFinalPriceCts();
            if(toBeRefunded > 0) {
                paymentManager.refund(reservation, e, toBeRefunded, username);
            }
        }
        //
    }
}
