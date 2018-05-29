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

import alfio.manager.support.DuplicateReferenceException;
import alfio.model.*;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.decorator.TicketPriceContainer;
import alfio.model.modification.AdminReservationModification;
import alfio.model.modification.AdminReservationModification.Attendee;
import alfio.model.modification.AdminReservationModification.Category;
import alfio.model.modification.AdminReservationModification.TicketsInfo;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.result.Result.ResultStatus;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.*;
import alfio.repository.user.UserRepository;
import alfio.util.Json;
import alfio.util.MonetaryUtil;
import alfio.util.TemplateManager;
import alfio.util.TemplateResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.Audit.EntityType.RESERVATION;
import static alfio.model.Audit.EntityType.TICKET;
import static alfio.model.Audit.EventType.*;
import static alfio.model.modification.DateTimeModification.fromZonedDateTime;
import static alfio.util.EventUtil.generateEmptyTickets;
import static alfio.util.OptionalWrapper.optionally;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.*;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Component
@Log4j2
@RequiredArgsConstructor
public class AdminReservationManager {

    private static final EnumSet<TicketReservationStatus> UPDATE_INVOICE_STATUSES = EnumSet.of(TicketReservationStatus.OFFLINE_PAYMENT, TicketReservationStatus.PENDING);
    private final EventManager eventManager;
    private final TicketReservationManager ticketReservationManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final SpecialPriceRepository specialPriceRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final EventRepository eventRepository;
    private final PlatformTransactionManager transactionManager;
    private final SpecialPriceTokenGenerator specialPriceTokenGenerator;
    private final TicketFieldRepository ticketFieldRepository;
    private final PaymentManager paymentManager;
    private final NotificationManager notificationManager;
    private final MessageSource messageSource;
    private final TemplateManager templateManager;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final AuditingRepository auditingRepository;
    private final UserRepository userRepository;
    private final ExtensionManager extensionManager;

