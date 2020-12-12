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
import static alfio.model.Audit.EntityType.RESERVATION;
import static alfio.model.Audit.EventType.EXTERNAL_INVOICE_NUMBER;
import static alfio.model.Audit.EventType.INIT_PAYMENT;
import static alfio.model.Audit.EventType.MATCHING_PAYMENT_FOUND;
import static alfio.model.Audit.EventType.PAYMENT_CONFIRMED;
import static alfio.model.Audit.EventType.RESET_PAYMENT;
import static alfio.model.BillingDocument.Type.CREDIT_NOTE;
import static alfio.model.BillingDocument.Type.INVOICE;
import static alfio.model.BillingDocument.Type.RECEIPT;
import static alfio.model.PromoCodeDiscount.categoriesOrNull;
import static alfio.model.TicketReservation.TicketReservationStatus.COMPLETE;
import static alfio.model.TicketReservation.TicketReservationStatus.DEFERRED_OFFLINE_PAYMENT;
import static alfio.model.TicketReservation.TicketReservationStatus.EXTERNAL_PROCESSING_PAYMENT;
import static alfio.model.TicketReservation.TicketReservationStatus.IN_PAYMENT;
import static alfio.model.TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT;
import static alfio.model.TicketReservation.TicketReservationStatus.PENDING;
import static alfio.model.TicketReservation.TicketReservationStatus.WAITING_EXTERNAL_CONFIRMATION;
import static alfio.model.system.ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION;
import static alfio.model.system.ConfigurationKeys.ASSIGNMENT_REMINDER_INTERVAL;
import static alfio.model.system.ConfigurationKeys.ASSIGNMENT_REMINDER_START;
import static alfio.model.system.ConfigurationKeys.AUTOMATIC_REMOVAL_EXPIRED_OFFLINE_PAYMENT;
import static alfio.model.system.ConfigurationKeys.BANK_ACCOUNT_NR;
import static alfio.model.system.ConfigurationKeys.BANK_ACCOUNT_OWNER;
import static alfio.model.system.ConfigurationKeys.BASE_URL;
import static alfio.model.system.ConfigurationKeys.DEFERRED_BANK_TRANSFER_ENABLED;
import static alfio.model.system.ConfigurationKeys.DEFERRED_BANK_TRANSFER_SEND_CONFIRMATION_EMAIL;
import static alfio.model.system.ConfigurationKeys.ENABLE_TICKET_TRANSFER;
import static alfio.model.system.ConfigurationKeys.INVOICE_ADDRESS;
import static alfio.model.system.ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION;
import static alfio.model.system.ConfigurationKeys.NOTIFY_ALL_FAILED_PAYMENT_ATTEMPTS;
import static alfio.model.system.ConfigurationKeys.OFFLINE_REMINDER_HOURS;
import static alfio.model.system.ConfigurationKeys.OPTIONAL_DATA_REMINDER_ENABLED;
import static alfio.model.system.ConfigurationKeys.RESERVATION_MIN_TIMEOUT_AFTER_FAILED_PAYMENT;
import static alfio.model.system.ConfigurationKeys.RESERVATION_TIMEOUT;
import static alfio.model.system.ConfigurationKeys.SEND_TICKETS_AUTOMATICALLY;
import static alfio.model.system.ConfigurationKeys.VAT_NR;
import static alfio.util.MonetaryUtil.formatCents;
import static alfio.util.MonetaryUtil.unitToCents;
import static alfio.util.Wrappers.optionally;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.summingLong;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.stripAll;
import static org.apache.commons.lang3.StringUtils.stripToEmpty;
import static org.apache.commons.lang3.StringUtils.stripToNull;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.apache.commons.lang3.time.DateUtils.addHours;
import static org.apache.commons.lang3.time.DateUtils.truncate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import alfio.model.metadata.AlfioMetadata;
import alfio.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.BindingResult;
import org.springframework.web.util.UriComponentsBuilder;
import alfio.controller.api.support.TicketHelper;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.support.TemplateProcessor;
import alfio.manager.PaymentManager.PaymentMethodDTO.PaymentMethodStatus;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.payment.BankTransferManager;
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.support.*;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.Mailer;
import alfio.manager.system.ReservationPriceCalculator;
import alfio.model.*;
import alfio.model.AdditionalServiceItem.AdditionalServiceItemStatus;
import alfio.model.PromoCodeDiscount.CodeType;
import alfio.model.PromoCodeDiscount.DiscountType;
import alfio.model.SpecialPrice.Status;
import alfio.model.SummaryRow.SummaryType;
import alfio.model.Ticket.TicketStatus;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.checkin.OnlineCheckInFullInfo;
import alfio.model.decorator.AdditionalServiceItemPriceContainer;
import alfio.model.decorator.AdditionalServicePriceContainer;
import alfio.model.decorator.TicketPriceContainer;
import alfio.model.extension.CustomEmailText;
import alfio.model.group.LinkedGroup;
import alfio.model.modification.ASReservationWithOptionalCodeModification;
import alfio.model.modification.AdditionalServiceReservationModification;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.OfflineProcessor;
import alfio.model.transaction.capabilities.ServerInitiatedTransaction;
import alfio.model.transaction.capabilities.WebhookHandler;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.util.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.BindingResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static alfio.model.Audit.EntityType.RESERVATION;
import static alfio.model.Audit.EventType.*;
import static alfio.model.BillingDocument.Type.*;
import static alfio.model.PromoCodeDiscount.categoriesOrNull;
import static alfio.model.TicketReservation.TicketReservationStatus.*;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.MonetaryUtil.formatCents;
import static alfio.util.MonetaryUtil.unitToCents;
import static alfio.util.Wrappers.optionally;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.*;
import static org.apache.commons.lang3.StringUtils.*;
import static org.apache.commons.lang3.time.DateUtils.addHours;
import static org.apache.commons.lang3.time.DateUtils.truncate;