    //the following methods have an explicit transaction handling, therefore the @Transactional annotation is not helpful here
    public Result<Triple<TicketReservation, List<Ticket>, Event>> confirmReservation(String eventName, String reservationId, String username) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        TransactionTemplate template = new TransactionTemplate(transactionManager, definition);
        return template.execute(status -> {
            try {
                Result<Triple<TicketReservation, List<Ticket>, Event>> result = eventRepository.findOptionalByShortName(eventName)
                    .flatMap(e -> optionally(() -> {
                        eventManager.checkOwnership(e, username, e.getOrganizationId());
                        return e;
                    })).map(event -> ticketReservationRepository.findOptionalReservationById(reservationId)
                        .filter(r -> r.getStatus() == TicketReservationStatus.PENDING || r.getStatus() == TicketReservationStatus.STUCK)
                        .map(r -> performConfirmation(reservationId, event, r))
                        .orElseGet(() -> Result.error(ErrorCode.ReservationError.UPDATE_FAILED))
                    ).orElseGet(() -> Result.error(ErrorCode.ReservationError.NOT_FOUND));
                if(!result.isSuccess()) {
                    log.debug("Reservation confirmation failed for eventName: {} reservationId: {}, username: {}", eventName, reservationId, username);
                    status.setRollbackOnly();
                }
                return result;
            } catch (Exception e) {
                log.error("Error during confirmation of reservation eventName: {} reservationId: {}, username: {}", eventName, reservationId, username);
                status.setRollbackOnly();
                return Result.error(singletonList(ErrorCode.custom("", e.getMessage())));
            }
        });
    }

    public Result<Boolean> updateReservation(String eventName, String reservationId, AdminReservationModification adminReservationModification, String username) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        TransactionTemplate template = new TransactionTemplate(transactionManager, definition);
        return template.execute(status -> {
            try {
                Result<Boolean> result = eventRepository.findOptionalByShortName(eventName)
                    .flatMap(e -> optionally(() -> {
                        eventManager.checkOwnership(e, username, e.getOrganizationId());
                        return e;
                    })).map(event -> ticketReservationRepository.findOptionalReservationById(reservationId)
                        .map(r -> performUpdate(reservationId, event, r, adminReservationModification))
                        .orElseGet(() -> Result.error(ErrorCode.ReservationError.UPDATE_FAILED))
                    ).orElseGet(() -> Result.error(ErrorCode.ReservationError.NOT_FOUND));
                if(!result.isSuccess()) {
                    log.debug("Application error detected eventName: {} reservationId: {}, username: {}, reservation: {}", eventName, reservationId, username, AdminReservationModification.summary(adminReservationModification));
                    status.setRollbackOnly();
                }
                return result;
            } catch (Exception e) {
                log.error("Error during update of reservation eventName: {} reservationId: {}, username: {}, reservation: {}", eventName, reservationId, username, AdminReservationModification.summary(adminReservationModification));
                status.setRollbackOnly();
                return Result.error(singletonList(ErrorCode.custom("", e.getMessage())));
            }
        });
    }

    public Result<Pair<TicketReservation, List<Ticket>>> createReservation(AdminReservationModification input, String eventName, String username) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        TransactionTemplate template = new TransactionTemplate(transactionManager, definition);
        return template.execute(status -> {
            try {
                Result<Pair<TicketReservation, List<Ticket>>> result = eventRepository.findOptionalByShortNameForUpdate(eventName)
                    .map(e -> validateTickets(input, e))
                    .map(r -> r.flatMap(p -> transactionalCreateReservation(p.getRight(), p.getLeft(), username)))
                    .orElse(Result.error(ErrorCode.EventError.NOT_FOUND));
                if (!result.isSuccess()) {
                    log.debug("Error during update of reservation eventName: {}, username: {}, reservation: {}", eventName, username, AdminReservationModification.summary(input));
                    status.setRollbackOnly();
                }
                return result;
            } catch (Exception e) {
                log.error("Error during update of reservation eventName: {}, username: {}, reservation: {}", eventName, username, AdminReservationModification.summary(input));
                status.setRollbackOnly();
                return Result.error(singletonList(ErrorCode.custom(e instanceof DuplicateReferenceException ? "duplicate-reference" : "", e.getMessage())));
            }
        });
    }

    //end - the public / package protected methods below must be annotated with @Transactional

    @Transactional
    public Result<Boolean> notify(String eventName, String reservationId, AdminReservationModification arm, String username) {
        AdminReservationModification.Notification notification = arm.getNotification();
        return eventRepository.findOptionalByShortName(eventName)
            .flatMap(e -> optionally(() -> {
                eventManager.checkOwnership(e, username, e.getOrganizationId());
                return e;
            }).flatMap(ev -> ticketReservationRepository.findOptionalReservationById(reservationId).map(r -> Pair.of(e, r))))
            .map(pair -> {
                Event event = pair.getLeft();
                TicketReservation reservation = pair.getRight();
                if(notification.isCustomer()){
                    ticketReservationManager.sendConfirmationEmail(event, reservation, Locale.forLanguageTag(reservation.getUserLanguage()));
                }
                if(notification.isAttendees()) {
                    ticketRepository.findTicketsInReservation(reservationId)
                        .stream()
                        .filter(Ticket::getAssigned)
                        .forEach(t -> {
                            Locale locale = Locale.forLanguageTag(t.getUserLanguage());
                            ticketReservationManager.sendTicketByEmail(t, locale, event, ticketReservationManager.getTicketEmailGenerator(event, reservation, locale));
                        });
                }
                return Result.success(true);
            }).orElseGet(() -> Result.error(ErrorCode.EventError.NOT_FOUND));

    }

    private Result<Boolean> performUpdate(String reservationId, Event event, TicketReservation r, AdminReservationModification arm) {
        ticketReservationRepository.updateValidity(reservationId, Date.from(arm.getExpiration().toZonedDateTime(event.getZoneId()).toInstant()));
        if(arm.isUpdateContactData()) {
            AdminReservationModification.CustomerData customerData = arm.getCustomerData();
            ticketReservationRepository.updateTicketReservation(reservationId, r.getStatus().name(), customerData.getEmailAddress(),
                customerData.getFullName(), customerData.getFirstName(), customerData.getLastName(), customerData.getUserLanguage(),
                customerData.getBillingAddress(), r.getConfirmationTimestamp(),
                Optional.ofNullable(r.getPaymentMethod()).map(PaymentProxy::name).orElse(null), customerData.getCustomerReference());
        }
        arm.getTicketsInfo().stream()
            .filter(TicketsInfo::isUpdateAttendees)
            .flatMap(ti -> ti.getAttendees().stream())
            .forEach(a -> ticketRepository.updateTicketOwnerById(a.getTicketId(), trimToNull(a.getEmailAddress()),
                trimToNull(a.getFullName()), trimToNull(a.getFirstName()), trimToNull(a.getLastName())));
        return Result.success(true);
    }

    @Transactional
    public Result<Triple<TicketReservation, List<Ticket>, Event>> loadReservation(String eventName, String reservationId, String username) {
        return eventRepository.findOptionalByShortName(eventName)
            .flatMap(e -> optionally(() -> {
                eventManager.checkOwnership(e, username, e.getOrganizationId());
                return e;
            })).map(r -> loadReservation(reservationId))
            .orElseGet(() -> Result.error(ErrorCode.ReservationError.NOT_FOUND));
    }


    private Result<Triple<TicketReservation, List<Ticket>, Event>> loadReservation(String reservationId) {
        return ticketReservationRepository.findOptionalReservationById(reservationId)
            .map(r -> Triple.of(r, ticketRepository.findTicketsInReservation(reservationId), eventRepository.findByReservationId(reservationId)))
            .map(Result::success)
            .orElseGet(() -> Result.error(ErrorCode.ReservationError.NOT_FOUND));
    }

    private Result<Triple<TicketReservation, List<Ticket>, Event>> performConfirmation(String reservationId, Event event, TicketReservation original) {
        try {
            ticketReservationManager.completeReservation(event.getId(), reservationId, original.getEmail(), new CustomerName(original.getFullName(), original.getFirstName(), original.getLastName(), event),
                Locale.forLanguageTag(original.getUserLanguage()), original.getBillingAddress(), Optional.empty(), PaymentProxy.ADMIN, original.getCustomerReference());
            return loadReservation(reservationId);
        } catch(Exception e) {
            return Result.error(ErrorCode.ReservationError.UPDATE_FAILED);
        }
    }

    @Transactional
    Result<Pair<Event, AdminReservationModification>> validateTickets(AdminReservationModification input, Event event) {
        Set<String> keys = input.getTicketsInfo().stream().flatMap(ti -> ti.getAttendees().stream())
            .flatMap(a -> a.getAdditionalInfo().keySet().stream())
            .map(String::toLowerCase)
            .distinct()
            .collect(toSet());

        if(keys.size() == 0) {
            return Result.success(Pair.of(event, input));
        }

        List<String> existing = ticketFieldRepository.getExistingFields(event.getId(), keys);
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
            String specialPriceSessionId = UUID.randomUUID().toString();
            Date validity = Date.from(arm.getExpiration().toZonedDateTime(event.getZoneId()).toInstant());
            ticketReservationRepository.createNewReservation(reservationId, ZonedDateTime.now(event.getZoneId()), validity, null,
                arm.getLanguage(), event.getId(), event.getVat(), event.isVatIncluded());
            AdminReservationModification.CustomerData customerData = arm.getCustomerData();
            ticketReservationRepository.updateTicketReservation(reservationId, TicketReservationStatus.PENDING.name(), customerData.getEmailAddress(),
                customerData.getFullName(), customerData.getFirstName(), customerData.getLastName(), arm.getLanguage(),
                customerData.getBillingAddress(), null, null, customerData.getCustomerReference());

            Result<List<Ticket>> result = flattenTicketsInfo(event, empty, t)
                .map(pair -> reserveForTicketsInfo(event, arm, reservationId, specialPriceSessionId, pair))
                .reduce(this::reduceReservationResults)
                .orElseGet(() -> Result.error(ErrorCode.custom("", "unknown error")));

            updateInvoiceReceiptModel(event, arm.getLanguage(), reservationId);

            return result.map(list -> Pair.of(ticketReservationRepository.findReservationById(reservationId), list));
        });
    }

    private void updateInvoiceReceiptModel(Event event, String language, String reservationId) {
        OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event, Locale.forLanguageTag(language));
        ticketReservationRepository.addReservationInvoiceOrReceiptModel(reservationId, Json.toJson(orderSummary));
    }

    private Result<List<Ticket>> reserveForTicketsInfo(Event event, AdminReservationModification arm, String reservationId, String specialPriceSessionId, Pair<TicketCategory, TicketsInfo> pair) {
        TicketCategory category = pair.getLeft();
        TicketsInfo ticketsInfo = pair.getRight();
        int categoryId = category.getId();
        List<Attendee> attendees = ticketsInfo.getAttendees();
        List<Integer> reservedForUpdate = ticketReservationManager.reserveTickets(event.getId(), categoryId, attendees.size(), singletonList(Ticket.TicketStatus.FREE));
        if (reservedForUpdate.size() == 0 || reservedForUpdate.size() != attendees.size()) {
            return Result.error(ErrorCode.CategoryError.NOT_ENOUGH_SEATS);
        }
        ticketRepository.reserveTickets(reservationId, reservedForUpdate, categoryId, arm.getLanguage(), category.getSrcPriceCts());
        Ticket ticket = ticketRepository.findById(reservedForUpdate.get(0), categoryId);
        TicketPriceContainer priceContainer = TicketPriceContainer.from(ticket, null, event, null);
        ticketRepository.updateTicketPrice(reservedForUpdate, categoryId, event.getId(), category.getSrcPriceCts(), MonetaryUtil.unitToCents(priceContainer.getFinalPrice()), MonetaryUtil.unitToCents(priceContainer.getVAT()), MonetaryUtil.unitToCents(priceContainer.getAppliedDiscount()));
        List<SpecialPrice> codes = category.isAccessRestricted() ? bindSpecialPriceTokens(specialPriceSessionId, categoryId, attendees) : Collections.emptyList();
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

    private <T> Result<T> reduceResults(Result<T> r1, Result<T> r2, BiFunction<T, T, T> processData) {
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

    private List<SpecialPrice> bindSpecialPriceTokens(String specialPriceSessionId, int categoryId, List<Attendee> attendees) {
        specialPriceTokenGenerator.generatePendingCodesForCategory(categoryId);
        List<SpecialPrice> codes = specialPriceRepository.findActiveNotAssignedByCategoryId(categoryId)
            .stream()
            .limit(attendees.size())
            .collect(toList());
        codes.forEach(c -> specialPriceRepository.updateStatus(c.getId(), SpecialPrice.Status.PENDING.toString(), specialPriceSessionId));
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
            if(!attendee.isEmpty()) {
                Integer ticketId = reservedForUpdate.get(i);
                ticketRepository.updateTicketOwnerById(ticketId, attendee.getEmailAddress(), attendee.getFullName(), attendee.getFirstName(), attendee.getLastName());
                if(StringUtils.isNotBlank(attendee.getReference()) || attendee.isReassignmentForbidden()) {
                    updateExtRefAndLocking(categoryId, attendee, ticketId);
                }
                if(!attendee.getAdditionalInfo().isEmpty()) {
                    ticketFieldRepository.updateOrInsert(attendee.getAdditionalInfo(), ticketId, event.getId());
                }
                specialPriceIterator.map(Iterator::next).ifPresent(code -> ticketRepository.reserveTicket(reservationId, ticketId, code.getId(), userLanguage, srcPriceCts));
            }
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
            .map(tc -> Collections.singletonList(new TicketsInfo(new Category(tc.getId(), tc.getName(), tc.getPrice()), ti.getAttendees(), ti.isAddSeatsIfNotAvailable(), ti.isUpdateAttendees())));
    }

    private Result<TicketCategory> createCategory(TicketsInfo ti, Event event, AdminReservationModification reservation, String username) {
        Category category = ti.getCategory();
        List<Attendee> attendees = ti.getAttendees();
        DateTimeModification inception = fromZonedDateTime(ZonedDateTime.now(event.getZoneId()));

        int tickets = attendees.size();
        TicketCategoryModification tcm = new TicketCategoryModification(category.getExistingCategoryId(), category.getName(), tickets,
            inception, reservation.getExpiration(), Collections.emptyMap(), category.getPrice(), true, "",
            true, null, null, null, null, null);
        int notAllocated = getNotAllocatedTickets(event);
        int missingTickets = Math.max(tickets - notAllocated, 0);
        Event modified = increaseSeatsIfNeeded(ti, event, missingTickets, event);
        return eventManager.insertCategory(modified, tcm, username).map(id -> ticketCategoryRepository.getByIdAndActive(id, event.getId()));
    }

    private Event increaseSeatsIfNeeded(TicketsInfo ti, Event event, int missingTickets, Event modified) {
        if(missingTickets > 0 && ti.isAddSeatsIfNotAvailable()) {
            createMissingTickets(event, missingTickets);
            //update seats and reload event
            log.debug("adding {} extra seats to the event", missingTickets);
            eventRepository.updateAvailableSeats(event.getId(), eventRepository.countExistingTickets(event.getId()) + missingTickets);
            modified = eventRepository.findById(event.getId());
        }
        return modified;
    }

    private int getNotAllocatedTickets(Event event) {
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
            TicketCategoryModification tcm = new TicketCategoryModification(existingCategoryId, existing.getName(), maxTickets,
                fromZonedDateTime(existing.getInception(modified.getZoneId())), fromZonedDateTime(existing.getExpiration(event.getZoneId())),
                Collections.emptyMap(), existing.getPrice(), existing.isAccessRestricted(), "", true, existing.getCode(),
                fromZonedDateTime(existing.getValidCheckInFrom(modified.getZoneId())),
                fromZonedDateTime(existing.getValidCheckInTo(modified.getZoneId())),
                fromZonedDateTime(existing.getTicketValidityStart(modified.getZoneId())),
                fromZonedDateTime(existing.getTicketValidityEnd(modified.getZoneId())));
            return eventManager.updateCategory(existingCategoryId, modified, tcm, username, true);
        }
        return Result.success(existing);
    }

    private void createMissingTickets(Event event, int tickets) {
        final MapSqlParameterSource[] params = generateEmptyTickets(event, Date.from(ZonedDateTime.now(event.getZoneId()).toInstant()), tickets, Ticket.TicketStatus.FREE).toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(ticketRepository.bulkTicketInitialization(), params);
    }

    @Transactional
    public void removeTickets(String eventName, String reservationId, List<Integer> ticketIds, List<Integer> toRefund, boolean notify, boolean forceInvoiceReceiptUpdate, String username) {
        loadReservation(eventName, reservationId, username).ifSuccess((res) -> {
            Event e = res.getRight();
            TicketReservation reservation = res.getLeft();
            List<Ticket> tickets = res.getMiddle();
            Map<Integer, Ticket> ticketsById = tickets.stream().collect(Collectors.toMap(Ticket::getId, Function.identity()));
            Set<Integer> ticketIdsInReservation = tickets.stream().map(Ticket::getId).collect(toSet());
            // ensure that all the tickets ids are present in tickets
            Assert.isTrue(ticketIdsInReservation.containsAll(ticketIds), "Some ticket ids are not contained in the reservation");
            Assert.isTrue(ticketIdsInReservation.containsAll(toRefund), "Some ticket ids to refund are not contained in the reservation");
            //

            removeTicketsFromReservation(reservation, e, ticketIds, notify, username, false, forceInvoiceReceiptUpdate);
            //

            handleTicketsRefund(toRefund, e, reservation, ticketsById, username);

            if(tickets.size() - ticketIds.size() <= 0) {
                markAsCancelled(reservation);
                additionalServiceItemRepository.updateItemsStatusWithReservationUUID(reservation.getId(), AdditionalServiceItem.AdditionalServiceItemStatus.CANCELLED);
            }
        });
    }

    @Transactional
    public Result<List<Audit>> getAudit(String eventName, String reservationId, String username) {
        return loadReservation(eventName, reservationId, username).map((res) -> auditingRepository.findAllForReservation(reservationId));
    }

    @Transactional
    public Result<TransactionAndPaymentInfo> getPaymentInfo(String eventName, String reservationId, String username) {
        return loadReservation(eventName, reservationId, username)
            .map((res) -> paymentManager.getInfo(res.getLeft(), res.getRight()));
    }

    @Transactional
    public void removeReservation(String eventName, String reservationId, boolean refund, boolean notify, String username) {
        loadReservation(eventName, reservationId, username).ifSuccess((res) -> {
            Event e = res.getRight();
            TicketReservation reservation = res.getLeft();
            List<Ticket> tickets = res.getMiddle();

            removeTicketsFromReservation(reservation, e, tickets.stream().map(Ticket::getId).collect(toList()), notify, username, true, false);

            additionalServiceItemRepository.updateItemsStatusWithReservationUUID(reservation.getId(), AdditionalServiceItem.AdditionalServiceItemStatus.CANCELLED);

            if(refund && reservation.getPaymentMethod() != null && reservation.getPaymentMethod().isSupportRefund()) {
                //fully refund
                paymentManager.refund(reservation, e, Optional.empty(), username);
            }

            markAsCancelled(reservation);
        });
    }

    @Transactional
    public Result<Boolean> refund(String eventName, String reservationId, BigDecimal refundAmount, String username) {
        return loadReservation(eventName, reservationId, username).map((res) -> {
            Event e = res.getRight();
            TicketReservation reservation = res.getLeft();
            return reservation.getPaymentMethod() != null
                && reservation.getPaymentMethod().isSupportRefund()
                && paymentManager.refund(reservation, e, Optional.of(MonetaryUtil.unitToCents(refundAmount)), username);
        });
    }

    private void removeTicketsFromReservation(TicketReservation reservation, Event event, List<Integer> ticketIds, boolean notify, String username, boolean removeReservation, boolean forceInvoiceReceiptUpdate) {
        String reservationId = reservation.getId();
        if(notify && !ticketIds.isEmpty()) {
            Organization o = eventManager.loadOrganizer(event, username);
            ticketRepository.findByIds(ticketIds).forEach(t -> {
                if(StringUtils.isNotBlank(t.getEmail())) {
                    sendTicketHasBeenRemoved(event, o, t);
                }
            });
        }

        Integer userId = userRepository.findIdByUserName(username).orElse(null);
        Date date = new Date();

        ticketIds.forEach(id -> auditingRepository.insert(reservationId, userId, event.getId(), CANCEL_TICKET, date, TICKET, id.toString()));

        ticketRepository.resetCategoryIdForUnboundedCategoriesWithTicketIds(ticketIds);
        ticketFieldRepository.deleteAllValuesForTicketIds(ticketIds);
        MapSqlParameterSource[] args = ticketIds.stream().map(id -> new MapSqlParameterSource("ticketId", id)
            .addValue("reservationId", reservationId)
            .addValue("eventId", event.getId())
            .addValue("newUuid", UUID.randomUUID().toString())
        ).toArray(MapSqlParameterSource[]::new);
        List<String> reservationIds = ticketRepository.findReservationIds(ticketIds);
        List<String> ticketUUIDs = ticketRepository.findUUIDs(ticketIds);
        int[] results = jdbc.batchUpdate(ticketRepository.batchReleaseTickets(), args);
        Validate.isTrue(Arrays.stream(results).sum() == args.length, "Failed to update tickets");
        if(!removeReservation) {
            //#407 update invoice/receipt model only if the reservation is still "PENDING", otherwise we could lead to accountancy problems
            if(UPDATE_INVOICE_STATUSES.contains(reservation.getStatus()) || forceInvoiceReceiptUpdate) {
                Audit.EventType eventType = forceInvoiceReceiptUpdate ? FORCED_UPDATE_INVOICE : UPDATE_INVOICE;
                auditingRepository.insert(reservationId, userId, event.getId(), eventType, date, RESERVATION, reservationId);
                updateInvoiceReceiptModel(event, reservation.getUserLanguage(), reservationId);
            }
            extensionManager.handleTicketCancelledForEvent(event, ticketUUIDs);
        } else {
            extensionManager.handleReservationsCancelledForEvent(event, reservationIds);
        }
    }

    private void sendTicketHasBeenRemoved(Event event, Organization organization, Ticket ticket) {
        Map<String, Object> model = TemplateResource.buildModelForTicketHasBeenCancelled(organization, event, ticket);
        Locale locale = Locale.forLanguageTag(Optional.ofNullable(ticket.getUserLanguage()).orElse("en"));
        notificationManager.sendSimpleEmail(event, ticket.getEmail(), messageSource.getMessage("email-ticket-released.subject",
            new Object[]{event.getDisplayName()}, locale),
            () -> templateManager.renderTemplate(event, TemplateResource.TICKET_HAS_BEEN_CANCELLED, model, locale));
    }

    private void markAsCancelled(TicketReservation ticketReservation) {
        ticketReservationRepository.updateReservationStatus(ticketReservation.getId(), TicketReservationStatus.CANCELLED.toString());
    }

    private void handleTicketsRefund(List<Integer> toRefund, Event e, TicketReservation reservation, Map<Integer, Ticket> ticketsById, String username) {
        if(reservation.getPaymentMethod() == null || !reservation.getPaymentMethod().isSupportRefund()) {
            return;
        }
        // refund each selected ticket
        for(Integer toRefundId : toRefund) {
            int toBeRefunded = ticketsById.get(toRefundId).getFinalPriceCts();
            if(toBeRefunded > 0) {
                paymentManager.refund(reservation, e, Optional.of(toBeRefunded), username);
            }
        }
        //
    }
}