@Component
@Transactional
@Log4j2
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class TicketReservationManager {
    
    public static final String NOT_YET_PAID_TRANSACTION_ID = "not-paid";
    private static final String STUCK_TICKETS_MSG = "there are stuck tickets for the event %s. Please check admin area.";
    private static final String STUCK_TICKETS_SUBJECT = "warning: stuck tickets found";

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
    private final MessageSourceManager messageSourceManager;
    private final TemplateManager templateManager;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final TransactionTemplate serializedTransactionTemplate;
    private final TransactionTemplate nestedTransactionTemplate;
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
    private final GroupManager groupManager;
    private final BillingDocumentRepository billingDocumentRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Json json;
    private final BillingDocumentManager billingDocumentManager;
    private final EventManager eventManager;
    private final ClockProvider clockProvider;

    public static class NotEnoughTicketsException extends RuntimeException {

    }

    public static class MissingSpecialPriceTokenException extends RuntimeException {
    }

    public static class InvalidSpecialPriceTokenException extends RuntimeException {

    }

    public static class TooManyTicketsForDiscountCodeException extends RuntimeException {
    }

    public static class CannotProceedWithPayment extends RuntimeException {
        CannotProceedWithPayment(String message) {
            super(message);
        }
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
                                    MessageSourceManager messageSourceManager,
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
                                    ExtensionManager extensionManager, TicketSearchRepository ticketSearchRepository,
                                    GroupManager groupManager,
                                    BillingDocumentRepository billingDocumentRepository,
                                    NamedParameterJdbcTemplate jdbcTemplate,
                                    Json json,
                                    BillingDocumentManager billingDocumentManager,
                                    EventManager eventManager,
                                    ClockProvider clockProvider) {

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
        this.messageSourceManager = messageSourceManager;
        this.templateManager = templateManager;
        this.waitingQueueManager = waitingQueueManager;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager, new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        DefaultTransactionDefinition serialized = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        serialized.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        this.serializedTransactionTemplate = new TransactionTemplate(transactionManager, serialized);
        this.nestedTransactionTemplate = new TransactionTemplate(transactionManager, new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_NESTED));
        this.ticketFieldRepository = ticketFieldRepository;
        this.additionalServiceRepository = additionalServiceRepository;
        this.additionalServiceItemRepository = additionalServiceItemRepository;
        this.additionalServiceTextRepository = additionalServiceTextRepository;
        this.invoiceSequencesRepository = invoiceSequencesRepository;
        this.auditingRepository = auditingRepository;
        this.userRepository = userRepository;
        this.extensionManager = extensionManager;
        this.ticketSearchRepository = ticketSearchRepository;
        this.groupManager = groupManager;
        this.billingDocumentRepository = billingDocumentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.json = json;
        this.billingDocumentManager = billingDocumentManager;
        this.eventManager = eventManager;
        this.clockProvider = clockProvider;
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
                                          Optional<String> promotionCodeDiscount,
                                          Locale locale,
                                          boolean forWaitingQueue) throws NotEnoughTicketsException, MissingSpecialPriceTokenException, InvalidSpecialPriceTokenException {
        String reservationId = UUID.randomUUID().toString();

        Optional<PromoCodeDiscount> tmpDiscount = promotionCodeDiscount
            .flatMap(promoCodeDiscount -> promoCodeDiscountRepository.findPromoCodeInEventOrOrganization(event.getId(), promoCodeDiscount));
        tmpDiscount = checkPromoCodeIsValid(tmpDiscount,event);
        Optional<PromoCodeDiscount> dynamicDiscount = createDynamicPromoCode(tmpDiscount, event, list, reservationId);

        Optional<PromoCodeDiscount> discount = tmpDiscount;

        ticketReservationRepository.createNewReservation(reservationId,
            event.now(clockProvider),
            reservationExpiration, dynamicDiscount.or(() -> discount).map(PromoCodeDiscount::getId).orElse(null),
            locale.getLanguage(),
            event.getId(),
            event.getVat(),
            event.isVatIncluded(),
            event.getCurrency());
        list.forEach(t -> reserveTicketsForCategory(event, reservationId, t, locale, forWaitingQueue, discount.orElse(null), dynamicDiscount.orElse(null)));

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
        var totalPrice = totalReservationCostWithVAT(reservationId).getLeft();
        var vatStatus = event.getVatStatus();
        ticketReservationRepository.updateBillingData(event.getVatStatus(), calculateSrcPrice(vatStatus, totalPrice), totalPrice.getPriceWithVAT(), totalPrice.getVAT(), Math.abs(totalPrice.getDiscount()), event.getCurrency(), null, null, false, reservationId);
        auditingRepository.insert(reservationId, null, event.getId(), Audit.EventType.RESERVATION_CREATE, new Date(), Audit.EntityType.RESERVATION, reservationId);
        if(isDiscountCodeUsageExceeded(reservationId)) {
            throw new TooManyTicketsForDiscountCodeException();
        }

        if(!canProceedWithPayment(event, totalPrice, reservationId)) {
            throw new CannotProceedWithPayment("No payment method applicable for categories "+list.stream().map(t -> String.valueOf(t.getTicketCategoryId())).collect(Collectors.joining(", ")));
        }

        return reservationId;
    }

    private Optional<PromoCodeDiscount> createDynamicPromoCode(Optional<PromoCodeDiscount> existingDiscount, Event event, List<TicketReservationWithOptionalCodeModification> list, String reservationId) {
        if(existingDiscount.filter(dt -> dt.getCodeType() != CodeType.ACCESS).isEmpty()) {
            return createDynamicPromoCodeIfNeeded(event, list, reservationId)
                .flatMap(promoCodeDiscount -> promoCodeDiscountRepository.findPromoCodeInEventOrOrganization(event.getId(), promoCodeDiscount));
        }
        return existingDiscount;
    }

    Optional<String> createDynamicPromoCodeIfNeeded(Event event, List<TicketReservationWithOptionalCodeModification> list, String reservationId) {

        var paidCategories = ticketCategoryRepository.countPaidCategoriesInReservation(list.stream().map(TicketReservationWithOptionalCodeModification::getTicketCategoryId).collect(Collectors.toSet()));
        if(paidCategories == null || paidCategories == 0) {
            return Optional.empty();
        }

        var newCodeOptional = extensionManager.handleDynamicDiscount(event, list.stream().collect(groupingBy(TicketReservationWithOptionalCodeModification::getTicketCategoryId, summingLong(TicketReservationWithOptionalCodeModification::getAmount))), reservationId);
        if(newCodeOptional.isPresent()) {
            var newCode = newCodeOptional.get();
            int result = promoCodeDiscountRepository.addPromoCodeIfNotExists(newCode.getPromoCode(), event.getId(),
                event.getOrganizationId(), newCode.getUtcStart(), newCode.getUtcEnd(), newCode.getDiscountAmount(),
                newCode.getDiscountType(), "[]", null, null, null, newCode.getCodeType(), null);
            if(result > 0) {
                auditingRepository.insert(reservationId, null, event.getId(), Audit.EventType.DYNAMIC_DISCOUNT_CODE_CREATED, new Date(), Audit.EntityType.RESERVATION, reservationId);
            }
        }
        return newCodeOptional.map(PromoCodeDiscount::getPromoCode);
    }

    private int calculateSrcPrice(PriceContainer.VatStatus vatStatus, TotalPrice totalPrice) {
        return (vatStatus == PriceContainer.VatStatus.INCLUDED ? totalPrice.getPriceWithVAT() : totalPrice.getPriceWithVAT() - totalPrice.getVAT())
            + Math.abs(totalPrice.getDiscount());
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

    void reserveTicketsForCategory(Event event,
                                   String reservationId,
                                   TicketReservationWithOptionalCodeModification ticketReservation,
                                   Locale locale,
                                   boolean forWaitingQueue,
                                   PromoCodeDiscount accessCodeOrDiscount,
                                   PromoCodeDiscount dynamicDiscount) {

        List<SpecialPrice> specialPrices;
        if(accessCodeOrDiscount != null && accessCodeOrDiscount.getCodeType() == CodeType.ACCESS
            && ticketReservation.getTicketCategoryId().equals(accessCodeOrDiscount.getHiddenCategoryId())
            && ticketCategoryRepository.isAccessRestricted(accessCodeOrDiscount.getHiddenCategoryId())
        ) {
            specialPrices = reserveTokensForAccessCode(ticketReservation, accessCodeOrDiscount);
        } else {
            //first check if there is another pending special price token bound to the current sessionId
            Optional<SpecialPrice> specialPrice = fixToken(ticketReservation.getSpecialPrice(), ticketReservation.getTicketCategoryId(), event.getId(), ticketReservation);
            specialPrices = specialPrice.stream().collect(toList());
        }

        List<Integer> reservedForUpdate = reserveTickets(event.getId(), ticketReservation, forWaitingQueue ? asList(TicketStatus.RELEASED, TicketStatus.PRE_RESERVED) : singletonList(TicketStatus.FREE));
        int requested = ticketReservation.getAmount();
        if (reservedForUpdate.size() != requested) {
            throw new NotEnoughTicketsException();
        }

        TicketCategory category = ticketCategoryRepository.getByIdAndActive(ticketReservation.getTicketCategoryId(), event.getId());
        if (!specialPrices.isEmpty()) {
            if(specialPrices.size() != reservedForUpdate.size()) {
                throw new NotEnoughTicketsException();
            }

            AtomicInteger counter = new AtomicInteger(0);
            var ticketsAndSpecialPrices = specialPrices.stream().map(sp -> Pair.of(reservedForUpdate.get(counter.getAndIncrement()), sp)).collect(Collectors.toList());

            if(specialPrices.size() == 1) {
                var ticketId = reservedForUpdate.get(0);
                var sp = specialPrices.get(0);
                var accessCodeId = accessCodeOrDiscount != null && accessCodeOrDiscount.getHiddenCategoryId() != null ? accessCodeOrDiscount.getId() : null;
                ticketRepository.reserveTicket(reservationId, ticketId,sp.getId(), locale.getLanguage(), category.getSrcPriceCts(), category.getCurrencyCode());
                specialPriceRepository.updateStatus(sp.getId(), Status.PENDING.toString(), null, accessCodeId);
            } else {
                jdbcTemplate.batchUpdate(ticketRepository.batchReserveTicket(), ticketsAndSpecialPrices.stream().map(
                    pair -> new MapSqlParameterSource("reservationId", reservationId)
                        .addValue("ticketId", pair.getKey())
                        .addValue("specialCodeId", pair.getValue().getId())
                        .addValue("userLanguage", locale.getLanguage())
                        .addValue("srcPriceCts", category.getSrcPriceCts())
                        .addValue("currencyCode", category.getCurrencyCode())
                ).toArray(MapSqlParameterSource[]::new));
                specialPriceRepository.batchUpdateStatus(
                    specialPrices.stream().map(SpecialPrice::getId).collect(toList()),
                    Status.PENDING,
                    Objects.requireNonNull(accessCodeOrDiscount).getId());
            }
        } else {
            ticketRepository.reserveTickets(reservationId, reservedForUpdate, ticketReservation.getTicketCategoryId(), locale.getLanguage(), category.getSrcPriceCts(), category.getCurrencyCode());
        }
        Ticket ticket = ticketRepository.findById(reservedForUpdate.get(0), category.getId());
        var discountToApply = ObjectUtils.firstNonNull(dynamicDiscount, accessCodeOrDiscount);
        TicketPriceContainer priceContainer = TicketPriceContainer.from(ticket, null, event.getVat(), event.getVatStatus(), discountToApply);
        var currencyCode = priceContainer.getCurrencyCode();
        ticketRepository.updateTicketPrice(reservedForUpdate,
            category.getId(),
            event.getId(),
            category.getSrcPriceCts(),
            MonetaryUtil.unitToCents(priceContainer.getFinalPrice(), currencyCode),
            MonetaryUtil.unitToCents(priceContainer.getVAT(), currencyCode),
            MonetaryUtil.unitToCents(priceContainer.getAppliedDiscount(), currencyCode),
            category.getCurrencyCode());
    }

    List<SpecialPrice> reserveTokensForAccessCode(TicketReservationWithOptionalCodeModification ticketReservation, PromoCodeDiscount accessCode) {
        try {
            // since we're going to get some tokens for an access code, we lock the access code itself until we're done.
            // This will allow us to serialize the requests and limit the contention
            Validate.isTrue(promoCodeDiscountRepository.lockAccessCodeForUpdate(accessCode.getId()).equals(accessCode.getId()));
            List<SpecialPrice> boundSpecialPrices = specialPriceRepository.bindToAccessCode(ticketReservation.getTicketCategoryId(), accessCode.getId(), ticketReservation.getAmount());
            if(boundSpecialPrices.size() != ticketReservation.getAmount()) {
                throw new NotEnoughTicketsException();
            }
            return boundSpecialPrices;
        } catch (Exception e) {
            log.trace("constraints violated", e);
            if(e instanceof NotEnoughTicketsException) {
                throw e;
            }
            throw new TooManyTicketsForDiscountCodeException();
        }
    }

    private void reserveAdditionalServicesForReservation(int eventId, String transactionId, ASReservationWithOptionalCodeModification additionalServiceReservation, PromoCodeDiscount discount) {
        Optional.ofNullable(additionalServiceReservation.getAdditionalServiceId())
            .flatMap(id -> additionalServiceRepository.getOptionalById(id, eventId))
            .filter(as -> additionalServiceReservation.getQuantity() > 0 && (as.isFixPrice() || Optional.ofNullable(additionalServiceReservation.getAmount()).filter(a -> a.compareTo(BigDecimal.ZERO) > 0).isPresent()))
            .map(as -> Pair.of(eventRepository.findById(eventId), as))
            .ifPresent(pair -> {
                Event e = pair.getKey();
                AdditionalService as = pair.getValue();
                IntStream.range(0, additionalServiceReservation.getQuantity())
                    .forEach(i -> {
                        AdditionalServicePriceContainer pc = AdditionalServicePriceContainer.from(additionalServiceReservation.getAmount(), as, e, discount);
                        var currencyCode = pc.getCurrencyCode();
                        additionalServiceItemRepository.insert(UUID.randomUUID().toString(),
                            ZonedDateTime.now(clockProvider.getClock()),
                            transactionId,
                            as.getId(),
                            AdditionalServiceItemStatus.PENDING,
                            eventId,
                            pc.getSrcPriceCts(),
                            unitToCents(pc.getFinalPrice(), currencyCode),
                            unitToCents(pc.getVAT(), currencyCode),
                            unitToCents(pc.getAppliedDiscount(), currencyCode),
                            as.getCurrencyCode());
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
            return ticketRepository.selectTicketInCategoryForUpdateSkipLocked(eventId, categoryId, qty, statusesAsString);
        }
        return ticketRepository.selectNotAllocatedTicketsForUpdateSkipLocked(eventId, qty, statusesAsString);
    }

    Optional<SpecialPrice> fixToken(Optional<SpecialPrice> token, int ticketCategoryId, int eventId, TicketReservationWithOptionalCodeModification ticketReservation) {

        TicketCategory ticketCategory = ticketCategoryRepository.getByIdAndActive(ticketCategoryId, eventId);
        if(!ticketCategory.isAccessRestricted()) {
            return Optional.empty();
        }

        Optional<SpecialPrice> specialPrice = token.map(SpecialPrice::getCode).flatMap(specialPriceRepository::getForUpdateByCode);

        if(token.isPresent() && specialPrice.isEmpty()) {
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

    public PaymentResult performPayment(PaymentSpecification spec,
                                        TotalPrice reservationCost,
                                        PaymentProxy proxy,
                                        PaymentMethod paymentMethod) {
        PaymentProxy paymentProxy = evaluatePaymentProxy(proxy, reservationCost);

        if(!acquireGroupMembers(spec.getReservationId(), spec.getEvent())) {
            groupManager.deleteWhitelistedTicketsForReservation(spec.getReservationId());
            return PaymentResult.failed("error.STEP2_WHITELIST");
        }

        if(paymentMethodIsBlacklisted(paymentMethod, spec)) {
            log.warn("payment method {} forbidden for reservationId {}", paymentMethod, spec.getReservationId());
            return PaymentResult.failed("error.STEP2_UNABLE_TO_TRANSITION");
        }

        if(!initPaymentProcess(reservationCost, paymentProxy, spec)) {
            return PaymentResult.failed("error.STEP2_UNABLE_TO_TRANSITION");
        }

        TicketReservation reservation = null;

        try {
            PaymentResult paymentResult;
            reservation = ticketReservationRepository.findReservationByIdForUpdate(spec.getReservationId());
            if(reservation.getStatus() == COMPLETE) {
                return PaymentResult.successful("");
            }
            //save billing data in case we have to go back to PENDING
            ticketReservationRepository.updateBillingData(spec.getVatStatus(), reservation.getSrcPriceCts(), reservation.getFinalPriceCts(),
                reservation.getVatCts(), reservation.getDiscountCts(), reservation.getCurrencyCode(), spec.getVatNr(), spec.getVatCountryCode(), spec.isInvoiceRequested(), spec.getReservationId());
            if(isDiscountCodeUsageExceeded(spec.getReservationId())) {
                return PaymentResult.failed(ErrorsCode.STEP_2_DISCOUNT_CODE_USAGE_EXCEEDED);
            }
            if(reservationCost.requiresPayment()) {
                var transactionRequest = new TransactionRequest(reservationCost, ticketReservationRepository.getBillingDetailsForReservation(spec.getReservationId()));
                PaymentContext paymentContext = spec.getPaymentContext();
                paymentResult = paymentManager.streamActiveProvidersByProxy(paymentProxy, paymentContext)
                    .filter(paymentProvider -> paymentProvider.accept(paymentMethod, paymentContext, transactionRequest))
                    .findFirst()
                    .map(paymentProvider -> paymentProvider.getTokenAndPay(spec))
                    .orElseGet(() -> PaymentResult.failed("error.STEP2_STRIPE_unexpected"));
            } else {
                paymentResult = PaymentResult.successful(NOT_YET_PAID_TRANSACTION_ID);
            }

            if (paymentResult.isSuccessful()) {

                reservation = ticketReservationRepository.findReservationById(spec.getReservationId());
                transitionToComplete(spec, reservationCost, paymentProxy, null);
            } else if(paymentResult.isFailed()) {
                reTransitionToPending(spec.getReservationId());
            }
            return paymentResult;
        } catch(Exception ex) {
            if(reservation != null && reservation.getStatus() != IN_PAYMENT) {
                reTransitionToPending(spec.getReservationId());
            }
            //it is guaranteed that in this case we're dealing with "local" error (e.g. database failure),
            //thus it is safer to not rollback the reservation status
            log.error("unexpected error during payment confirmation", ex);
            return PaymentResult.failed("error.STEP2_STRIPE_unexpected");
        }

    }

    public void managePromoCodeForCarnetEvent(Event event, TicketReservation ticketReservation) {
        var mDataSrc = eventRepository.findEventMetadataByIdMatchAttribute(event.getId(), Event.EventOccurrence.CARNET.toString());
        mDataSrc.ifPresent(alfioMetadata -> {
            //I have to add the promo code
            if (alfioMetadata.getAttributes() != null && alfioMetadata.getAttributes().get(Event.EventOccurrence.CARNET.toString())!=null) {
                int discount = -1;
                try {
                    discount = Integer.parseInt(alfioMetadata.getAttributes().get(Event.EventOccurrence.CARNET.toString()).toString());
                } catch (ClassCastException e) {
                    discount = -1;
                }

                for (Integer ticketId : ticketRepository.findTicketIdsInReservation(ticketReservation.getId())){
                    //generating 1 vuocher for ticket
                    var attributeList = new HashMap<String, Object>();
                    attributeList.put("idTicket", ticketId);
                    attributeList.put("idEvent", event.getId());
                    attributeList.put("eventShortName", event.getShortName());
                    attributeList.put("eventDisplayName", event.getDisplayName());
                    attributeList.put("promoCodeType", Event.EventOccurrence.CARNET.toString());
                    attributeList.put("buyerName", ticketReservation.getFullName());
                    var metadata = new AlfioMetadata(
                        alfioMetadata.getTags(),
                        null,
                        Map.of(),
                        List.of(),
                        attributeList);

                    var promoCode = VoucherGenerator.generateRandomVoucher();
                    // the promo code will be binded to the organization
                    if (discount != -1) {
                        eventManager.addPromoCode(
                            promoCode,
                            null,
                            event.getOrganizationId(),
                            event.getBegin(),
                            event.getEnd(),
                            100,
                            PromoCodeDiscount.DiscountType.PERCENTAGE,
                            null, discount,
                            event.getDisplayName(),
                            ticketReservation.getEmail(),
                            PromoCodeDiscount.CodeType.DISCOUNT,
                            null,
                            metadata);
                    }
                }
            }
        });
    }

    private boolean paymentMethodIsBlacklisted(PaymentMethod paymentMethod, PaymentSpecification spec) {
        return configurationManager.getBlacklistedMethodsForReservation(spec.getEvent(), findCategoryIdsInReservation(spec.getReservationId()))
            .stream().anyMatch(m -> m == paymentMethod);
    }

    public Collection<Integer> findCategoryIdsInReservation(String reservationId) {
        return findTicketsInReservation(reservationId)
            .stream()
            .map(Ticket::getCategoryId)
            .collect(Collectors.toSet());
    }

    public boolean cancelPendingPayment(String reservationId, Event event) {
        var optionalReservation = findById(reservationId);
        if(optionalReservation.isEmpty()) {
            return false;
        }
        var optionalTransaction = transactionRepository.loadOptionalByReservationId(reservationId);
        if(optionalTransaction.isEmpty() || optionalTransaction.get().getStatus() != Transaction.Status.PENDING) {
            log.warn("Trying to cancel a non-pending transaction for reservation {}", reservationId);
            return false;
        }
        Transaction transaction = optionalTransaction.get();
        boolean remoteDeleteResult = paymentManager.lookupProviderByTransactionAndCapabilities(transaction, List.of(ServerInitiatedTransaction.class))
            .map(provider -> ((ServerInitiatedTransaction)provider).discardTransaction(optionalTransaction.get(), event))
            .orElse(true);

        if(remoteDeleteResult) {
            reTransitionToPending(reservationId);
            auditingRepository.insert(reservationId, null, event.getId(), RESET_PAYMENT, new Date(), RESERVATION, reservationId);
            return true;
        }
        log.warn("Cannot delete payment with ID {} for reservation {}", transaction.getPaymentId(), reservationId);
        return false;
    }

    private void transitionToComplete(PaymentSpecification spec, TotalPrice reservationCost, PaymentProxy paymentProxy, String username) {
        var status = ticketReservationRepository.findOptionalStatusAndValidationById(spec.getReservationId()).orElseThrow().getStatus();
        if(status != COMPLETE) {
            generateInvoiceNumber(spec, reservationCost);
            completeReservation(spec, paymentProxy, true, true, username);
        }
    }

    private void generateInvoiceNumber(PaymentSpecification spec, TotalPrice reservationCost) {
        if(!reservationCost.requiresPayment() || !spec.isInvoiceRequested() || !configurationManager.hasAllConfigurationsForInvoice(spec.getEvent())) {
            return;
        }

        String reservationId = spec.getReservationId();
        var optionalInvoiceNumber = extensionManager.handleInvoiceGeneration(spec, reservationCost, ticketReservationRepository.getBillingDetailsForReservation(reservationId))
            .flatMap(invoiceGeneration -> Optional.ofNullable(trimToNull(invoiceGeneration.getInvoiceNumber())));

        optionalInvoiceNumber.ifPresent(invoiceNumber -> {
            List<Map<String, Object>> modifications = List.of(Map.of("invoiceNumber", invoiceNumber));
            auditingRepository.insert(reservationId, null, spec.getEvent().getId(), EXTERNAL_INVOICE_NUMBER, new Date(), RESERVATION, reservationId, modifications);
        });

        String invoiceNumber = optionalInvoiceNumber.orElseGet(() -> {
                int invoiceSequence = invoiceSequencesRepository.lockReservationForUpdate(spec.getEvent().getOrganizationId());
                invoiceSequencesRepository.incrementSequenceFor(spec.getEvent().getOrganizationId());
                String pattern = configurationManager
                    .getFor(ConfigurationKeys.INVOICE_NUMBER_PATTERN, ConfigurationLevel.event(spec.getEvent()))
                    .getValueOrDefault("%d");
                return String.format(ObjectUtils.firstNonNull(StringUtils.trimToNull(pattern), "%d"), invoiceSequence);
        });

        ticketReservationRepository.setInvoiceNumber(reservationId, invoiceNumber);
    }

    private boolean isDiscountCodeUsageExceeded(String reservationId) {
        TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
        if(reservation.getPromoCodeDiscountId() != null) {
            final PromoCodeDiscount promoCode = promoCodeDiscountRepository.findById(reservation.getPromoCodeDiscountId());
            if(promoCode.getMaxUsage() == null) {
                return false;
            }
            int currentTickets = ticketReservationRepository.countTicketsInReservationForCategories(reservationId, categoriesOrNull(promoCode));
            return Boolean.TRUE.equals(serializedTransactionTemplate.execute(status -> {
                Integer confirmedPromoCode = promoCodeDiscountRepository.countConfirmedPromoCode(promoCode.getId(), categoriesOrNull(promoCode), reservationId, categoriesOrNull(promoCode) != null ? "X" : null);
                return promoCode.getMaxUsage() < currentTickets + confirmedPromoCode;
            }));
        }
        return false;
    }

    public boolean containsCategoriesLinkedToGroups(String reservationId, int eventId) {
        List<LinkedGroup> allLinks = groupManager.getLinksForEvent(eventId);
        if(allLinks.isEmpty()) {
            return false;
        }
        return ticketRepository.findTicketsInReservation(reservationId).stream()
            .anyMatch(t -> allLinks.stream().anyMatch(lg -> lg.getTicketCategoryId() == null || lg.getTicketCategoryId().equals(t.getCategoryId())));
    }

    private PaymentProxy evaluatePaymentProxy(PaymentProxy proxy, TotalPrice reservationCost) {
        if(proxy != null) {
            return proxy;
        }
        if(reservationCost.getPriceWithVAT() == 0) {
            return PaymentProxy.NONE;
        }
        return PaymentProxy.STRIPE;
    }

    private boolean initPaymentProcess(TotalPrice reservationCost, PaymentProxy paymentProxy, PaymentSpecification spec) {
        if(reservationCost.getPriceWithVAT() > 0 && paymentProxy == PaymentProxy.STRIPE) {
            try {
                transitionToInPayment(spec);
            } catch (Exception e) {
                //unable to do the transition. Exiting.
                log.debug(String.format("unable to flag the reservation %s as IN_PAYMENT", spec.getReservationId()), e);
                return false;
            }
        }
        return true;
    }

    private boolean acquireGroupMembers(String reservationId, EventAndOrganizationId event) {
        List<LinkedGroup> linkedGroups = groupManager.getLinksForEvent(event.getId());
        if(!linkedGroups.isEmpty()) {
            List<Ticket> ticketsInReservation = ticketRepository.findTicketsInReservation(reservationId);
            return Boolean.TRUE.equals(requiresNewTransactionTemplate.execute(status ->
                ticketsInReservation
                    .stream()
                    .filter(ticket -> linkedGroups.stream().anyMatch(c -> c.getTicketCategoryId() == null || c.getTicketCategoryId().equals(ticket.getCategoryId())))
                    .map(groupManager::acquireMemberForTicket)
                    .reduce(true, Boolean::logicalAnd)));
        }
        return true;
    }

    public void confirmOfflinePayment(Event event, String reservationId, String username) {
        TicketReservation ticketReservation = findById(reservationId).orElseThrow(IllegalArgumentException::new);
        ticketReservationRepository.lockReservationForUpdate(reservationId);
        Validate.isTrue(ticketReservation.getPaymentMethod() == PaymentProxy.OFFLINE, "invalid payment method");
        Validate.isTrue(ticketReservation.isPendingOfflinePayment(), "invalid status");


        ticketReservationRepository.confirmOfflinePayment(reservationId, TicketReservationStatus.COMPLETE.name(), event.now(clockProvider));

        registerAlfioTransaction(event, reservationId, PaymentProxy.OFFLINE);

        auditingRepository.insert(reservationId, userRepository.findIdByUserName(username).orElse(null), event.getId(), Audit.EventType.RESERVATION_OFFLINE_PAYMENT_CONFIRMED, new Date(), Audit.EntityType.RESERVATION, ticketReservation.getId());

        CustomerName customerName = new CustomerName(ticketReservation.getFullName(), ticketReservation.getFirstName(), ticketReservation.getLastName(), event.mustUseFirstAndLastName());
        acquireItems(PaymentProxy.OFFLINE, reservationId, ticketReservation.getEmail(), customerName,
            ticketReservation.getUserLanguage(), ticketReservation.getBillingAddress(),
            ticketReservation.getCustomerReference(), event, true);

        Locale language = findReservationLanguage(reservationId);

        final TicketReservation finalReservation = ticketReservationRepository.findReservationById(reservationId);
        billingDocumentManager.createBillingDocument(event, finalReservation, username, orderSummaryForReservation(finalReservation, event));
        var configuration = configurationManager.getFor(EnumSet.of(DEFERRED_BANK_TRANSFER_ENABLED, DEFERRED_BANK_TRANSFER_SEND_CONFIRMATION_EMAIL), ConfigurationLevel.event(event));
        if(!configuration.get(DEFERRED_BANK_TRANSFER_ENABLED).getValueAsBooleanOrDefault() || configuration.get(DEFERRED_BANK_TRANSFER_SEND_CONFIRMATION_EMAIL).getValueAsBooleanOrDefault()) {
            sendConfirmationEmail(event, findById(reservationId).orElseThrow(IllegalArgumentException::new), language, username);
        }
        extensionManager.handleReservationConfirmation(finalReservation, ticketReservationRepository.getBillingDetailsForReservation(reservationId), event.getId());
    }

    void registerAlfioTransaction(Event event, String reservationId, PaymentProxy paymentProxy) {
        var totalPrice = totalReservationCostWithVAT(reservationId).getLeft();
        int priceWithVAT = totalPrice.getPriceWithVAT();
        long platformFee = FeeCalculator.getCalculator(event, configurationManager, Objects.requireNonNullElse(totalPrice.getCurrencyCode(), event.getCurrency()))
            .apply(ticketRepository.countTicketsInReservation(reservationId), (long) priceWithVAT)
            .orElse(0L);

        //FIXME we must support multiple transactions for a reservation, otherwise we can't handle properly the case of ON_SITE payments

        var transactionOptional = transactionRepository.loadOptionalByReservationId(reservationId);
        String transactionId = paymentProxy.getKey() + "-" + System.currentTimeMillis();
        if(transactionOptional.isEmpty()) {
            transactionRepository.insert(transactionId, null, reservationId, event.now(clockProvider),
                priceWithVAT, event.getCurrency(), "Offline payment confirmed for "+reservationId, paymentProxy.getKey(),
                platformFee, 0L, Transaction.Status.COMPLETE, Map.of());
        } else if(paymentProxy == PaymentProxy.OFFLINE) {
            var transaction = transactionOptional.get();
            transactionRepository.update(transaction.getId(), transactionId, null, event.now(clockProvider),
                platformFee, 0L, Transaction.Status.COMPLETE, Map.of());
        } else {
            log.warn("ON-Site check-in: ignoring transaction registration for reservationId {}", reservationId);
        }

    }


    public void sendConfirmationEmail(Event event, TicketReservation ticketReservation, Locale language, String username) {
        String reservationId = ticketReservation.getId();

        OrderSummary summary = orderSummaryForReservationId(reservationId, event);

        Map<String, Object> reservationEmailModel = prepareModelForReservationEmail(event, ticketReservation);
        List<Mailer.Attachment> attachments = Collections.emptyList();

        if (configurationManager.canGenerateReceiptOrInvoiceToCustomer(event)) { // https://github.com/alfio-event/alf.io/issues/573
            attachments = generateAttachmentForConfirmationEmail(event, ticketReservation, language, summary, username);
        }

        notificationManager.sendSimpleEmail(event, ticketReservation.getId(), ticketReservation.getEmail(), messageSourceManager.getMessageSourceForEvent(event).getMessage("reservation-email-subject",
                new Object[]{getShortReservationID(event, ticketReservation), event.getDisplayName()}, language),
           () -> templateManager.renderTemplate(event, TemplateResource.CONFIRMATION_EMAIL, reservationEmailModel, language),
            attachments);
    }

    private List<Mailer.Attachment> generateAttachmentForConfirmationEmail(Event event,
                                                                           TicketReservation ticketReservation,
                                                                           Locale language,
                                                                           OrderSummary summary,
                                                                           String username) {
        if(mustGenerateBillingDocument(summary, ticketReservation)) { //#459 - include PDF invoice in reservation email
            BillingDocument.Type type = ticketReservation.getHasInvoiceNumber() ? INVOICE : RECEIPT;
            return billingDocumentManager.generateBillingDocumentAttachment(event, ticketReservation, language, type, username, summary);
        }
        return List.of();
    }

    public void sendReservationCompleteEmailToOrganizer(Event event, TicketReservation ticketReservation, Locale language, String username) {
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        List<String> cc = notificationManager.getCCForEventOrganizer(event);

        Map<String, Object> reservationEmailModel = prepareModelForReservationEmail(event, ticketReservation);

        String reservationId = ticketReservation.getId();
        OrderSummary summary = orderSummaryForReservationId(reservationId, event);

        List<Mailer.Attachment> attachments = Collections.emptyList();

        if (!configurationManager.canGenerateReceiptOrInvoiceToCustomer(event) || configurationManager.isInvoiceOnly(event)) { // https://github.com/alfio-event/alf.io/issues/573
            attachments = generateAttachmentForConfirmationEmail(event, ticketReservation, language, summary, username);
        }


        String shortReservationID = configurationManager.getShortReservationID(event, ticketReservation);
        notificationManager.sendSimpleEmail(event, null, organization.getEmail(), cc, "Reservation complete " + shortReservationID,
        	() -> templateManager.renderTemplate(event, TemplateResource.CONFIRMATION_EMAIL_FOR_ORGANIZER, reservationEmailModel, language),
            attachments);
    }

    private static boolean mustGenerateBillingDocument(OrderSummary summary, TicketReservation ticketReservation) {
        return !summary.getFree() && (!summary.getNotYetPaid() || (summary.getWaitingForPayment() && ticketReservation.isInvoiceRequested()));
    }

    private List<Mailer.Attachment> generateBillingDocumentAttachment(EventAndOrganizationId event,
                                                                             TicketReservation ticketReservation,
                                                                             Locale language,
                                                                             Map<String, Object> billingDocumentModel,
                                                                             BillingDocument.Type documentType) {
        Map<String, String> model = new HashMap<>();
        model.put("reservationId", ticketReservation.getId());
        model.put("eventId", Integer.toString(event.getId()));
        model.put("language", json.asJsonString(language));
        model.put("reservationEmailModel", json.asJsonString(billingDocumentModel));//ticketReservation.getHasInvoiceNumber()
        switch (documentType) {
            case INVOICE:
                return Collections.singletonList(new Mailer.Attachment("invoice.pdf", null, "application/pdf", model, Mailer.AttachmentIdentifier.INVOICE_PDF));
            case RECEIPT:
                return Collections.singletonList(new Mailer.Attachment("receipt.pdf", null, "application/pdf", model, Mailer.AttachmentIdentifier.RECEIPT_PDF));
            case CREDIT_NOTE:
                return Collections.singletonList(new Mailer.Attachment("credit-note.pdf", null, "application/pdf", model, Mailer.AttachmentIdentifier.CREDIT_NOTE_PDF));
            default:
                throw new IllegalStateException(documentType+" is not supported");
        }
    }

    private Locale findReservationLanguage(String reservationId) {
        return ticketReservationRepository.findOptionalReservationById(reservationId).map(TicketReservationManager::getReservationLocale).orElse(Locale.ENGLISH);
    }

    public void deleteOfflinePayment(Event event, String reservationId, boolean expired, boolean credit, String username) {
        TicketReservation reservation = findById(reservationId).orElseThrow(IllegalArgumentException::new);
        Validate.isTrue(reservation.getStatus() == OFFLINE_PAYMENT || reservation.getStatus() == DEFERRED_OFFLINE_PAYMENT, "Invalid reservation status");
        Validate.isTrue(!(credit && reservation.getStatus() == DEFERRED_OFFLINE_PAYMENT), "Cannot credit deferred payment");
        if(credit) {
            creditReservation(reservation, username);
        } else {
            Map<String, Object> emailModel = prepareModelForReservationEmail(event, reservation);
            Locale reservationLanguage = findReservationLanguage(reservationId);
            String subject = getReservationEmailSubject(event, reservationLanguage, "reservation-email-expired-subject", reservation.getId());
            notificationManager.sendSimpleEmail(event, reservationId, reservation.getEmail(), subject,
            	() ->  templateManager.renderTemplate(event, TemplateResource.OFFLINE_RESERVATION_EXPIRED_EMAIL, emailModel, reservationLanguage)
            );
            cancelReservation(reservation, expired, username);
        }
    }

    private String getReservationEmailSubject(Event event, Locale reservationLanguage, String key, String id) {
        return messageSourceManager.getMessageSourceForEvent(event).getMessage(key, new Object[]{id, event.getDisplayName()}, reservationLanguage);
    }

    @Transactional
    public void issueCreditNoteForReservation(Event event, String reservationId, String username) {
        TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
        ticketReservationRepository.updateReservationStatus(reservationId, TicketReservationStatus.CREDIT_NOTE_ISSUED.toString());
        auditingRepository.insert(reservationId, userRepository.nullSafeFindIdByUserName(username).orElse(null), event.getId(), Audit.EventType.CREDIT_NOTE_ISSUED, new Date(), RESERVATION, reservationId);
        Map<String, Object> model = prepareModelForReservationEmail(event, reservation);
        BillingDocument billingDocument = billingDocumentManager.createBillingDocument(event, reservation, username, BillingDocument.Type.CREDIT_NOTE, orderSummaryForReservation(reservation, event));
        notificationManager.sendSimpleEmail(event,
            reservationId,
            reservation.getEmail(),
            getReservationEmailSubject(event, getReservationLocale(reservation), "credit-note-issued-email-subject", reservation.getId()),
            () -> templateManager.renderTemplate(event, TemplateResource.CREDIT_NOTE_ISSUED_EMAIL, model, getReservationLocale(reservation)),
            generateBillingDocumentAttachment(event, reservation, getReservationLocale(reservation), billingDocument.getModel(), CREDIT_NOTE)
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> prepareModelForReservationEmail(Event event, TicketReservation reservation, Optional<String> vat, OrderSummary summary) {
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        String baseUrl = baseUrl(event);
        String reservationUrl = reservationUrl(reservation.getId());
        String reservationShortID = getShortReservationID(event, reservation);

        var bankingInfo = configurationManager.getFor(Set.of(INVOICE_ADDRESS, BANK_ACCOUNT_NR, BANK_ACCOUNT_OWNER), ConfigurationLevel.event(event));
        Optional<String> invoiceAddress = bankingInfo.get(INVOICE_ADDRESS).getValue();
        Optional<String> bankAccountNr = bankingInfo.get(BANK_ACCOUNT_NR).getValue();
        Optional<String> bankAccountOwner = bankingInfo.get(BANK_ACCOUNT_OWNER).getValue();

        Map<Integer, List<Ticket>> ticketsByCategory = ticketRepository.findTicketsInReservation(reservation.getId())
            .stream()
            .collect(groupingBy(Ticket::getCategoryId));
        final List<TicketWithCategory> ticketsWithCategory;
        if(!ticketsByCategory.isEmpty()) {
            ticketsWithCategory = ticketCategoryRepository.findByIds(ticketsByCategory.keySet())
                .stream()
                .flatMap(tc -> ticketsByCategory.get(tc.getId()).stream().map(t -> new TicketWithCategory(t, tc)))
                .collect(toList());
        } else {
            ticketsWithCategory = Collections.emptyList();
        }
        var initialOptions = extensionManager.handleReservationEmailCustomText(event, reservation, ticketReservationRepository.getAdditionalInfo(reservation.getId()))
            .map(CustomEmailText::toMap)
            .orElse(Map.of());
        Map<String, Object> model = TemplateResource.prepareModelForConfirmationEmail(organization, event, reservation, vat, ticketsWithCategory, summary, baseUrl, reservationUrl, reservationShortID, invoiceAddress, bankAccountNr, bankAccountOwner, initialOptions);
        boolean euBusiness = StringUtils.isNotBlank(reservation.getVatCountryCode()) && StringUtils.isNotBlank(reservation.getVatNr())
            && configurationManager.getForSystem(ConfigurationKeys.EU_COUNTRIES_LIST).getRequiredValue().contains(reservation.getVatCountryCode())
            && PriceContainer.VatStatus.isVatExempt(reservation.getVatStatus());
        model.put("euBusiness", euBusiness);
        model.put("publicId", configurationManager.getPublicReservationID(event, reservation));
        model.put("invoicingAdditionalInfo", loadAdditionalInfo(reservation.getId()).getInvoicingAdditionalInfo());
        return model;
    }

    public TicketReservationAdditionalInfo loadAdditionalInfo(String reservationId) {
        return ticketReservationRepository.getAdditionalInfo(reservationId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> prepareModelForReservationEmail(Event event, TicketReservation reservation) {
        Optional<String> vat = getVAT(event);
        OrderSummary summary = orderSummaryForReservationId(reservation.getId(), event);
        return prepareModelForReservationEmail(event, reservation, vat, summary);
    }

    private void transitionToInPayment(PaymentSpecification spec) {
        requiresNewTransactionTemplate.execute(status -> {
            var optionalStatusAndValidation = ticketReservationRepository.findOptionalStatusAndValidationById(spec.getReservationId());
            if(optionalStatusAndValidation.isPresent() && optionalStatusAndValidation.get().getStatus() == COMPLETE) {
                // reservation has been already completed. Let's check if there is a corresponding audit event
                Validate.isTrue(auditingRepository.countAuditsOfTypeForReservation(spec.getReservationId(), PAYMENT_CONFIRMED) == 1, "Trying to confirm an already paid reservation, but can't find autiting event");
            } else {
                int updatedReservation = ticketReservationRepository.updateTicketReservation(spec.getReservationId(),
                    IN_PAYMENT.toString(), spec.getEmail(), spec.getCustomerName().getFullName(),
                    spec.getCustomerName().getFirstName(), spec.getCustomerName().getLastName(),
                    spec.getLocale().getLanguage(), spec.getBillingAddress(),null, PaymentProxy.STRIPE.toString(), spec.getCustomerReference());
                Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got " + updatedReservation);
            }
            return null;
        });
    }

    public static boolean hasValidOfflinePaymentWaitingPeriod(PaymentContext context, ConfigurationManager configurationManager) {
        OptionalInt result = BankTransferManager.getOfflinePaymentWaitingPeriod(context, configurationManager);
        return result.isPresent() && result.getAsInt() >= 0;
    }

    /**
     * ValidPaymentMethod should be configured in organisation and event. And if even already started then event should not have PaymentProxy.OFFLINE as only payment method
     *
     * @param paymentMethodDTO
     * @param event
     * @param configurationManager
     * @return
     */
    public static boolean isValidPaymentMethod(PaymentManager.PaymentMethodDTO paymentMethodDTO, Event event, ConfigurationManager configurationManager) {
        return paymentMethodDTO.isActive()
            && event.getAllowedPaymentProxies().contains(paymentMethodDTO.getPaymentProxy())
            && (!paymentMethodDTO.getPaymentProxy().equals(PaymentProxy.OFFLINE) || hasValidOfflinePaymentWaitingPeriod(new PaymentContext(event), configurationManager));
    }

    private void reTransitionToPending(String reservationId, boolean deleteTransactions) {
        int updatedReservation = ticketReservationRepository.updateReservationStatus(reservationId, TicketReservationStatus.PENDING.toString());
        Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got "+updatedReservation);
        if(deleteTransactions) {
            // delete all pending transactions, if any
            transactionRepository.deleteForReservationsWithStatus(List.of(reservationId), Transaction.Status.PENDING);
        }
    }
    private void reTransitionToPending(String reservationId) {
        reTransitionToPending(reservationId, true);
    }

    //check internal consistency between the 3 values
    public Optional<Triple<Event, TicketReservation, Ticket>> from(String eventName, String reservationId, String ticketIdentifier) {

        return eventRepository.findOptionalByShortName(eventName).flatMap(event ->
            ticketReservationRepository.findOptionalReservationById(reservationId).flatMap(reservation ->
                ticketRepository.findOptionalByUUID(ticketIdentifier).flatMap(ticket -> Optional.of(Triple.of(event, reservation, ticket)))))
        .filter(x -> {
            Ticket t = x.getRight();
            Event e = x.getLeft();
            TicketReservation tr = x.getMiddle();
            return tr.getId().equals(t.getTicketsReservationId()) && e.getId() == t.getEventId();
        });
    }

    /**
     * Set the tickets attached to the reservation to the ACQUIRED state and the ticket reservation to the COMPLETE state. Additionally it will save email/fullName/billingaddress/userLanguage.
     */
    void completeReservation(PaymentSpecification spec, PaymentProxy paymentProxy, boolean sendReservationConfirmationEmail, boolean sendTickets, String username) {
        String reservationId = spec.getReservationId();
        int eventId = spec.getEvent().getId();

        final TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
        Locale locale = LocaleUtil.forLanguageTag(reservation.getUserLanguage());
        List<Ticket> tickets = null;
        if(paymentProxy != PaymentProxy.OFFLINE) {
            tickets = acquireItems(paymentProxy, reservationId, spec.getEmail(), spec.getCustomerName(), spec.getLocale().getLanguage(), spec.getBillingAddress(), spec.getCustomerReference(), spec.getEvent(), sendTickets);
            extensionManager.handleReservationConfirmation(reservation, ticketReservationRepository.getBillingDetailsForReservation(reservationId), eventId);
        }

        Date eventTime = new Date();
        auditingRepository.insert(reservationId, null, eventId, Audit.EventType.RESERVATION_COMPLETE, eventTime, Audit.EntityType.RESERVATION, reservationId);
        ticketReservationRepository.updateRegistrationTimestamp(reservationId, ZonedDateTime.now(clockProvider.withZone(spec.getEvent().getZoneId())));
        if(spec.isTcAccepted()) {
            auditingRepository.insert(reservationId, null, eventId, Audit.EventType.TERMS_CONDITION_ACCEPTED, eventTime, Audit.EntityType.RESERVATION, reservationId, singletonList(singletonMap("termsAndConditionsUrl", spec.getEvent().getTermsAndConditionsUrl())));
        }

        if(eventHasPrivacyPolicy(spec.getEvent()) && spec.isPrivacyAccepted()) {
            auditingRepository.insert(reservationId, null, eventId, Audit.EventType.PRIVACY_POLICY_ACCEPTED, eventTime, Audit.EntityType.RESERVATION, reservationId, singletonList(singletonMap("privacyPolicyUrl", spec.getEvent().getPrivacyPolicyUrl())));
        }

        if(sendReservationConfirmationEmail) {
            TicketReservation updatedReservation = ticketReservationRepository.findReservationById(reservationId);
            sendConfirmationEmailIfNecessary(updatedReservation, tickets, spec.getEvent(), locale, username);
            sendReservationCompleteEmailToOrganizer(spec.getEvent(), updatedReservation, locale, username);
        }
    }

    void sendConfirmationEmailIfNecessary(TicketReservation ticketReservation,
                                          List<Ticket> tickets,
                                          Event event,
                                          Locale locale,
                                          String username) {
        var config = configurationManager.getFor(List.of(SEND_RESERVATION_EMAIL_IF_NECESSARY, SEND_TICKETS_AUTOMATICALLY), ConfigurationLevel.event(event));
        if(ticketReservation.getSrcPriceCts() > 0
            || CollectionUtils.isEmpty(tickets) || tickets.size() > 1
            || !tickets.get(0).getEmail().equals(ticketReservation.getEmail())
            || !config.get(SEND_RESERVATION_EMAIL_IF_NECESSARY).getValueAsBooleanOrDefault()
            || !config.get(SEND_TICKETS_AUTOMATICALLY).getValueAsBooleanOrDefault()
            ) {
            sendConfirmationEmail(event, ticketReservation, locale, username);
        }
    }

    private boolean eventHasPrivacyPolicy(Event event) {
        return StringUtils.isNotBlank(event.getPrivacyPolicyLinkOrNull());
    }

    private List<Ticket> acquireItems(PaymentProxy paymentProxy, String reservationId, String email, CustomerName customerName,
                              String userLanguage, String billingAddress, String customerReference, Event event, boolean sendTickets) {

        TicketStatus ticketStatus = paymentProxy.isDeskPaymentRequired() ? TicketStatus.TO_BE_PAID : TicketStatus.ACQUIRED;


        AdditionalServiceItemStatus asStatus = paymentProxy.isDeskPaymentRequired() ? AdditionalServiceItemStatus.TO_BE_PAID : AdditionalServiceItemStatus.ACQUIRED;

        Map<Integer, Ticket> preUpdateTicket = ticketRepository.findTicketsInReservation(reservationId).stream().collect(toMap(Ticket::getId, Function.identity()));
        int updatedTickets = ticketRepository.updateTicketsStatusWithReservationId(reservationId, ticketStatus.toString());

        if(!configurationManager.getFor(ENABLE_TICKET_TRANSFER, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault()) {
            //automatically lock assignment
            int locked = ticketRepository.forbidReassignment(preUpdateTicket.keySet());
            Validate.isTrue(updatedTickets == locked, "Expected to lock "+updatedTickets+" tickets, locked "+ locked);
            Map<Integer, Ticket> postUpdateTicket = ticketRepository.findTicketsInReservation(reservationId).stream().collect(toMap(Ticket::getId, Function.identity()));

            postUpdateTicket.forEach((id, ticket) -> {
                auditUpdateTicket(preUpdateTicket.get(id), Collections.emptyMap(), ticket, Collections.emptyMap(), event.getId());
            });
        }

        List<Ticket> ticketsInReservation = ticketRepository.findTicketsInReservation(reservationId);
        Map<Integer, Ticket> postUpdateTicket = ticketsInReservation.stream().collect(toMap(Ticket::getId, Function.identity()));
        postUpdateTicket.forEach((id, ticket) -> auditUpdateTicket(preUpdateTicket.get(id), Collections.emptyMap(), ticket, Collections.emptyMap(), event.getId()));

        int updatedAS = additionalServiceItemRepository.updateItemsStatusWithReservationUUID(reservationId, asStatus);
        Validate.isTrue(updatedTickets + updatedAS > 0, "no items have been updated");
        specialPriceRepository.updateStatusForReservation(singletonList(reservationId), Status.TAKEN.toString());
        ZonedDateTime timestamp = ZonedDateTime.now(clockProvider.getClock());
        int updatedReservation = ticketReservationRepository.updateTicketReservation(reservationId, TicketReservationStatus.COMPLETE.toString(), email,
            customerName.getFullName(), customerName.getFirstName(), customerName.getLastName(), userLanguage, billingAddress, timestamp, paymentProxy.toString(), customerReference);
        Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got " + updatedReservation);
        waitingQueueManager.fireReservationConfirmed(reservationId);
        //we must notify the plugins about ticket assignment and send them by email
        TicketReservation reservation = findById(reservationId).orElseThrow(IllegalStateException::new);
        // check for carnet
        if (event.isOnline()) {
            log.info("StripeWebhook managePromoCodeForCarnetEvent");
            managePromoCodeForCarnetEvent(event, reservation);
        }
        List<Ticket> assignedTickets = findTicketsInReservation(reservationId);
        assignedTickets.stream()
            .filter(ticket -> StringUtils.isNotBlank(ticket.getFullName()) || StringUtils.isNotBlank(ticket.getFirstName()) || StringUtils.isNotBlank(ticket.getEmail()))
            .forEach(ticket -> {
                Locale locale = LocaleUtil.forLanguageTag(ticket.getUserLanguage());
                if((paymentProxy != PaymentProxy.ADMIN || sendTickets) && configurationManager.getFor(SEND_TICKETS_AUTOMATICALLY, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault()) {
                    sendTicketByEmail(ticket, locale, event, getTicketEmailGenerator(event, reservation, locale));
                }
                Map<String, List<String>> additionalInfo = ticketFieldRepository.findNameAndValue(ticket.getId())
                    .stream()
                    .collect(groupingBy(FieldNameAndValue::getName, mapping(FieldNameAndValue::getValue, toList())));
                extensionManager.handleTicketAssignment(ticket, additionalInfo);
            });
        return assignedTickets;
    }

    public PartialTicketTextGenerator getTicketEmailGenerator(Event event, TicketReservation ticketReservation, Locale ticketLanguage) {
        return ticket -> {
            Organization organization = organizationRepository.getById(event.getOrganizationId());
            String ticketUrl = ticketUpdateUrl(event, ticket.getUuid());
            var ticketCategory = ticketCategoryRepository.getById(ticket.getCategoryId());

            var initialOptions = extensionManager.handleTicketEmailCustomText(event, ticketReservation, ticketReservationRepository.getAdditionalInfo(ticketReservation.getId()), ticketFieldRepository.findAllByTicketId(ticket.getId()))
                .map(CustomEmailText::toMap)
                .orElse(Map.of());
            if(event.getFormat() == Event.EventFormat.ONLINE) {
                initialOptions = new HashMap<>(initialOptions);
                var allMetadata = eventRepository.getMetadataForEvent(event.getId());
                var eventMetadata = Optional.ofNullable(allMetadata.getRequirementsDescriptions()).flatMap(m -> Optional.ofNullable(m.get(ticketLanguage.getLanguage())));
                var categoryMetadata = Optional.ofNullable(ticketCategoryRepository.getMetadata(event.getId(), ticketCategory.getId()).getRequirementsDescriptions()).flatMap(m -> Optional.ofNullable(m.get(ticketLanguage.getLanguage())));
                //check carnet event
                if (allMetadata.getAttributes() != null
                    && allMetadata.getAttributes().size() > 0
                    && allMetadata.getAttributes().containsKey(Event.EventOccurrence.CARNET.toString())
                    ){
                    // multiple event
                    var promoCode = promoCodeDiscountRepository.getPromoCodeByIdTicket(((Integer)ticket.getId()).toString());
                    initialOptions.put("promoCode", promoCode);
                    initialOptions.put("promoCodeAmount", allMetadata.getAttributes().get(Event.EventOccurrence.CARNET.toString()));
                } else {
                    //single event
                    if (ticketReservation.getPromoCodeDiscountId()!=null) {
                        var pCode = promoCodeDiscountRepository.getPromoCode(ticketReservation.getPromoCodeDiscountId());
                        if (!pCode.isEmpty() && pCode.get().getEmailReference().equalsIgnoreCase(ticketReservation.getEmail())){
                            //we should notify remaining promocodes
                            initialOptions.put("promoCodeAmount", pCode.get().getMaxUsage() - promoCodeDiscountRepository.countConfirmedPromoCode(pCode.get().getId(), categoriesOrNull(pCode.get()), null, categoriesOrNull(pCode.get()) != null ? "X" : null));
                        }
                    }

                    initialOptions.put("onlineCheckInUrl", ticketOnlineCheckIn(event, ticket.getUuid()));
                    initialOptions.put("prerequisites", categoryMetadata.or(() -> eventMetadata).orElse(""));
                }
            }
            var baseUrl = StringUtils.removeEnd(configurationManager.getFor(BASE_URL, ConfigurationLevel.event(event)).getRequiredValue(), "/");
            var calendarUrl = UriComponentsBuilder.fromUriString(baseUrl + "/api/v2/public/event/{eventShortName}/calendar/{currentLang}")
                .queryParam("type", "google")
                .build(Map.of("eventShortName", event.getShortName(), "currentLang", ticketLanguage.getLanguage()))
                .toString();
            return TemplateProcessor.buildPartialEmail(event, organization, ticketReservation, ticketCategory, templateManager, baseUrl, ticketUrl, calendarUrl, ticketLanguage, initialOptions).generate(ticket);
        };
    }



    @Transactional
    public void cleanupExpiredReservations(Date expirationDate) {
        List<String> expiredReservationIds = ticketReservationRepository.findExpiredReservationForUpdate(expirationDate);
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
            List<String> reservationIds = reservations.stream().map(ReservationIdAndEventId::getId).collect(toList());
            extensionManager.handleReservationsExpiredForEvent(event, reservationIds);
            billingDocumentRepository.deleteForReservations(reservationIds, eventId);
            transactionRepository.deleteForReservations(reservationIds);
        });
        //
        ticketReservationRepository.remove(expiredReservationIds);
    }

    public void cleanupExpiredOfflineReservations(Date expirationDate) {
        ticketReservationRepository.findExpiredOfflineReservationsForUpdate(expirationDate)
            .forEach(this::cleanupOfflinePayment);
    }

    private void cleanupOfflinePayment(String reservationId) {
        try {
            nestedTransactionTemplate.execute(tc -> {
                Event event = eventRepository.findByReservationId(reservationId);
                boolean enabled = configurationManager.getFor(AUTOMATIC_REMOVAL_EXPIRED_OFFLINE_PAYMENT, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault();
                if (enabled) {
                    deleteOfflinePayment(event, reservationId, true, false, null);
                } else {
                    log.trace("Will not cleanup reservation with id {} because the automatic removal has been disabled", reservationId);
                }
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
        List<Pair<TicketReservation, Event>> stuckReservations = findStuckPaymentsToBeNotified(expirationDate);
        if(!stuckReservations.isEmpty()) {
            List<String> ids = stuckReservations.stream().map(p -> p.getLeft().getId()).collect(toList());
            ticketReservationRepository.updateReservationsStatus(ids, TicketReservationStatus.STUCK.name());


            Map<Event, List<Pair<TicketReservation, Event>>> reservationsGroupedByEvent = stuckReservations
                .stream()
                .collect(Collectors.groupingBy(Pair::getRight));

            reservationsGroupedByEvent.forEach((event, reservations) -> {
                Organization organization = organizationRepository.getById(event.getOrganizationId());
                notificationManager.sendSimpleEmail(event, null, organization.getEmail(),
                    STUCK_TICKETS_SUBJECT,  () -> RenderedTemplate.plaintext(String.format(STUCK_TICKETS_MSG, event.getDisplayName())));

                extensionManager.handleStuckReservations(event, reservations.stream().map(p -> p.getLeft().getId()).collect(toList()));
            });
        }
    }

    private List<Pair<TicketReservation, Event>> findStuckPaymentsToBeNotified(Date expirationDate) {
        List<ReservationIdAndEventId> stuckReservations = ticketReservationRepository.findStuckReservationsForUpdate(expirationDate);
        Map<Integer, Event> events;
        if(!stuckReservations.isEmpty()){
            events = eventRepository.findByIds(stuckReservations.stream().map(ReservationIdAndEventId::getEventId).collect(toSet()))
                .stream()
                .collect(toMap(Event::getId, Function.identity()));
        } else {
            events = Map.of();
        }

        return stuckReservations.stream()
            .map(id -> Pair.of(ticketReservationRepository.findReservationById(id.getId()), events.get(id.getEventId())))
            .filter(reservationAndEvent -> {
                var event = reservationAndEvent.getRight();
                var reservation = reservationAndEvent.getLeft();
                var optionalTransaction = transactionRepository.loadOptionalByReservationIdAndStatusForUpdate(reservation.getId(), Transaction.Status.PENDING);
                if(optionalTransaction.isEmpty()) {
                    return true;
                }
                var transaction = optionalTransaction.get();
                PaymentContext paymentContext = new PaymentContext(event, reservation.getId());
                var paymentResultOptional = checkTransactionStatus(event, reservation);
                if(paymentResultOptional.isEmpty()) {
                    return true;
                }
                var providerAndWebhookResult = paymentResultOptional.get();
                var paymentWebhookResult = providerAndWebhookResult.getRight();
                handlePaymentWebhookResult(event, providerAndWebhookResult.getLeft(), paymentWebhookResult, reservation, transaction, paymentContext, "stuck-check", false);
                return paymentWebhookResult.getType() == PaymentWebhookResult.Type.NOT_RELEVANT;
            })
            .collect(toList());
    }

    private static Pair<TotalPrice, Optional<PromoCodeDiscount>> totalReservationCostWithVAT(PromoCodeDiscount promoCodeDiscount,
                                                          Event event,
                                                          TicketReservation reservation,
                                                          List<Ticket> tickets,
                                                          List<Pair<AdditionalService, List<AdditionalServiceItem>>> additionalServiceItems) {

        String currencyCode = event.getCurrency();
        List<TicketPriceContainer> ticketPrices = tickets.stream().map(t -> TicketPriceContainer.from(t, reservation.getVatStatus(), event.getVat(), event.getVatStatus(), promoCodeDiscount)).collect(toList());
        int discountedTickets = (int) ticketPrices.stream().filter(t -> t.getAppliedDiscount().compareTo(BigDecimal.ZERO) > 0).count();
        int discountAppliedCount = discountedTickets <= 1 || promoCodeDiscount.getDiscountType() == DiscountType.FIXED_AMOUNT ? discountedTickets : 1;
        if(discountAppliedCount == 0 && promoCodeDiscount != null && promoCodeDiscount.getDiscountType() == DiscountType.FIXED_AMOUNT_RESERVATION) {
            discountAppliedCount = 1;
        }
        var reservationPriceCalculator = ReservationPriceCalculator.from(reservation, promoCodeDiscount, tickets, event, additionalServiceItems);
        var price = new TotalPrice(unitToCents(reservationPriceCalculator.getFinalPrice(), currencyCode),
            unitToCents(reservationPriceCalculator.getVAT(), currencyCode),
            -MonetaryUtil.unitToCents(reservationPriceCalculator.getAppliedDiscount(), currencyCode),
            discountAppliedCount,
            currencyCode);
        return Pair.of(price, Optional.ofNullable(promoCodeDiscount));
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
    public Pair<TotalPrice, Optional<PromoCodeDiscount>> totalReservationCostWithVAT(String reservationId) {
        return totalReservationCostWithVAT(ticketReservationRepository.findReservationById(reservationId));
    }

    public Pair<TotalPrice, Optional<PromoCodeDiscount>> totalReservationCostWithVAT(TicketReservation reservation) {
        return totalReservationCostWithVAT(eventRepository.findByReservationId(reservation.getId()), reservation, ticketRepository.findTicketsInReservation(reservation.getId()));
    }

    public Pair<TotalPrice, Optional<PromoCodeDiscount>> totalReservationCostWithVAT(Event event, TicketReservation reservation, List<Ticket> tickets) {
        Optional<PromoCodeDiscount> promoCodeDiscount = Optional.ofNullable(reservation.getPromoCodeDiscountId())
            .map(promoCodeDiscountRepository::findById);
        return totalReservationCostWithVAT(promoCodeDiscount.orElse(null), event, reservation, tickets, collectAdditionalServiceItems(reservation.getId(), event));
    }

    private String formatPromoCode(PromoCodeDiscount promoCodeDiscount, List<Ticket> tickets, Locale locale, Event event) {

        if(promoCodeDiscount.getCodeType() == CodeType.DYNAMIC) {
            return messageSourceManager.getMessageSourceForEvent(event).getMessage("reservation.dynamic.discount.description", null, locale); //we don't expose the internal promo code
        }

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

    public OrderSummary orderSummaryForReservationId(String reservationId, Event event) {
        TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
        return orderSummaryForReservation(reservation, event);
    }

    public OrderSummary orderSummaryForReservation(TicketReservation reservation, Event event) {
        var totalPriceAndDiscount = totalReservationCostWithVAT(reservation);
        TotalPrice reservationCost = totalPriceAndDiscount.getLeft();
        PromoCodeDiscount discount = totalPriceAndDiscount.getRight().orElse(null);
        //
        boolean free = reservationCost.getPriceWithVAT() == 0;
        String refundedAmount = null;

        boolean hasRefund = auditingRepository.countAuditsOfTypeForReservation(reservation.getId(), Audit.EventType.REFUND) > 0;

        if(hasRefund) {
            refundedAmount = paymentManager.getInfo(reservation, event).getPaymentInformation().getRefundedAmount();
        }

        var currencyCode = reservation.getCurrencyCode();
        return new OrderSummary(reservationCost,
            extractSummary(reservation.getId(), reservation.getVatStatus(), event, LocaleUtil.forLanguageTag(reservation.getUserLanguage()), discount, reservationCost),
            free,
            formatCents(reservationCost.getPriceWithVAT(), currencyCode),
            formatCents(reservationCost.getVAT(), currencyCode),
            reservation.getStatus() == TicketReservationStatus.OFFLINE_PAYMENT,
            reservation.getStatus() == DEFERRED_OFFLINE_PAYMENT,
            reservation.getPaymentMethod() == PaymentProxy.ON_SITE,
            Optional.ofNullable(event.getVat()).map(p -> MonetaryUtil.formatCents(MonetaryUtil.unitToCents(p, currencyCode), currencyCode)).orElse(null),
            reservation.getVatStatus(),
            refundedAmount);
    }
    
    List<SummaryRow> extractSummary(String reservationId, PriceContainer.VatStatus reservationVatStatus,
                                    Event event, Locale locale, PromoCodeDiscount promoCodeDiscount, TotalPrice reservationCost) {
        List<SummaryRow> summary = new ArrayList<>();
        var currencyCode = reservationCost.getCurrencyCode();
        List<TicketPriceContainer> tickets = ticketRepository.findTicketsInReservation(reservationId).stream()
            .map(t -> TicketPriceContainer.from(t, reservationVatStatus, event.getVat(), event.getVatStatus(), promoCodeDiscount)).collect(toList());
        tickets.stream()
            .collect(Collectors.groupingBy(TicketPriceContainer::getCategoryId))
            .forEach((categoryId, ticketsByCategory) -> {
                final int subTotal = ticketsByCategory.stream().mapToInt(TicketPriceContainer::getSummarySrcPriceCts).sum();
                final int subTotalBeforeVat = SummaryPriceContainer.getSummaryPriceBeforeVatCts(ticketsByCategory);
                TicketPriceContainer firstTicket = ticketsByCategory.get(0);
                final int ticketPriceCts = firstTicket.getSummarySrcPriceCts();
                final int priceBeforeVat = SummaryPriceContainer.getSummaryPriceBeforeVatCts(singletonList(firstTicket));
                String categoryName = ticketCategoryRepository.getByIdAndActive(categoryId, event.getId()).getName();
                summary.add(new SummaryRow(categoryName, formatCents(ticketPriceCts, currencyCode), formatCents(priceBeforeVat, currencyCode), ticketsByCategory.size(), formatCents(subTotal, currencyCode), formatCents(subTotalBeforeVat, currencyCode), subTotal, SummaryType.TICKET));
            });

        summary.addAll(streamAdditionalServiceItems(reservationId, event)
            .map(entry -> {
                String language = locale.getLanguage();
                AdditionalServiceText title = additionalServiceTextRepository.findBestMatchByLocaleAndType(entry.getKey().getId(), language, AdditionalServiceText.TextType.TITLE);
                if(!title.getLocale().equals(language) || title.getId() == -1) {
                    log.debug("additional service {}: title not found for locale {}", title.getAdditionalServiceId(), language);
                }
                List<AdditionalServiceItemPriceContainer> prices = generateASIPriceContainers(event, null).apply(entry).collect(toList());
                AdditionalServiceItemPriceContainer first = prices.get(0);
                final int subtotal = prices.stream().mapToInt(AdditionalServiceItemPriceContainer::getSrcPriceCts).sum();
                final int subtotalBeforeVat = SummaryPriceContainer.getSummaryPriceBeforeVatCts(prices);
                return new SummaryRow(title.getValue(), formatCents(first.getSrcPriceCts(), currencyCode), formatCents(SummaryPriceContainer.getSummaryPriceBeforeVatCts(singletonList(first)), currencyCode), prices.size(), formatCents(subtotal, currencyCode), formatCents(subtotalBeforeVat, currencyCode), subtotal, SummaryType.ADDITIONAL_SERVICE);
            }).collect(toList()));

        Optional.ofNullable(promoCodeDiscount).ifPresent(promo -> {
            String formattedSingleAmount = "-" + (DiscountType.isFixedAmount(promo.getDiscountType())  ? formatCents(promo.getDiscountAmount(), currencyCode) : (promo.getDiscountAmount()+"%"));
            summary.add(new SummaryRow(formatPromoCode(promo, ticketRepository.findTicketsInReservation(reservationId), locale, event),
                formattedSingleAmount,
                formattedSingleAmount,
                reservationCost.getDiscountAppliedCount(),
                formatCents(reservationCost.getDiscount(), currencyCode), formatCents(reservationCost.getDiscount(), currencyCode), reservationCost.getDiscount(),
                promo.isDynamic() ? SummaryType.DYNAMIC_DISCOUNT : SummaryType.PROMOTION_CODE));
        });
        return summary;
    }

    private Stream<Pair<AdditionalService, List<AdditionalServiceItem>>> streamAdditionalServiceItems(String reservationId, Event event) {
        return additionalServiceItemRepository.findByReservationUuid(reservationId)
            .stream()
            .collect(Collectors.groupingBy(AdditionalServiceItem::getAdditionalServiceId))
            .entrySet()
            .stream()
            .map(entry -> Pair.of(additionalServiceRepository.getById(entry.getKey(), event.getId()), entry.getValue()));
    }
    private List<Pair<AdditionalService, List<AdditionalServiceItem>>> collectAdditionalServiceItems(String reservationId, Event event) {
        return streamAdditionalServiceItems(reservationId, event).collect(Collectors.toList());
    }

    String reservationUrl(String reservationId) {
        return reservationUrl(reservationId, eventRepository.findByReservationId(reservationId));
    }

    public String reservationUrl(String reservationId, Event event) {
        return reservationUrl(ticketReservationRepository.findReservationById(reservationId), event);
    }
    
    String baseUrl(Event event) {
    	return StringUtils.removeEnd(configurationManager.getFor(BASE_URL, ConfigurationLevel.event(event)).getRequiredValue(), "/");
    }

    String reservationUrl(TicketReservation reservation, Event event) {
        return baseUrl(event) + "/event/" + event.getShortName() + "/reservation/" + reservation.getId() + "?lang="+reservation.getUserLanguage();
    }

    String ticketUrl(Event event, String ticketId) {
        Ticket ticket = ticketRepository.findByUUID(ticketId);
    
        return baseUrl(event) + "/event/" + event.getShortName() + "/ticket/" + ticketId + "?lang=" + ticket.getUserLanguage();
    }

    public String ticketUpdateUrl(Event event, String ticketId) {
        Ticket ticket = ticketRepository.findByUUID(ticketId);
        
        return baseUrl(event) + "/event/" + event.getShortName() + "/ticket/" + ticketId + "/update?lang=" + ticket.getUserLanguage();
    }

    public String ticketOnlineCheckIn(Event event, String ticketId) {
        Ticket ticket = ticketRepository.findByUUID(ticketId);
        
        return ticketOnlineCheckInUrl(event, ticket, baseUrl(event));
    }

    public static String ticketOnlineCheckInUrl(Event event, Ticket ticket, String baseUrl) {
        var ticketCode = DigestUtils.sha256Hex(ticket.ticketCode(event.getPrivateKey()));
        return StringUtils.removeEnd(baseUrl, "/")
            + "/event/" + event.getShortName() + "/ticket/" + ticket.getUuid() + "/check-in/"+ticketCode;
    }

    public int maxAmountOfTicketsForCategory(EventAndOrganizationId eventAndOrganizationId, int ticketCategoryId, String promoCode) {
        // verify if the promo code is present and if it's actually an access code
        if(StringUtils.isNotBlank(promoCode)) {
            Integer maxTicketsPerAccessCode = promoCodeDiscountRepository.findPromoCodeInEventOrOrganization(eventAndOrganizationId.getId(), promoCode)
                .filter(d -> d.getCodeType() == CodeType.ACCESS)
                .map(PromoCodeDiscount::getMaxUsage).orElse(null);
            if(maxTicketsPerAccessCode != null) {
                return maxTicketsPerAccessCode;
            }
        }
        return configurationManager.getFor(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, ConfigurationLevel.ticketCategory(eventAndOrganizationId, ticketCategoryId)).getValueAsIntOrDefault(5);
    }

    public Optional<TicketReservation> findByIdForEvent(String reservationId, int eventId) {
        return ticketReservationRepository.findOptionalReservationByIdAndEventId(reservationId, eventId);
    }
    
    public Optional<TicketReservation> findById(String reservationId) {
        return ticketReservationRepository.findOptionalReservationById(reservationId);
    }

    private Optional<TicketReservation> findByIdForNotification(String reservationId, Clock clock, int quietPeriod) {
        return findById(reservationId).filter(notificationNotSent(clock, quietPeriod));
    }

    private static Predicate<TicketReservation> notificationNotSent(Clock clock, int quietPeriod) {
        return r -> r.latestNotificationTimestamp(clock.getZone())
                .map(t -> t.truncatedTo(ChronoUnit.DAYS).plusDays(quietPeriod).isBefore(ZonedDateTime.now(clock).truncatedTo(ChronoUnit.DAYS)))
                .orElse(true);
    }

    public void cancelPendingReservation(String reservationId, boolean expired, String username) {
        cancelPendingReservation(ticketReservationRepository.findReservationById(reservationId), expired, username);
    }

    private void cancelPendingReservation(TicketReservation reservation, boolean expired, String username) {
        Validate.isTrue(reservation.getStatus() == TicketReservationStatus.PENDING, "status is not PENDING");
        cancelReservation(reservation, expired, username);
    }

    private void cancelReservation(TicketReservation reservation, boolean expired, String username) {
        String reservationId = reservation.getId();
        Event event = eventRepository.findByReservationId(reservationId);
        cleanupReferencesToReservation(expired, username, reservationId, event);
        removeReservation(event, reservation, expired, username);
    }

    private void creditReservation(TicketReservation reservation, String username) {
        String reservationId = reservation.getId();
        Event event = eventRepository.findByReservationId(reservationId);
        billingDocumentManager.ensureBillingDocumentIsPresent(event, reservation, username, () -> orderSummaryForReservationId(reservation.getId(), event));
        issueCreditNoteForReservation(event, reservationId, username);
        cleanupReferencesToReservation(false, username, reservationId, event);
        extensionManager.handleReservationsCreditNoteIssuedForEvent(event, Collections.singletonList(reservationId));
    }

    private void cleanupReferencesToReservation(boolean expired, String username, String reservationId, EventAndOrganizationId event) {
        List<String> reservationIdsToRemove = singletonList(reservationId);
        specialPriceRepository.resetToFreeAndCleanupForReservation(reservationIdsToRemove);
        groupManager.deleteWhitelistedTicketsForReservation(reservationId);
        ticketRepository.resetCategoryIdForUnboundedCategories(reservationIdsToRemove);
        ticketFieldRepository.deleteAllValuesForReservations(reservationIdsToRemove);
        int updatedAS = additionalServiceItemRepository.updateItemsStatusWithReservationUUID(reservationId, expired ? AdditionalServiceItemStatus.EXPIRED : AdditionalServiceItemStatus.CANCELLED);
        int updatedTickets = ticketRepository.findTicketIdsInReservation(reservationId).stream().mapToInt(
            tickedId -> ticketRepository.releaseExpiredTicket(reservationId, event.getId(), tickedId, UUID.randomUUID().toString())
        ).sum();
        Validate.isTrue(updatedTickets  + updatedAS > 0, "no items have been updated");
        transactionRepository.deleteForReservations(List.of(reservationId));
        waitingQueueManager.fireReservationExpired(reservationId);
        auditingRepository.insert(reservationId, userRepository.nullSafeFindIdByUserName(username).orElse(null), event.getId(), expired ? Audit.EventType.CANCEL_RESERVATION_EXPIRED : Audit.EventType.CANCEL_RESERVATION, new Date(), Audit.EntityType.RESERVATION, reservationId);
    }

    private void removeReservation(Event event, TicketReservation reservation, boolean expired, String username) {
        //handle removal of ticket
        String reservationIdToRemove = reservation.getId();
        List<String> wrappedReservationIdToRemove = Collections.singletonList(reservationIdToRemove);
        waitingQueueManager.cleanExpiredReservations(wrappedReservationIdToRemove);
        int result = billingDocumentRepository.deleteForReservation(reservationIdToRemove, event.getId());
        if(result > 0) {
            log.warn("deleted {} documents for reservation id {}", result, reservationIdToRemove);
        }
        //
        if(expired) {
            extensionManager.handleReservationsExpiredForEvent(event, wrappedReservationIdToRemove);
        } else {
            extensionManager.handleReservationsCancelledForEvent(event, wrappedReservationIdToRemove);
        }
        int removedReservation = ticketReservationRepository.remove(wrappedReservationIdToRemove);
        Validate.isTrue(removedReservation == 1, "expected exactly one removed reservation, got " + removedReservation);
        auditingRepository.insert(reservationIdToRemove, userRepository.nullSafeFindIdByUserName(username).orElse(null), event.getId(), expired ? Audit.EventType.CANCEL_RESERVATION_EXPIRED : Audit.EventType.CANCEL_RESERVATION, new Date(), Audit.EntityType.RESERVATION, reservationIdToRemove);
    }

    public Optional<SpecialPrice> getSpecialPriceByCode(String code) {
        return specialPriceRepository.getByCode(code);
    }

    public List<Ticket> findTicketsInReservation(String reservationId) {
        return ticketRepository.findTicketsInReservation(reservationId);
    }

    public Optional<Ticket> findFirstInReservation(String reservationId) {
        return ticketRepository.findFirstTicketInReservation(reservationId);
    }

    public Optional<String> getVAT(EventAndOrganizationId event) {
        return configurationManager.getFor(VAT_NR, ConfigurationLevel.event(event)).getValue();
    }

    public void updateTicketOwner(Ticket ticket,
                                  Locale locale,
                                  Event event,
                                  UpdateTicketOwnerForm updateTicketOwner,
                                  PartialTicketTextGenerator confirmationTextBuilder,
                                  PartialTicketTextGenerator ownerChangeTextBuilder,
                                  Optional<UserDetails> userDetails) {

        Ticket preUpdateTicket = ticketRepository.findByUUID(ticket.getUuid());
        if(preUpdateTicket.getLockedAssignment() && isTicketBeingReassigned(ticket, updateTicketOwner, event)) {
            log.warn("trying to update assignee for a locked ticket ({})", preUpdateTicket.getId());
            return;
        }

        Map<String, String> preUpdateTicketFields = ticketFieldRepository.findAllByTicketId(ticket.getId()).stream().collect(Collectors.toMap(TicketFieldValue::getName, TicketFieldValue::getValue));

        String newEmail = StringUtils.trim(updateTicketOwner.getEmail());
        CustomerName customerName = new CustomerName(updateTicketOwner.getFullName(), updateTicketOwner.getFirstName(), updateTicketOwner.getLastName(), event.mustUseFirstAndLastName(), false);
        ticketRepository.updateTicketOwner(ticket.getUuid(), newEmail, customerName.getFullName(), customerName.getFirstName(), customerName.getLastName());

        //
        Locale userLocale = Optional.ofNullable(StringUtils.trimToNull(updateTicketOwner.getUserLanguage())).map(LocaleUtil::forLanguageTag).orElse(locale);

        ticketRepository.updateOptionalTicketInfo(ticket.getUuid(), userLocale.getLanguage());
        ticketFieldRepository.updateOrInsert(updateTicketOwner.getAdditional(), ticket.getId(), event.getId());

        Ticket newTicket = ticketRepository.findByUUID(ticket.getUuid());
        boolean sendTicketAllowed = configurationManager.getFor(SEND_TICKETS_AUTOMATICALLY, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault();
        if (sendTicketAllowed && (newTicket.getStatus() == TicketStatus.ACQUIRED || newTicket.getStatus() == TicketStatus.TO_BE_PAID)
            && (!equalsIgnoreCase(newEmail, ticket.getEmail()) || !equalsIgnoreCase(customerName.getFullName(), ticket.getFullName()))) {
            sendTicketByEmail(newTicket, userLocale, event, confirmationTextBuilder);
        }

        boolean admin = isAdmin(userDetails);

        if (!admin && StringUtils.isNotBlank(ticket.getEmail()) && !equalsIgnoreCase(newEmail, ticket.getEmail()) && ticket.getStatus() == TicketStatus.ACQUIRED) {
            Locale oldUserLocale = LocaleUtil.forLanguageTag(ticket.getUserLanguage());
            String subject = messageSourceManager.getMessageSourceForEvent(event).getMessage("ticket-has-changed-owner-subject", new Object[] {event.getDisplayName()}, oldUserLocale);
            notificationManager.sendSimpleEmail(event, ticket.getTicketsReservationId(), ticket.getEmail(), subject, () -> ownerChangeTextBuilder.generate(newTicket));
            if(event.getBegin().isBefore(event.now(clockProvider))) {
                Organization organization = organizationRepository.getById(event.getOrganizationId());
                notificationManager.sendSimpleEmail(event, null, organization.getEmail(), "WARNING: Ticket has been reassigned after event start", () -> ownerChangeTextBuilder.generate(newTicket));
            }
        }

        if(admin) {
            TicketReservation reservation = findById(ticket.getTicketsReservationId()).orElseThrow(IllegalStateException::new);
            //if the current user is admin, then it would be good to update also the name of the Reservation Owner
            String username = userDetails.orElseThrow().getUsername();
            log.warn("Reservation {}: forced assignee replacement old: {} new: {}", reservation.getId(), reservation.getFullName(), username);
            ticketReservationRepository.updateAssignee(reservation.getId(), username);
        }
        extensionManager.handleTicketAssignment(newTicket, updateTicketOwner.getAdditional());



        Ticket postUpdateTicket = ticketRepository.findByUUID(ticket.getUuid());
        Map<String, String> postUpdateTicketFields = ticketFieldRepository.findAllByTicketId(ticket.getId()).stream().collect(Collectors.toMap(TicketFieldValue::getName, TicketFieldValue::getValue));

        auditUpdateTicket(preUpdateTicket, preUpdateTicketFields, postUpdateTicket, postUpdateTicketFields, event.getId());
    }

    boolean isTicketBeingReassigned(Ticket original, UpdateTicketOwnerForm updated, Event event) {
        if(StringUtils.isBlank(original.getEmail()) || StringUtils.isBlank(original.getFullName())) {
            return false;
        }
        CustomerName customerName = new CustomerName(updated.getFullName(), updated.getFirstName(), updated.getLastName(), event.mustUseFirstAndLastName());
        return StringUtils.isNotBlank(original.getEmail()) && StringUtils.isNotBlank(original.getFullName())
            && (!equalsIgnoreCase(original.getEmail(), updated.getEmail()) || !equalsIgnoreCase(original.getFullName(), customerName.getFullName()));
    }

    private void auditUpdateTicket(Ticket preUpdateTicket, Map<String, String> preUpdateTicketFields, Ticket postUpdateTicket, Map<String, String> postUpdateTicketFields, int eventId) {
        List<ObjectDiffUtil.Change> diffTicket = ObjectDiffUtil.diff(preUpdateTicket, postUpdateTicket);
        List<ObjectDiffUtil.Change> diffTicketFields = ObjectDiffUtil.diff(preUpdateTicketFields, postUpdateTicketFields);

        List<Map<String, Object>> changes = Stream.concat(diffTicket.stream(), diffTicketFields.stream()).map(change -> {
                var v = new HashMap<String, Object>();
                v.put("propertyName", change.getPropertyName());
                v.put("state", change.getState());
                v.put("oldValue", change.getOldValue());
                v.put("newValue", change.getNewValue());
                return v;
            }).collect(Collectors.toList());

        auditingRepository.insert(preUpdateTicket.getTicketsReservationId(), null, eventId,
            Audit.EventType.UPDATE_TICKET, new Date(), Audit.EntityType.TICKET, Integer.toString(preUpdateTicket.getId()), changes);
    }

    private boolean isAdmin(Optional<UserDetails> userDetails) {
        return userDetails.flatMap(u -> u.getAuthorities().stream().map(a -> Role.fromRoleName(a.getAuthority())).filter(Role.ADMIN::equals).findFirst()).isPresent();
    }

    void sendTicketByEmail(Ticket ticket, Locale locale, Event event, PartialTicketTextGenerator confirmationTextBuilder) {
        TicketReservation reservation = ticketReservationRepository.findReservationById(ticket.getTicketsReservationId());
        TicketCategory ticketCategory = ticketCategoryRepository.getByIdAndActive(ticket.getCategoryId(), event.getId());
        notificationManager.sendTicketByEmail(ticket, event, locale, confirmationTextBuilder, reservation, ticketCategory);
    }

    public Optional<Triple<Event, TicketReservation, Ticket>> fetchComplete(String eventName, String ticketIdentifier) {
        return ticketRepository.findOptionalByUUID(ticketIdentifier)
            .flatMap(ticket -> from(eventName, ticket.getTicketsReservationId(), ticketIdentifier)
                .flatMap(triple -> {
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
        return fetchComplete(eventName, ticketIdentifier).flatMap(t -> {
            if (t.getRight().getAssigned()) {
                return Optional.of(t);
            } else {
                return Optional.empty();
            }
        });
    }

    public Optional<OnlineCheckInFullInfo> fetchCompleteAndAssignedForOnlineCheckIn(String eventName, String ticketIdentifier) {
        return ticketRepository.getFullInfoForOnlineCheckin(eventName, ticketIdentifier);
    }

    public void sendReminderForOfflinePayments() {
        Date expiration = truncate(addHours(new Date(), configurationManager.getForSystem(OFFLINE_REMINDER_HOURS).getValueAsIntOrDefault(24)), Calendar.DATE);
        ticketReservationRepository.findAllOfflinePaymentReservationForNotificationForUpdate(expiration).stream()
                .map(reservation -> {
                    Optional<Ticket> ticket = ticketRepository.findFirstTicketInReservation(reservation.getId());
                    Optional<Event> event = ticket.map(t -> eventRepository.findById(t.getEventId()));
                    Optional<Locale> locale = ticket.map(t -> LocaleUtil.forLanguageTag(t.getUserLanguage()));
                    return Triple.of(reservation, event, locale);
                })
                .filter(p -> p.getMiddle().isPresent())
                .filter(p -> {
                    Event event = p.getMiddle().get();
                    return truncate(addHours(new Date(), configurationManager.getFor(OFFLINE_REMINDER_HOURS, ConfigurationLevel.event(event)).getValueAsIntOrDefault(24)), Calendar.DATE).compareTo(p.getLeft().getValidity()) >= 0;
                })
                .map(p -> Triple.of(p.getLeft(), p.getMiddle().orElseThrow(), p.getRight().orElseThrow()))
                .forEach(p -> {
                    TicketReservation reservation = p.getLeft();
                    Event event = p.getMiddle();
                    Map<String, Object> model = prepareModelForReservationEmail(event, reservation);
                    Locale locale = p.getRight();
                    ticketReservationRepository.flagAsOfflinePaymentReminderSent(reservation.getId());
                    notificationManager.sendSimpleEmail(event, reservation.getId(), reservation.getEmail(), messageSourceManager.getMessageSourceForEvent(event).getMessage("reservation.reminder.mail.subject", 
                    		new Object[]{getShortReservationID(event, reservation)}, locale), () -> templateManager.renderTemplate(event, TemplateResource.REMINDER_EMAIL, model, locale));
                });
    }

    //called each hour
    public void sendReminderForOfflinePaymentsToEventManagers() {
        eventRepository.findAllActives(ZonedDateTime.now(clockProvider.getClock())).stream().filter(event -> {
            ZonedDateTime dateTimeForEvent = event.now(clockProvider);
            return dateTimeForEvent.truncatedTo(ChronoUnit.HOURS).getHour() == 5; //only for the events at 5:00 local time
        }).forEachOrdered(event -> {
            ZonedDateTime dateTimeForEvent = event.now(clockProvider).truncatedTo(ChronoUnit.DAYS).plusDays(1);
            List<TicketReservationInfo> reservations = ticketReservationRepository.findAllOfflinePaymentReservationWithExpirationBeforeForUpdate(dateTimeForEvent, event.getId());
            log.info("for event {} there are {} pending offline payments to handle", event.getId(), reservations.size());
            if(!reservations.isEmpty()) {
                Organization organization = organizationRepository.getById(event.getOrganizationId());
                List<String> cc = notificationManager.getCCForEventOrganizer(event);
                String subject = String.format("There are %d pending offline payments that will expire in event: %s", reservations.size(), event.getDisplayName());
                String baseUrl = configurationManager.getFor(BASE_URL, ConfigurationLevel.event(event)).getRequiredValue();
                Map<String, Object> model = TemplateResource.prepareModelForOfflineReservationExpiringEmailForOrganizer(event, reservations, baseUrl);
                notificationManager.sendSimpleEmail(event, null, organization.getEmail(), cc, subject, () ->
                    templateManager.renderTemplate(event, TemplateResource.OFFLINE_RESERVATION_EXPIRING_EMAIL_FOR_ORGANIZER, model, Locale.ENGLISH));
                extensionManager.handleOfflineReservationsWillExpire(event, reservations);
            }
        });
    }

    public void sendReminderForTicketAssignment() {
        getNotifiableEventsStream()
                .map(e -> Pair.of(e, ticketRepository.findAllReservationsConfirmedButNotAssignedForUpdate(e.getId())))
                .filter(p -> !p.getRight().isEmpty())
                .forEach(p -> Wrappers.voidTransactionWrapper(this::sendAssignmentReminder, p));
    }

    public void sendReminderForOptionalData() {
        getNotifiableEventsStream()
                .filter(e -> configurationManager.getFor(OPTIONAL_DATA_REMINDER_ENABLED, ConfigurationLevel.event(e)).getValueAsBooleanOrDefault())
                .filter(e -> ticketFieldRepository.countAdditionalFieldsForEvent(e.getId()) > 0)
                .map(e -> Pair.of(e, ticketRepository.findAllAssignedButNotYetNotifiedForUpdate(e.getId())))
                .filter(p -> !p.getRight().isEmpty())
                .forEach(p -> Wrappers.voidTransactionWrapper(this::sendOptionalDataReminder, p));
    }

    private void sendOptionalDataReminder(Pair<Event, List<Ticket>> eventAndTickets) {
        nestedTransactionTemplate.execute(ts -> {
            Event event = eventAndTickets.getLeft();
            var messageSource = messageSourceManager.getMessageSourceForEvent(event);
            int daysBeforeStart = configurationManager.getFor(ASSIGNMENT_REMINDER_START, ConfigurationLevel.event(event)).getValueAsIntOrDefault(10);
            List<Ticket> tickets = eventAndTickets.getRight().stream().filter(t -> !ticketFieldRepository.hasOptionalData(t.getId())).collect(toList());
            Set<String> notYetNotifiedReservations = tickets.stream().map(Ticket::getTicketsReservationId).distinct().filter(rid -> findByIdForNotification(rid, clockProvider.withZone(event.getZoneId()), daysBeforeStart).isPresent()).collect(toSet());
            tickets.stream()
                    .filter(t -> notYetNotifiedReservations.contains(t.getTicketsReservationId()))
                    .forEach(t -> {
                        int result = ticketRepository.flagTicketAsReminderSent(t.getId());
                        Validate.isTrue(result == 1);
                        Map<String, Object> model = TemplateResource.prepareModelForReminderTicketAdditionalInfo(organizationRepository.getById(event.getOrganizationId()), event, t, ticketUpdateUrl(event, t.getUuid()));
                        Locale locale = Optional.ofNullable(t.getUserLanguage()).map(LocaleUtil::forLanguageTag).orElseGet(() -> findReservationLanguage(t.getTicketsReservationId()));
                        notificationManager.sendSimpleEmail(event, t.getTicketsReservationId(), t.getEmail(), messageSource.getMessage("reminder.ticket-additional-info.subject", 
                        		new Object[]{event.getDisplayName()}, locale), () -> templateManager.renderTemplate(event, TemplateResource.REMINDER_TICKET_ADDITIONAL_INFO, model, locale));
                    });
            return null;
        });
    }

    Stream<Event> getNotifiableEventsStream() {
        return eventRepository.findAll().stream()
                .filter(e -> {
                    int daysBeforeStart = configurationManager.getFor(ASSIGNMENT_REMINDER_START, ConfigurationLevel.event(e)).getValueAsIntOrDefault(10);
                    int days = (int) ChronoUnit.DAYS.between(ZonedDateTime.now(clockProvider.withZone(e.getZoneId())).toLocalDate(), e.getBegin().toLocalDate());
                    return days > 0 && days <= daysBeforeStart;
                });
    }

    private void sendAssignmentReminder(Pair<Event, Set<String>> p) {
        try {
            nestedTransactionTemplate.execute(ts -> {
                Event event = p.getLeft();
                var messageSource = messageSourceManager.getMessageSourceForEvent(event);
                ZoneId eventZoneId = event.getZoneId();
                int quietPeriod = configurationManager.getFor(ASSIGNMENT_REMINDER_INTERVAL, ConfigurationLevel.event(event)).getValueAsIntOrDefault(3);
                p.getRight().stream()
                    .map(id -> findByIdForNotification(id, clockProvider.withZone(eventZoneId), quietPeriod))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(reservation -> {
                        Map<String, Object> model = prepareModelForReservationEmail(event, reservation);
                        ticketReservationRepository.updateLatestReminderTimestamp(reservation.getId(), ZonedDateTime.now(clockProvider.withZone(eventZoneId)));
                        Locale locale = findReservationLanguage(reservation.getId());
                        notificationManager.sendSimpleEmail(event, reservation.getId(), reservation.getEmail(), messageSource.getMessage("reminder.ticket-not-assigned.subject", 
                        		new Object[]{event.getDisplayName()}, locale), () -> templateManager.renderTemplate(event, TemplateResource.REMINDER_TICKETS_ASSIGNMENT_EMAIL, model, locale));
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
        List<TicketReservation> results = ticketReservationRepository.findByPartialID(trimToEmpty(reservationId).toLowerCase() + "%");
        Validate.isTrue(!results.isEmpty(), "reservation not found");
        Validate.isTrue(results.size() == 1, "multiple results found. Try handling this reservation manually.");
        return results.get(0);
    }

    public String getShortReservationID(EventAndOrganizationId event, String reservationId) {
        return configurationManager.getShortReservationID(event, findById(reservationId).orElseThrow());
    }

    public String getShortReservationID(EventAndOrganizationId event, TicketReservation reservation) {
        return configurationManager.getShortReservationID(event, reservation);
    }

    public int countAvailableTickets(EventAndOrganizationId event, TicketCategory category) {
        if(category.isBounded()) {
            return ticketRepository.countFreeTickets(event.getId(), category.getId());
        }
        return ticketRepository.countFreeTicketsForUnbounded(event.getId());
    }

    public void releaseTicket(Event event, TicketReservation ticketReservation, final Ticket ticket) {
        var messageSource = messageSourceManager.getMessageSourceForEvent(event);
        var category = ticketCategoryRepository.getByIdAndActive(ticket.getCategoryId(), event.getId());
        var isFree = ticket.getFinalPriceCts() == 0;

        var configurationLevel = ticket.getCategoryId() != null ? ConfigurationLevel.ticketCategory(event, ticket.getCategoryId()) : ConfigurationLevel.event(event);
        var enableFreeCancellation = configurationManager.getFor(ALLOW_FREE_TICKETS_CANCELLATION, configurationLevel).getValueAsBooleanOrDefault();
        var conditionsMet = CategoryEvaluator.isTicketCancellationAvailable(ticketCategoryRepository, ticket);

        // reported the conditions of TicketDecorator.getCancellationEnabled
        if (!(isFree && enableFreeCancellation && conditionsMet)) {
            throw new IllegalStateException("Cannot release reserved tickets");
        }
        //

        String reservationId = ticketReservation.getId();
        //#365 - reset UUID when releasing a ticket
        int result = ticketRepository.releaseTicket(reservationId, UUID.randomUUID().toString(), event.getId(), ticket.getId());
        Validate.isTrue(result == 1, String.format("Expected 1 row to be updated, got %d", result));
        if(category.isAccessRestricted() || !category.isBounded()) {
            ticketRepository.unbindTicketsFromCategory(event.getId(), category.getId(), singletonList(ticket.getId()));
        }
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        Map<String, Object> model = TemplateResource.buildModelForTicketHasBeenCancelled(organization, event, ticket);
        Locale locale = LocaleUtil.forLanguageTag(Optional.ofNullable(ticket.getUserLanguage()).orElse("en"));
        notificationManager.sendSimpleEmail(event, reservationId, ticket.getEmail(), messageSource.getMessage("email-ticket-released.subject",
                new Object[]{event.getDisplayName()}, locale),
        		() -> templateManager.renderTemplate(event, TemplateResource.TICKET_HAS_BEEN_CANCELLED, model, locale));

        String ticketCategoryDescription = ticketCategoryDescriptionRepository.findByTicketCategoryIdAndLocale(category.getId(), ticket.getUserLanguage()).orElse("");

        List<AdditionalServiceItem> additionalServiceItems = additionalServiceItemRepository.findByReservationUuid(reservationId);
        Map<String, Object> adminModel = TemplateResource.buildModelForTicketHasBeenCancelledAdmin(organization, event, ticket,
            ticketCategoryDescription, additionalServiceItems, asi -> additionalServiceTextRepository.findByLocaleAndType(asi.getAdditionalServiceId(), locale.getLanguage(), AdditionalServiceText.TextType.TITLE));
        notificationManager.sendSimpleEmail(event, null, organization.getEmail(), messageSource.getMessage("email-ticket-released.admin.subject", new Object[]{ticket.getId(), event.getDisplayName()}, locale),
        		() -> templateManager.renderTemplate(event, TemplateResource.TICKET_HAS_BEEN_CANCELLED_ADMIN, adminModel, locale));

        int deletedValues = ticketFieldRepository.deleteAllValuesForTicket(ticket.getId());
        log.debug("deleting {} field values for ticket {}", deletedValues, ticket.getId());

        auditingRepository.insert(reservationId, null, event.getId(), Audit.EventType.CANCEL_TICKET, new Date(), Audit.EntityType.TICKET, Integer.toString(ticket.getId()));

        if(ticketRepository.countTicketsInReservation(reservationId) == 0 && transactionRepository.loadOptionalByReservationId(reservationId).isEmpty()) {
            removeReservation(event, ticketReservation, false, null);
            auditingRepository.insert(reservationId, null, event.getId(), Audit.EventType.CANCEL_RESERVATION, new Date(), Audit.EntityType.RESERVATION, reservationId);
        } else {
            extensionManager.handleTicketCancelledForEvent(event, Collections.singletonList(ticket.getUuid()));
        }
    }

    public int getReservationTimeout(EventAndOrganizationId event) {
        return configurationManager.getFor(RESERVATION_TIMEOUT, ConfigurationLevel.event(event)).getValueAsIntOrDefault(25);
    }

    public void validateAndConfirmOfflinePayment(String reservationId, Event event, BigDecimal paidAmount, String username) {
        TicketReservation reservation = findByPartialID(reservationId);
        Optional<OrderSummary> optionalOrderSummary = optionally(() -> orderSummaryForReservationId(reservation.getId(), event));
        Validate.isTrue(optionalOrderSummary.isPresent(), "Reservation not found");
        OrderSummary orderSummary = optionalOrderSummary.get();
        var currencyCode = orderSummary.getOriginalTotalPrice().getCurrencyCode();
        Validate.isTrue(MonetaryUtil.centsToUnit(orderSummary.getOriginalTotalPrice().getPriceWithVAT(), currencyCode).compareTo(paidAmount) == 0, "paid price differs from due price");
        confirmOfflinePayment(event, reservation.getId(), username);
    }

    public List<TicketReservationWithTransaction> getPendingPayments(String eventName) {
        return eventRepository.findOptionalEventAndOrganizationIdByShortName(eventName)
            .map(event -> ticketSearchRepository.findOfflineReservationsWithOptionalTransaction(event.getId()))
            .orElse(List.of());
    }

    public Integer getPendingPaymentsCount(int eventId) {
        return ticketReservationRepository.findAllReservationsWaitingForPaymentCountInEventId(eventId);
    }

    public List<Pair<TicketReservation, BillingDocument>> findAllInvoices(int eventId) {
        List<BillingDocument> documents = billingDocumentRepository.findAllOfTypeForEvent(BillingDocument.Type.INVOICE, eventId);
        Map<String, BillingDocument> documentsByReservationId = documents.stream().collect(toMap(BillingDocument::getReservationId, Function.identity()));
        return ticketReservationRepository.findByIds(documentsByReservationId.keySet()).stream()
            .map(r -> Pair.of(r, documentsByReservationId.get(r.getId())))
            .collect(toList());
    }

    public Integer countInvoices(int eventId) {
        return ticketReservationRepository.countInvoices(eventId);
    }


    public boolean hasPaidSupplements(String reservationId) {
        return additionalServiceItemRepository.hasPaidSupplements(reservationId);
    }

    public int revertTicketsToFreeIfAccessRestricted(int eventId) {
        List<Integer> restrictedCategories = ticketCategoryRepository.findAllTicketCategories(eventId).stream()
            .filter(TicketCategory::isAccessRestricted)
            .map(TicketCategory::getId)
            .collect(toList());
        if(!restrictedCategories.isEmpty()) {
            int count = ticketRepository.revertToFreeForRestrictedCategories(eventId, restrictedCategories);
            if(count > 0) {
                log.debug("reverted {} tickets for categories {}", count, restrictedCategories);
            }
            return count;
        }
        return 0;
    }


    public void updateReservation(String reservationId, CustomerName customerName, String email,
                                  String billingAddressCompany, String billingAddressLine1, String billingAddressLine2,
                                  String billingAddressZip, String billingAddressCity, String vatCountryCode, String customerReference,
                                  String vatNr,
                                  boolean isInvoiceRequested,
                                  boolean addCompanyBillingDetails,
                                  boolean skipVatNr,
                                  boolean validated,
                                  Locale locale) {


        String completeBillingAddress = buildCompleteBillingAddress(customerName,
            billingAddressCompany,
            billingAddressLine1,
            billingAddressLine2,
            billingAddressZip,
            billingAddressCity,
            vatCountryCode,
            locale);

        ticketReservationRepository.updateTicketReservationWithValidation(reservationId,
            customerName.getFullName(), customerName.getFirstName(), customerName.getLastName(),
            email, billingAddressCompany, billingAddressLine1, billingAddressLine2, billingAddressZip,
            billingAddressCity, completeBillingAddress, vatCountryCode, vatNr, isInvoiceRequested, addCompanyBillingDetails, skipVatNr,
            customerReference,
            validated);
    }

    static String buildCompleteBillingAddress(CustomerName customerName,
                                              String billingAddressCompany,
                                              String billingAddressLine1,
                                              String billingAddressLine2,
                                              String billingAddressZip,
                                              String billingAddressCity,
                                              String countryCode,
                                              Locale locale) {
        String companyName = stripToNull(billingAddressCompany);
        String fullName = stripToEmpty(customerName.getFullName());
        if(companyName != null && !fullName.isEmpty()) {
            companyName = companyName + "\n" + fullName;
        }
        String country = null;
        if(countryCode != null) {
            country = TicketHelper.getLocalizedCountriesForVat(locale).stream()
                .filter(c -> c.getKey().equals(countryCode))
                .map(Pair::getValue)
                .findFirst()
                .orElse(null);
        }

        return Arrays.stream(stripAll(defaultString(companyName, fullName), billingAddressLine1, billingAddressLine2, stripToEmpty(billingAddressZip) + " " + stripToEmpty(billingAddressCity), stripToNull(country)))
            .filter(Predicate.not(StringUtils::isEmpty))
            .collect(joining("\n"));
    }


    public void updateReservationInvoicingAdditionalInformation(String reservationId, EventAndOrganizationId event, TicketReservationInvoicingAdditionalInfo ticketReservationInvoicingAdditionalInfo) {
        auditingRepository.insert(reservationId, null, event.getId(), BILLING_DATA_UPDATED, new Date(), RESERVATION, reservationId, json.asJsonString(List.of(ticketReservationInvoicingAdditionalInfo)));
        ticketReservationRepository.updateInvoicingAdditionalInformation(reservationId, json.asJsonString(ticketReservationInvoicingAdditionalInfo));
    }

    private static Locale getReservationLocale(TicketReservation reservation) {
        return StringUtils.isEmpty(reservation.getUserLanguage()) ? Locale.ENGLISH : LocaleUtil.forLanguageTag(reservation.getUserLanguage());
    }

    public PaymentWebhookResult processTransactionWebhook(String body, String signature, PaymentProxy paymentProxy, Map<String, String> additionalInfo) {
        return processTransactionWebhook(body, signature, paymentProxy, additionalInfo, new PaymentContext());
    }

    public PaymentWebhookResult processTransactionWebhook(String body, String signature, PaymentProxy paymentProxy, Map<String, String> additionalInfo, PaymentContext paymentContext) {
        //load the payment provider using given configuration
        var paymentProviderOptional = paymentManager.streamActiveProvidersByProxyAndCapabilities(paymentProxy, paymentContext, List.of(WebhookHandler.class)).findFirst();
        if(paymentProviderOptional.isEmpty()) {
            return PaymentWebhookResult.error("payment provider not found");
        }

        var paymentProvider = paymentProviderOptional.get();
        if(((WebhookHandler)paymentProvider).requiresSignedBody() && StringUtils.isBlank(signature)) {
            return PaymentWebhookResult.error("signature is missing");
        }

        var optionalTransactionWebhookPayload = ((WebhookHandler)paymentProvider).parseTransactionPayload(body, signature, additionalInfo);
        if(optionalTransactionWebhookPayload.isEmpty()) {
            return PaymentWebhookResult.error("payload not recognized");
        }
        var transactionPayload = optionalTransactionWebhookPayload.get();

        var optionalReservation = ticketReservationRepository.findOptionalReservationById(transactionPayload.getReservationId());
        if(optionalReservation.isEmpty()) {
            return PaymentWebhookResult.notRelevant("reservation not found");
        }
        var reservation = optionalReservation.get();
        var optionalTransaction = transactionRepository.lockLatestForUpdate(reservation.getId());
        if(optionalTransaction.isEmpty()) {
            return PaymentWebhookResult.notRelevant("transaction not found");
        }
        var transaction = optionalTransaction.get();

        if(reservationStatusNotCompatible(reservation) || transaction.getStatus() != Transaction.Status.PENDING) {
            log.warn("discarding transaction webhook {} for reservation id {} ({}). Transaction status is: {}", transactionPayload.getType(), reservation.getId(), reservation.getStatus(), transaction.getStatus());
            return PaymentWebhookResult.notRelevant("reservation status is not compatible");
        }


        //FIXME in some cases the reload is redundant
        //reload the payment provider, this time within a more sensible context
        var paymentContextReloaded = new PaymentContext(eventRepository.findByReservationId(reservation.getId()));
        return paymentManager.lookupProviderByTransactionAndCapabilities(transaction, List.of(WebhookHandler.class))
            .map(provider -> {
                var paymentWebhookResult = ((WebhookHandler)provider).processWebhook(transactionPayload, transaction, paymentContextReloaded);
                var event = eventRepository.findByReservationId(reservation.getId());
                String operationType = transactionPayload.getType();
                return handlePaymentWebhookResult(event, paymentProvider, paymentWebhookResult, reservation, transaction, paymentContextReloaded, operationType, true);
            })
            .orElseGet(() -> PaymentWebhookResult.error("payment provider not found"));
    }

    private PaymentWebhookResult handlePaymentWebhookResult(Event event,
                                                            PaymentProvider paymentProvider,
                                                            PaymentWebhookResult paymentWebhookResult,
                                                            TicketReservation reservation,
                                                            Transaction transaction,
                                                            PaymentContext paymentContext,
                                                            String operationType,
                                                            boolean moveToWatingExternalConfirmationAllowed) {

        switch(paymentWebhookResult.getType()) {
            case NOT_RELEVANT: {
                log.trace("Discarding event {} for reservation {}", operationType, reservation.getId());
                break;
            }
            case TRANSACTION_INITIATED: {
                if(reservation.getStatus() == EXTERNAL_PROCESSING_PAYMENT && moveToWatingExternalConfirmationAllowed) {
                    String status = WAITING_EXTERNAL_CONFIRMATION.name();
                    log.trace("Event {} received. Setting status {} for reservation {}", operationType, status, reservation.getId());
                    ticketReservationRepository.updateReservationStatus(reservation.getId(), status);
                } else {
                    log.trace("Ignoring Event {}, as it cannot be applied for reservation {} ({})", operationType, reservation.getId(), reservation.getStatus());
                }
                break;
            }
            case SUCCESSFUL: {
                log.trace("Event {} for reservation {} has been successfully processed.", operationType, reservation.getId());
                var totalPrice = totalReservationCostWithVAT(reservation).getLeft();
                var paymentToken = paymentWebhookResult.getPaymentToken();
                var paymentSpecification = new PaymentSpecification(reservation, totalPrice, event, paymentToken,
                    orderSummaryForReservation(reservation, event), true, eventHasPrivacyPolicy(event));
                transitionToComplete(paymentSpecification, totalPrice, paymentToken.getPaymentProvider(), null);
                break;
            }
            case FAILED: {

                // depending on when we actually receive the event, we could have two possibilities:
                //
                //      1) the user is still waiting on the payment page. In this case, there's no harm in reverting the reservation status to PENDING
                //      2) the user has given up and we're officially in background mode.
                //
                // either way, we have to notify the user about the charge failure. Then:
                //
                //      - if the reservation has expired, we cancel it and keep its data for reference, and we notify also the organizer.
                //      - if the reservation is still valid, we can ensure that the user has at least 10 min left to retry

                log.debug("Event {} for reservation {} has failed with reason: {}", operationType, reservation.getId(), paymentWebhookResult.getReason());

                Date expiration = reservation.getValidity();
                Date now = new Date();
                int slackTime = configurationManager.getFor(RESERVATION_MIN_TIMEOUT_AFTER_FAILED_PAYMENT, paymentContext.getConfigurationLevel()).getValueAsIntOrDefault(10);
                PaymentMethod paymentMethodForTransaction = paymentProvider.getPaymentMethodForTransaction(transaction);
                if(expiration.before(now)) {
                    sendTransactionFailedEmail(event, reservation, paymentMethodForTransaction, paymentWebhookResult, true);
                    cancelReservation(reservation, false, null);
                    break;
                } else if(DateUtils.addMinutes(expiration, -slackTime).before(now)) {
                    ticketReservationRepository.updateValidity(reservation.getId(), DateUtils.addMinutes(now, slackTime));
                }
                reTransitionToPending(reservation.getId(), false);
                sendTransactionFailedEmail(event, reservation, paymentMethodForTransaction, paymentWebhookResult, false);
                break;
            }
            case CANCELLED: {
                reTransitionToPending(reservation.getId(), false);
                log.debug("Event {} for reservation {} has been cancelled", operationType, reservation.getId());
                break;
            }
            default:
                // do nothing for ERROR/REJECTED
                break;
        }
        return paymentWebhookResult;
    }

    public Optional<PaymentResult> forceTransactionCheck(Event event, TicketReservation reservation) {
        var optionalTransaction = transactionRepository.loadOptionalByReservationIdAndStatusForUpdate(reservation.getId(), Transaction.Status.PENDING);
        if(optionalTransaction.isEmpty()) {
            return Optional.empty();
        }
        var transaction = optionalTransaction.get();
        PaymentContext paymentContext = new PaymentContext(event, reservation.getId());
        return checkTransactionStatus(event, reservation)
            .map(providerAndWebhookResult -> {
                var paymentWebhookResult = providerAndWebhookResult.getRight();
                handlePaymentWebhookResult(event, providerAndWebhookResult.getLeft(), paymentWebhookResult, reservation, transaction, paymentContext, "force-check", true);

                switch(paymentWebhookResult.getType()) {
                    case FAILED:
                    case REJECTED:
                    case CANCELLED:
                        return PaymentResult.failed(paymentWebhookResult.getReason());
                    case NOT_RELEVANT:
                    case ERROR:
                        // to be on the safe side, we ignore errors when trying to reload the payment
                        // because they could be caused by network/availability problems
                        return PaymentResult.pending(transaction.getPaymentId());
                    case TRANSACTION_INITIATED:
                        return StringUtils.isNotEmpty(paymentWebhookResult.getRedirectUrl()) ? PaymentResult.redirect(paymentWebhookResult.getRedirectUrl()) : PaymentResult.pending(transaction.getPaymentId());
                    default:
                        return PaymentResult.successful(paymentWebhookResult.getPaymentToken().getToken());
                }

            });
    }

    private Optional<Pair<PaymentProvider, PaymentWebhookResult>> checkTransactionStatus(Event event, TicketReservation reservation) {
        var optionalTransaction = transactionRepository.loadOptionalByReservationIdAndStatusForUpdate(reservation.getId(), Transaction.Status.PENDING);
        if(optionalTransaction.isEmpty()) {
            return Optional.empty();
        }
        var transaction = optionalTransaction.get();
        PaymentContext paymentContext = new PaymentContext(event, reservation.getId());
        return paymentManager.lookupProviderByTransactionAndCapabilities(transaction, List.of(WebhookHandler.class))
            .map(provider -> Pair.of(provider, ((WebhookHandler)provider).forceTransactionCheck(reservation, transaction, paymentContext)));
    }

    private boolean reservationStatusNotCompatible(TicketReservation reservation) {
        TicketReservationStatus status = reservation.getStatus();
        return status != EXTERNAL_PROCESSING_PAYMENT && status != WAITING_EXTERNAL_CONFIRMATION;
    }

    private void sendTransactionFailedEmail(Event event, TicketReservation reservation, PaymentMethod paymentMethod, PaymentWebhookResult paymentWebhookResult, boolean cancelReservation) {
        var shortReservationID = getShortReservationID(event, reservation);
        var messageSource = messageSourceManager.getMessageSourceForEvent(event);
        Map<String, Object> model = Map.of(
        "organization", organizationRepository.getById(event.getOrganizationId()),
        "reservationCancelled", cancelReservation,
        "reservation", reservation,
        "reservationId", shortReservationID,
        "eventName", event.getDisplayName(),
        "provider", Objects.requireNonNullElse(paymentMethod.name(), ""),
        "reason", paymentWebhookResult.getReason(),
        "reservationUrl", reservationUrl(reservation, event));

        Locale locale = LocaleUtil.forLanguageTag(reservation.getUserLanguage());
        if(cancelReservation || configurationManager.getFor(NOTIFY_ALL_FAILED_PAYMENT_ATTEMPTS, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault()) {
            notificationManager.sendSimpleEmail(event, reservation.getId(), reservation.getEmail(), messageSource.getMessage("email-transaction-failed.subject",
                new Object[]{shortReservationID, event.getDisplayName()}, locale),
            	() -> templateManager.renderTemplate(event, TemplateResource.CHARGE_ATTEMPT_FAILED_EMAIL_FOR_ORGANIZER, model, locale),
                List.of());
        }

        notificationManager.sendSimpleEmail(event, reservation.getId(), reservation.getEmail(), messageSource.getMessage("email-transaction-failed.subject",
            new Object[]{shortReservationID, event.getDisplayName()}, locale),
        	() -> templateManager.renderTemplate(event, TemplateResource.CHARGE_ATTEMPT_FAILED_EMAIL, model, locale),
            List.of());

    }

    public Optional<TransactionInitializationToken> initTransaction(Event event, String reservationId, PaymentMethod paymentMethod, Map<String, List<String>> params) {
        ticketReservationRepository.lockReservationForUpdate(reservationId);
        var reservation = ticketReservationRepository.findReservationById(reservationId);
        var transactionRequest = new TransactionRequest(totalReservationCostWithVAT(reservation).getLeft(), ticketReservationRepository.getBillingDetailsForReservation(reservationId));
        var optionalProvider = paymentManager.lookupProviderByMethodAndCapabilities(paymentMethod, new PaymentContext(event), transactionRequest, List.of(WebhookHandler.class, ServerInitiatedTransaction.class));
        if (optionalProvider.isEmpty()) {
            return Optional.empty();
        }
        var messageSource = messageSourceManager.getMessageSourceForEvent(event);
        var provider = (ServerInitiatedTransaction) optionalProvider.get();
        var paymentSpecification = new PaymentSpecification(reservation,
            totalReservationCostWithVAT(reservation).getLeft(), event, null,
            orderSummaryForReservation(reservation, event), false, false);
        if(!acquireGroupMembers(reservationId, event)) {
            groupManager.deleteWhitelistedTicketsForReservation(reservationId);
            var errorMessage = messageSource.getMessage("error.STEP2_WHITELIST", null, LocaleUtil.forLanguageTag(reservation.getUserLanguage()));
            return Optional.of(provider.errorToken(errorMessage, false));
        }
        var transactionToken = provider.initTransaction(paymentSpecification, params);
        if(transitionToExternalProcessingPayment(reservation)) {
           auditingRepository.insert(reservationId, null, event.getId(), INIT_PAYMENT, new Date(), RESERVATION, reservationId);
        }
        return Optional.of(transactionToken);
    }

    private boolean transitionToExternalProcessingPayment(TicketReservation reservation) {
        var reservationId = reservation.getId();
        var optionalTransaction = transactionRepository.loadOptionalByReservationId(reservation.getId());
        if(optionalTransaction.filter(ot -> ot.getStatus() == Transaction.Status.PENDING).isEmpty()) {
            log.warn("trying to transition reservation {} to EXTERNAL_PROCESSING_PAYMENT but the current transaction is not PENDING. Ignoring the request...", reservationId);
            return false;
        }
        if(reservation.getStatus() != PENDING) {
            log.warn("trying to transition reservation {} to EXTERNAL_PROCESSING_PAYMENT but the current status is {}. Ignoring the request...", reservationId, reservation.getStatus());
            return false;
        }
        ticketReservationRepository.updateReservationStatus(reservation.getId(), EXTERNAL_PROCESSING_PAYMENT.name());
        return true;
    }

    public void checkOfflinePaymentsStatus() {
        eventRepository.findAllActives(ZonedDateTime.now(clockProvider.getClock()))
            .forEach(this::checkOfflinePaymentsForEvent);
    }

    public Optional<String> createTicketReservation(Event event,
                                                    List<TicketReservationWithOptionalCodeModification> list,
                                                    List<ASReservationWithOptionalCodeModification> additionalServices,
                                                    Optional<String> promoCodeDiscount,
                                                    Locale locale,
                                                    BindingResult bindingResult) {
        Date expiration = DateUtils.addMinutes(new Date(), getReservationTimeout(event));
        try {
            String reservationId = createTicketReservation(event,
                list, additionalServices, expiration,
                promoCodeDiscount,
                locale, false);
            return Optional.of(reservationId);
        } catch (TicketReservationManager.NotEnoughTicketsException nete) {
            bindingResult.reject(ErrorsCode.STEP_1_NOT_ENOUGH_TICKETS);
        } catch (TicketReservationManager.MissingSpecialPriceTokenException missing) {
            bindingResult.reject(ErrorsCode.STEP_1_ACCESS_RESTRICTED);
        } catch (TicketReservationManager.InvalidSpecialPriceTokenException invalid) {
            bindingResult.reject(ErrorsCode.STEP_1_CODE_NOT_FOUND);
        } catch (TicketReservationManager.TooManyTicketsForDiscountCodeException tooMany) {
            bindingResult.reject(ErrorsCode.STEP_2_DISCOUNT_CODE_USAGE_EXCEEDED);
        } catch (CannotProceedWithPayment cannotProceedWithPayment) {
            bindingResult.reject(ErrorsCode.STEP_1_CATEGORIES_NOT_COMPATIBLE);
            log.error("missing payment methods", cannotProceedWithPayment);
        }
        return Optional.empty();
    }

    boolean canProceedWithPayment(Event event, TotalPrice totalPrice, String reservationId) {
        if(!totalPrice.requiresPayment()) {
            return true;
        }
        var categoriesInReservation = ticketRepository.getCategoriesIdToPayInReservation(reservationId);
        var blacklistedPaymentMethods = configurationManager.getBlacklistedMethodsForReservation(event, categoriesInReservation);
        var transactionRequest = new TransactionRequest(totalPrice, ticketReservationRepository.getBillingDetailsForReservation(reservationId));
        var availableMethods = paymentManager.getPaymentMethods(event, transactionRequest).stream().filter(pm -> pm.getStatus() == PaymentMethodStatus.ACTIVE && pm.getPaymentMethod() != PaymentMethod.NONE).collect(toList());
        if(availableMethods.size() == 0  || availableMethods.stream().allMatch(pm -> blacklistedPaymentMethods.contains(pm.getPaymentMethod()))) {
            log.error("Cannot proceed with reservation. No payment methods available {} or all blacklisted {}", availableMethods, blacklistedPaymentMethods);
            return false;
        }
        return true;
    }

    public Result<Boolean> discardMatchingPayment(String eventName,
                                                  String reservationId,
                                                  int transactionId) {
        return eventRepository.findOptionalByShortName(eventName)
            .flatMap(e -> ticketReservationRepository.findOptionalReservationById(reservationId).map(r -> Pair.of(e, r)))
            .flatMap(pair -> transactionRepository.loadOptionalByIdAndStatus(transactionId, Transaction.Status.OFFLINE_PENDING_REVIEW)
                .map(transaction -> {
                    auditingRepository.insert(reservationId, null, pair.getLeft().getId(), Audit.EventType.MATCHING_PAYMENT_DISCARDED, new Date(), RESERVATION, reservationId);
                    Validate.isTrue(transactionRepository.discardMatchingPayment(transactionId) == 1, "Transaction is in an incompatible state.");
                    return Result.success(true);
                })
            ).orElse(Result.error(ErrorCode.EventError.NOT_FOUND));
    }

    public Optional<PromoCodeDiscount> checkPromoCodeIsValid(Optional<PromoCodeDiscount> promotionCodeDiscount, Event event) {
        if (!promotionCodeDiscount.isEmpty()
            && promotionCodeDiscount.get().getAlfioMetadata().getTags() != null
            && promotionCodeDiscount.get().getAlfioMetadata().getTags().size() > 0) {
            //maybe carnet?
            var eventMetadata = eventRepository.getMetadataForEvent(event.getId());
            if (eventMetadata.getAttributes()!=null
                && eventMetadata.getAttributes().size() > 0
                && eventMetadata.getAttributes().containsKey(Event.EventOccurrence.CARNET.toString())){
                //cannot use this promoCode: current event is a carnet too...
                promotionCodeDiscount = Optional.empty();
            } else if (eventMetadata.getTags() == null
                && eventMetadata.getTags().size() == 0) {
                //cannot use this promoCode: this event has no tags
                promotionCodeDiscount = Optional.empty();
            } else {
                var shouldApplyCode = false;
                for (var tag : eventMetadata.getTags()) {
                    if (promotionCodeDiscount.get().getAlfioMetadata().getTags().contains(tag)){
                        shouldApplyCode = true;
                        break;
                    }
                }
                if (!shouldApplyCode) {
                    //cannot use this promoCode: tags don't match!
                    promotionCodeDiscount = Optional.empty();
                }
            }
        }
        return promotionCodeDiscount;
    }

    private void checkOfflinePaymentsForEvent(Event event) {
        log.trace("check offline payments for event {}", event.getShortName());
        var paymentContext = new PaymentContext(event);
        var providers = paymentManager.streamActiveProvidersByProxyAndCapabilities(PaymentProxy.OFFLINE, paymentContext, List.of(OfflineProcessor.class))
            .collect(toList());
        if(providers.isEmpty()) {
            log.trace("No active offline provider has been found. Exiting...");
            return;
        }
        var pendingReservationsMap = ticketSearchRepository.findOfflineReservationsWithPendingTransaction(event.getId()).stream()
            .collect(toMap(tr -> tr.getTicketReservation().getId(), Function.identity()));

        if(pendingReservationsMap.isEmpty()) {
            log.trace("no pending reservations found. Exiting...");
            return;
        }

        var errors = new ArrayList<String>();
        var confirmed = new ArrayList<String>();
        var pendingReview = new ArrayList<String>();
        int matchingCount = 0;
        for (int i = 0; !pendingReservationsMap.isEmpty() && i < providers.size(); i++) {
            OfflineProcessor offlineProcessor = (OfflineProcessor) providers.get(i);
            Result<List<String>> matching = offlineProcessor.checkPendingReservations(pendingReservationsMap.values(), paymentContext, null);
            if(matching.isSuccess()) {
                int resultSize = matching.getData().size();
                matchingCount += resultSize;
                log.trace("found {} matches for provider {}", matchingCount, offlineProcessor.getClass().getName());
                if(resultSize > 0) {
                    processResults(event, pendingReservationsMap, errors, confirmed, pendingReview, matching);
                }
            }
        }
        if(matchingCount > 0) {
            var organization = organizationRepository.getById(event.getOrganizationId());
            var cc = notificationManager.getCCForEventOrganizer(event);
            var subject = String.format("%d matching payments found for: %s", matchingCount, event.getDisplayName());

            Map<String, Object> model = Map.of(
                "matchingCount", matchingCount,
                "eventName", event.getDisplayName(),
                "pendingReviewMatches", !pendingReview.isEmpty(),
                "pendingReview", pendingReview,
                "automaticApprovedMatches", !confirmed.isEmpty(),
                "automaticApproved", confirmed,
                "automaticApprovalErrors", !errors.isEmpty(),
                "approvalErrors", errors
            );
            notificationManager.sendSimpleEmail(event, null, organization.getEmail(), cc, subject,
            	() -> templateManager.renderTemplate(event, TemplateResource.OFFLINE_PAYMENT_MATCHES_FOUND, model, Locale.ENGLISH));
        }


    }

    private void processResults(Event event, Map<String, TicketReservationWithTransaction> pendingReservationsMap, ArrayList<String> errors, ArrayList<String> confirmed, ArrayList<String> pendingReview, Result<List<String>> matching) {
        var reservations = ticketSearchRepository.findOfflineReservationsWithTransaction(matching.getData());
        var byStatus = reservations.stream()
            .peek(tr -> {
                var reservationId = tr.getTicketReservation().getId();
                auditingRepository.insert(reservationId, null, event.getId(),
                    MATCHING_PAYMENT_FOUND, new Date(), RESERVATION, reservationId, json.asJsonString(List.of(tr.getTransaction().getMetadata())));
                pendingReservationsMap.remove(reservationId);
            })
            .collect(groupingBy(tr -> tr.getTransaction().getStatus()));

        byStatus.getOrDefault(Transaction.Status.OFFLINE_MATCHING_PAYMENT_FOUND, List.of()).forEach(tr -> {
            var reservationId = tr.getTicketReservation().getId();
            if(automaticConfirmOfflinePayment(event, reservationId)) {
                log.trace("reservation {} confirmed automatically", reservationId);
                auditingRepository.insert(reservationId, null, event.getId(), Audit.EventType.AUTOMATIC_PAYMENT_CONFIRMATION, new Date(), RESERVATION, reservationId);
                confirmed.add(reservationId);
            } else {
                log.trace("got error while confirming reservation {}", reservationId);
                auditingRepository.insert(reservationId, null, event.getId(), Audit.EventType.AUTOMATIC_PAYMENT_CONFIRMATION_FAILED, new Date(), RESERVATION, reservationId);
                errors.add(reservationId);
            }
        });

        pendingReview.addAll(byStatus.getOrDefault(Transaction.Status.OFFLINE_PENDING_REVIEW, List.of()).stream().map(tr -> tr.getTicketReservation().getId()).collect(toList()));
    }

    private boolean automaticConfirmOfflinePayment(Event event, String reservationId) {
        try {
            confirmOfflinePayment(event, reservationId, null);
            return true;
        } catch(Exception ex) {
            log.warn("Unable to confirm reservation "+reservationId, ex);
            return false;
        }
    }
}
