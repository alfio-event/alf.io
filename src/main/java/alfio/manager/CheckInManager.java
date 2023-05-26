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

import alfio.manager.support.*;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.Ticket.TicketStatus;
import alfio.model.api.v1.admin.CheckInLogEntry;
import alfio.model.audit.ScanAudit;
import alfio.model.checkin.AttendeeSearchResults;
import alfio.model.decorator.TicketPriceContainer;
import alfio.model.support.CheckInOutputColorConfiguration;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.*;
import alfio.repository.audit.ScanAuditRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.util.*;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static alfio.manager.support.CheckInStatus.*;
import static alfio.model.Audit.EventType.*;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.Wrappers.optionally;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

@Component
@Transactional
public class CheckInManager {

    private static final Logger log = LoggerFactory.getLogger(CheckInManager.class);

    static final Pattern CYPHER_SPLITTER = Pattern.compile("\\|");
    private static final int SEARCH_ATTENDEES_LIMIT = 20;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final TicketFieldRepository ticketFieldRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final ScanAuditRepository scanAuditRepository;
    private final AuditingRepository auditingRepository;
    private final ConfigurationManager configurationManager;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final TicketReservationManager ticketReservationManager;
    private final ExtensionManager extensionManager;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final PollRepository pollRepository;
    private final ClockProvider clockProvider;

    public CheckInManager(TicketRepository ticketRepository,
                          EventRepository eventRepository,
                          TicketReservationRepository ticketReservationRepository,
                          TicketFieldRepository ticketFieldRepository,
                          TicketCategoryRepository ticketCategoryRepository,
                          ScanAuditRepository scanAuditRepository,
                          AuditingRepository auditingRepository,
                          ConfigurationManager configurationManager,
                          OrganizationRepository organizationRepository,
                          UserRepository userRepository,
                          TicketReservationManager ticketReservationManager,
                          ExtensionManager extensionManager,
                          AdditionalServiceItemRepository additionalServiceItemRepository,
                          PollRepository pollRepository,
                          ClockProvider clockProvider) {
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
        this.ticketReservationRepository = ticketReservationRepository;
        this.ticketFieldRepository = ticketFieldRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.scanAuditRepository = scanAuditRepository;
        this.auditingRepository = auditingRepository;
        this.configurationManager = configurationManager;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.ticketReservationManager = ticketReservationManager;
        this.extensionManager = extensionManager;
        this.additionalServiceItemRepository = additionalServiceItemRepository;
        this.pollRepository = pollRepository;
        this.clockProvider = clockProvider;
    }


    private void checkIn(String uuid) {
        Ticket ticket = ticketRepository.findByUUID(uuid);
        Validate.isTrue(ticket.getStatus() == TicketStatus.ACQUIRED);
        ticketRepository.updateTicketStatusWithUUID(uuid, TicketStatus.CHECKED_IN.toString());
        ticketRepository.toggleTicketLocking(ticket.getId(), ticket.getCategoryId(), true);
        extensionManager.handleTicketCheckedIn(ticketRepository.findByUUID(uuid));
    }

    private void acquire(String uuid) {
        Ticket ticket = ticketRepository.findByUUID(uuid);
        Validate.isTrue(ticket.getStatus() == TicketStatus.TO_BE_PAID);
        ticketRepository.updateTicketStatusWithUUID(uuid, TicketStatus.ACQUIRED.toString());
        ticketReservationManager.registerAlfioTransactionForOnsitePayment(eventRepository.findById(ticket.getEventId()), ticket.getTicketsReservationId());
    }

    public AttendeeSearchResults searchAttendees(Event event, String query, int page) {
        if (StringUtils.isBlank(query)) {
            return new AttendeeSearchResults(0, 0, 0, 0, List.of());
        }
        int eventId = event.getId();
        var search = "%" + query + "%";
        var results = ticketRepository.searchAttendees(eventId, search, SEARCH_ATTENDEES_LIMIT, SEARCH_ATTENDEES_LIMIT * page);
        var statistics = ticketRepository.countSearchResults(eventId, search);
        var attendees = results.stream().map(fi -> {
            var ticket = fi.getTicket();
            var reservation = fi.getTicketReservation();
            String amountToPay = null;
            if (reservation.getPaymentMethod() == PaymentProxy.ON_SITE) {
                var priceContainer = TicketPriceContainer.from(ticket, reservation.getVatStatus(), reservation.getVAT(), event.getVatStatus(), reservation.getDiscount().orElse(null));
                amountToPay = event.getCurrency() + " " + MonetaryUtil.formatUnit(priceContainer.getFinalPrice(), event.getCurrency());
            }
            return new AttendeeSearchResults.Attendee(ticket.getUuid(), ticket.getFirstName(),
                ticket.getLastName(), fi.getTicketCategory().getName(), fi.getTicketAdditionalInfo(),
                ticket.getStatus(), amountToPay);
        }).collect(Collectors.toList());
        int totalPages = (int) Math.ceil((statistics.getTotal() / (double) SEARCH_ATTENDEES_LIMIT));
        return new AttendeeSearchResults(statistics.getTotal(), statistics.getCheckedIn(), totalPages, page, attendees);
    }

    /**
     * Simplified and more performant procedure for online events' check-in
     *
     * @param ticket
     * @param event
     * @return
     */
    public CheckInStatus performCheckinForOnlineEvent(Ticket ticket, EventCheckInInfo event, TicketCategory tc) {
        Validate.isTrue(EventUtil.isAccessOnline(tc, event));
        if(!tc.hasValidCheckIn(event.now(clockProvider), event.getZoneId())) {
            return INVALID_TICKET_CATEGORY_CHECK_IN_DATE;
        }
        if(ticket.isCheckedIn()) {
            //ticket is already checked in, there's no reason to attempt an update of the record.
            return ALREADY_CHECK_IN;
        }
        int affectedCount = ticketRepository.performCheckIn(ticket.getUuid(), event.getId());
        if(affectedCount == 1) {
            auditingRepository.insert(ticket.getTicketsReservationId(), null, event.getId(), CHECK_IN, new Date(), Audit.EntityType.TICKET, Integer.toString(ticket.getId()));
            extensionManager.handleTicketCheckedIn(ticket);
            return SUCCESS;
        }
        // if affected count is not "1" we return a failure
        return ERROR;
    }

    public TicketAndCheckInResult confirmOnSitePayment(String eventName, String ticketIdentifier, Optional<String> ticketCode, String username, String auditUser) {
        return eventRepository.findOptionalByShortName(eventName)
            .filter(EventManager.checkOwnership(username, organizationRepository))
            .flatMap(e -> confirmOnSitePayment(ticketIdentifier).map((String s) -> Pair.of(s, e)))
            .map(p -> checkIn(p.getRight().getId(), ticketIdentifier, ticketCode, auditUser))
            .orElseGet(() -> new TicketAndCheckInResult(null, new DefaultCheckInResult(CheckInStatus.TICKET_NOT_FOUND, "")));
    }

    public Optional<String> confirmOnSitePayment(String ticketIdentifier) {
        Optional<String> uuid = findAndLockTicket(ticketIdentifier)
            .filter(t -> t.getStatus() == TicketStatus.TO_BE_PAID)
            .map(Ticket::getUuid);

        uuid.ifPresent(this::acquire);
        return uuid;
    }

    public TicketAndCheckInResult checkIn(String eventShortName, String ticketIdentifier, Optional<String> ticketCode, String username, String auditUser,
                                          boolean automaticallyConfirmOnSitePayment) {
        return eventRepository.findOptionalByShortName(eventShortName)
            .filter(EventManager.checkOwnership(username, organizationRepository))
            .map(e -> {
                if (automaticallyConfirmOnSitePayment && CheckInStatus.MUST_PAY == evaluateTicketStatus(eventShortName, ticketIdentifier, ticketCode).getResult().getStatus()) {
                    log.info("in event {} automaticallyConfirmOnSitePayment for {}", eventShortName, ticketIdentifier);
                    confirmOnSitePayment(eventShortName, ticketIdentifier, ticketCode, username, auditUser);
                }
                return checkIn(e.getId(), ticketIdentifier, ticketCode, auditUser);
            })
            .orElseGet(() -> new TicketAndCheckInResult(null, new DefaultCheckInResult(CheckInStatus.EVENT_NOT_FOUND, "event not found")));
    }

    public TicketAndCheckInResult checkIn(String shortName, String ticketIdentifier, Optional<String> ticketCode, String username, String auditUser) {
        return checkIn(shortName, ticketIdentifier, ticketCode, username, auditUser, false);
    }

    public TicketAndCheckInResult checkIn(int eventId, String ticketIdentifier, Optional<String> ticketCode, String user) {
        TicketAndCheckInResult descriptor = extractStatus(eventId, ticketRepository.findByUUIDForUpdate(ticketIdentifier), ticketIdentifier, ticketCode);
        var checkInStatus = descriptor.getResult().getStatus();
        if(checkInStatus == OK_READY_TO_BE_CHECKED_IN) {
            checkIn(ticketIdentifier);
            TicketWithCategory ticket = descriptor.getTicket();
            scanAuditRepository.insert(ticketIdentifier, eventId, ZonedDateTime.now(clockProvider.getClock()), user, SUCCESS, ScanAudit.Operation.SCAN);
            auditingRepository.insert(ticket.getTicketsReservationId(), userRepository.findIdByUserName(user).orElse(null), eventId, CHECK_IN, new Date(), Audit.EntityType.TICKET, Integer.toString(descriptor.getTicket().getId()));
            // return also additional items, if any
            return new SuccessfulCheckIn(ticket, getAdditionalServicesForTicket(ticket), loadBoxColor(ticket));
        } else if(checkInStatus == BADGE_SCAN_ALREADY_DONE || checkInStatus == OK_READY_FOR_BADGE_SCAN) {
            var auditingStatus = checkInStatus == OK_READY_FOR_BADGE_SCAN ? BADGE_SCAN_SUCCESS : checkInStatus;
            scanAuditRepository.insert(ticketIdentifier, eventId, ZonedDateTime.now(clockProvider.getClock()), user, auditingStatus, ScanAudit.Operation.SCAN);
            auditingRepository.insert(descriptor.getTicket().getTicketsReservationId(), userRepository.findIdByUserName(user).orElse(null), eventId, BADGE_SCAN, new Date(), Audit.EntityType.TICKET, Integer.toString(descriptor.getTicket().getId()));
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(auditingStatus, checkInStatus == OK_READY_FOR_BADGE_SCAN ? "scan successful" : "already scanned"));
        }
        return descriptor;
    }

    public boolean manualCheckIn(int eventId, String ticketIdentifier, String user) {
        Optional<Ticket> ticket = findAndLockTicket(ticketIdentifier);
        return ticket.map(t -> {

            if(t.getStatus() == TicketStatus.TO_BE_PAID) {
                acquire(ticketIdentifier);
            }

            checkIn(ticketIdentifier);
            scanAuditRepository.insert(ticketIdentifier, eventId, ZonedDateTime.now(clockProvider.getClock()), user, SUCCESS, ScanAudit.Operation.SCAN);
            auditingRepository.insert(t.getTicketsReservationId(), userRepository.findIdByUserName(user).orElse(null), eventId, Audit.EventType.MANUAL_CHECK_IN, new Date(), Audit.EntityType.TICKET, Integer.toString(t.getId()));
            return true;
        }).orElse(false);
    }

    public boolean revertCheckIn(int eventId, String ticketIdentifier, String user) {
        return findAndLockTicket(ticketIdentifier).map(t -> {
            if(t.getStatus() == TicketStatus.CHECKED_IN) {
                TicketReservation reservation = ticketReservationRepository.findReservationById(t.getTicketsReservationId());
                TicketStatus revertedStatus = reservation.getPaymentMethod() == PaymentProxy.ON_SITE ? TicketStatus.TO_BE_PAID : TicketStatus.ACQUIRED;
                ticketRepository.updateTicketStatusWithUUID(ticketIdentifier, revertedStatus.toString());
                scanAuditRepository.insert(ticketIdentifier, eventId, ZonedDateTime.now(clockProvider.getClock()), user, OK_READY_TO_BE_CHECKED_IN, ScanAudit.Operation.REVERT);
                auditingRepository.insert(t.getTicketsReservationId(), userRepository.findIdByUserName(user).orElse(null), eventId, Audit.EventType.REVERT_CHECK_IN, new Date(), Audit.EntityType.TICKET, Integer.toString(t.getId()));
                extensionManager.handleTicketRevertCheckedIn(ticketRepository.findByUUID(ticketIdentifier));
                return true;
            }
            return false;
        }).orElse(false);
    }

    private Optional<Ticket> findAndLockTicket(String uuid) {
        return ticketRepository.findByUUIDForUpdate(uuid);
    }

    public TicketAndCheckInResult evaluateTicketStatus(int eventId, String ticketIdentifier, Optional<String> ticketCode) {
        return extractStatus(eventRepository.findOptionalById(eventId), ticketRepository.findOptionalByUUID(ticketIdentifier), ticketIdentifier, ticketCode);
    }

    public TicketAndCheckInResult evaluateTicketStatus(String eventName, String ticketIdentifier, Optional<String> ticketCode) {
        return extractStatus(eventRepository.findOptionalByShortName(eventName), ticketRepository.findOptionalByUUID(ticketIdentifier), ticketIdentifier, ticketCode);
    }

    private TicketAndCheckInResult extractStatus(int eventId, Optional<Ticket> maybeTicket, String ticketIdentifier, Optional<String> ticketCode) {
        return extractStatus(eventRepository.findOptionalById(eventId), maybeTicket, ticketIdentifier, ticketCode);
    }

    private TicketAndCheckInResult extractStatus(Optional<? extends EventCheckInInfo> maybeEvent, Optional<Ticket> maybeTicket, String ticketIdentifier, Optional<String> ticketCode) {

        if (maybeEvent.isEmpty()) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(EVENT_NOT_FOUND, "Event not found"));
        }

        if (maybeTicket.isEmpty()) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(TICKET_NOT_FOUND, "Ticket with uuid " + ticketIdentifier + " not found"));
        }

        Ticket ticket = maybeTicket.get();
        if(ticket.getCategoryId() == null) {
            return new TicketAndCheckInResult(new TicketWithCategory(ticket, null), new DefaultCheckInResult(INVALID_TICKET_STATE, "Invalid ticket state"));
        }

        TicketCategory tc = ticketCategoryRepository.getById(ticket.getCategoryId());

        EventCheckInInfo event = maybeEvent.get();
        if(ticketCode.filter(StringUtils::isNotBlank).isEmpty()) {
            if(ticket.isCheckedIn() && tc.getTicketCheckInStrategy() == TicketCategory.TicketCheckInStrategy.ONCE_PER_DAY) {
                if(!isBadgeValidNow(tc, event)) {
                    // if the badge is not currently valid, we give an error
                    return new TicketAndCheckInResult(new TicketWithCategory(ticket, null), new DefaultCheckInResult(INVALID_TICKET_CATEGORY_CHECK_IN_DATE, "Not allowed to check in at this time."));
                }
                var ticketsReservationId = ticket.getTicketsReservationId();
                int previousScan = auditingRepository.countAuditsOfTypesInTheSameDay(ticketsReservationId, Set.of(CHECK_IN.name(), MANUAL_CHECK_IN.name(), BADGE_SCAN.name()), event.now(clockProvider));
                if(previousScan > 0) {
                    return new TicketAndCheckInResult(new TicketWithCategory(ticket, null), new DefaultCheckInResult(BADGE_SCAN_ALREADY_DONE, "Badge scan already done"));
                }
                return new TicketAndCheckInResult(new TicketWithCategory(ticket, null), new DefaultCheckInResult(OK_READY_FOR_BADGE_SCAN, "Badge scan already done"));
            }
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(EMPTY_TICKET_CODE, "Missing ticket code"));
        }

        String code = ticketCode.get();

        ZonedDateTime now = event.now(clockProvider);
        if(!tc.hasValidCheckIn(now, event.getZoneId())) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy - hh:mm");
            String from = tc.getValidCheckInFrom() == null ? ".." : formatter.format(tc.getValidCheckInFrom(event.getZoneId()));
            String to = tc.getValidCheckInTo() == null ? ".." : formatter.format(tc.getValidCheckInTo(event.getZoneId()));
            String formattedNow = formatter.format(now);
            return new TicketAndCheckInResult(new TicketWithCategory(ticket, tc), new DefaultCheckInResult(INVALID_TICKET_CATEGORY_CHECK_IN_DATE,
                String.format("Invalid check-in date: valid range for category %s is from %s to %s, current time is: %s",
                    tc.getName(), from, to, formattedNow)));
        }

        if (!code.equals(ticket.ticketCode(event.getPrivateKey(), event.supportsQRCodeCaseInsensitive()))) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(INVALID_TICKET_CODE, "Ticket qr code does not match"));
        }

        final TicketStatus ticketStatus = ticket.getStatus();

        if (ticketStatus == TicketStatus.TO_BE_PAID) {
            return new TicketAndCheckInResult(new TicketWithCategory(ticket, tc), new OnSitePaymentResult(MUST_PAY, "Must pay for ticket", MonetaryUtil.centsToUnit(ticket.getFinalPriceCts(), ticket.getCurrencyCode()), ticket.getCurrencyCode()));
        }

        if (ticketStatus == TicketStatus.CHECKED_IN) {
            return new TicketAndCheckInResult(new TicketWithCategory(ticket, tc), new DefaultCheckInResult(ALREADY_CHECK_IN, "Error: already checked in"));
        }

        if (ticket.getStatus() != TicketStatus.ACQUIRED) {
            return new TicketAndCheckInResult(new TicketWithCategory(ticket, tc), new DefaultCheckInResult(INVALID_TICKET_STATE, "Invalid ticket state, expected ACQUIRED state, received " + ticket.getStatus()));
        }

        return new TicketAndCheckInResult(new TicketWithCategory(ticket, tc), new DefaultCheckInResult(OK_READY_TO_BE_CHECKED_IN, "Ready to be checked in"));
    }

    private static boolean isBadgeValidNow(TicketCategory tc, EventCheckInInfo event) {
        var zoneId = event.getZoneId();
        var now = ZonedDateTime.now(ClockProvider.clock().withZone(zoneId));
        return now.isAfter(toZoneIdIfNotNull(tc.getValidCheckInFrom(), zoneId).orElse(event.getBegin()))
            && now.isAfter(toZoneIdIfNotNull(tc.getTicketValidityStart(), zoneId).orElse(event.getBegin()))
            && now.isBefore(toZoneIdIfNotNull(tc.getTicketValidityEnd(), zoneId).orElse(event.getEnd()));
    }

    private static Optional<ZonedDateTime> toZoneIdIfNotNull(ZonedDateTime in, ZoneId zoneId) {
        return Optional.ofNullable(in).map(d -> d.withZoneSameInstant(zoneId));
    }

    static Pair<Cipher, SecretKeySpec>  getCypher(String key) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            int iterations = 1000;
            int keyLength = 256;
            PBEKeySpec spec = new PBEKeySpec(key.toCharArray(), key.getBytes(StandardCharsets.UTF_8), iterations, keyLength);
            SecretKey secretKey = factory.generateSecret(spec);
            SecretKeySpec secret = new SecretKeySpec(secretKey.getEncoded(), "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            return Pair.of(cipher, secret);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String encrypt(String key, String payload)  {
        try {
            Pair<Cipher, SecretKeySpec> cipherAndSecret = getCypher(key);
            Cipher cipher = cipherAndSecret.getKey();
            cipher.init(Cipher.ENCRYPT_MODE, cipherAndSecret.getRight());
            byte[] data = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            return Base64.encodeBase64URLSafeString(iv) + "|" + Base64.encodeBase64URLSafeString(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Integer> getAttendeesIdentifiers(EventAndOrganizationId ev, Date changedSince, String username) {
        return Optional.ofNullable(ev)
            .filter(EventManager.checkOwnership(username, organizationRepository))
            .filter(isOfflineCheckInEnabled())
            .map(event -> ticketRepository.findAllAssignedByEventIdForCheckIn(event.getId(), changedSince))
            .orElseGet(Collections::emptyList);
    }

    public List<Integer> getAttendeesIdentifiers(int eventId, Date changedSince, String username) {
        return eventRepository.findOptionalById(eventId)
            .filter(EventManager.checkOwnership(username, organizationRepository))
            .map(event -> ticketRepository.findAllAssignedByEventIdForCheckIn(event.getId(), changedSince))
            .orElse(Collections.emptyList());
    }

    public List<FullTicketInfo> getAttendeesInformation(int eventId, List<Integer> ids, String username) {
        return eventRepository.findOptionalById(eventId)
            .filter(EventManager.checkOwnership(username, organizationRepository))
            .map(event -> ticketRepository.findAllFullTicketInfoAssignedByEventId(event.getId(), ids))
            .orElse(Collections.emptyList());
    }

    public Predicate<EventAndOrganizationId> isOfflineCheckInEnabled() {
        return configurationManager.areBooleanSettingsEnabledForEvent(ALFIO_PI_INTEGRATION_ENABLED, OFFLINE_CHECKIN_ENABLED);
    }

    public Predicate<EventAndOrganizationId> isOfflineCheckInAndLabelPrintingEnabled() {
        return isOfflineCheckInEnabled().and(configurationManager.areBooleanSettingsEnabledForEvent(LABEL_PRINTING_ENABLED));
    }

    public Map<String,String> getEncryptedAttendeesInformation(Event ev, Set<String> additionalFields, List<Integer> ids) {

        return Optional.ofNullable(ev).filter(isOfflineCheckInEnabled()).map(event -> {
            boolean caseInsensitiveQRCode = ev.supportsQRCodeCaseInsensitive();
            Map<Integer, TicketCategory> categories = ticketCategoryRepository.findByEventIdAsMap(event.getId());
            String eventKey = event.getPrivateKey();

            Function<FullTicketInfo, String> hashedHMAC = ticket -> DigestUtils.sha256Hex(ticket.hmacTicketInfo(eventKey, caseInsensitiveQRCode));
            var outputColorConfiguration = getOutputColorConfiguration(event, configurationManager);

            // fetch polls for event, in order to determine if we have to print PIN or not
            var polls = pollRepository.findAllForEvent(event.getId());
            boolean hasPolls = !polls.isEmpty();
            var allowedTags = hasPolls ? polls.stream().flatMap(p -> p.allowedTags().stream()).collect(Collectors.toList()) : List.<String>of();

            Function<FullTicketInfo, String> encryptedBody = ticket -> {
                Map<String, String> info = new HashMap<>();
                info.put("firstName", ticket.getFirstName());
                info.put("lastName", ticket.getLastName());
                info.put("fullName", ticket.getFullName());
                info.put("email", ticket.getEmail());
                info.put("status", ticket.getStatus().toString());
                info.put("uuid", ticket.getUuid());
                if(hasPolls && (allowedTags.isEmpty() || CollectionUtils.containsAny(allowedTags, ticket.getTags()))) {
                    info.put("pin", PinGenerator.uuidToPin(ticket.getUuid()));
                }
                info.put("category", ticket.getTicketCategory().getName());
                if(outputColorConfiguration != null) {
                    info.put("boxColor", detectBoxColor(outputColorConfiguration, ticket.getCategoryId()));
                }

                if (!additionalFields.isEmpty()) {
                    Map<String, String> fields = new HashMap<>();
                    fields.put("company", trimToEmpty(ticket.getBillingDetails().getCompanyName()));
                    fields.put("category", ticket.getTicketCategory().getName());
                    fields.putAll(ticketFieldRepository.findValueForTicketId(ticket.getId(), additionalFields).stream()
                        .map(vd -> {
                            try {
                                if(StringUtils.isNotBlank(vd.getDescription())) {
                                    Map<String, Object> description = Json.GSON.fromJson(vd.getDescription(), new TypeToken<Map<String, Object>>(){}.getType());
                                    Object rv = description.get("restrictedValues");
                                    if(rv instanceof Map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, String> restrictedValues = (Map<String, String>) rv;
                                        return Pair.of(vd.getName(), restrictedValues.getOrDefault(vd.getValue(), vd.getValue()));
                                    }
                                }
                            } catch (Exception e) {
                                log.error("cannot deserialize restricted values", e);
                            }
                            return Pair.of(vd.getName(), vd.getValue());
                        })
                        .collect(toMap(Pair::getLeft, Pair::getRight)));
                    info.put("additionalInfoJson", Json.toJson(fields));
                }

                //
                TicketCategory tc = categories.get(ticket.getCategoryId());
                if (tc.getValidCheckInFrom() != null) {
                    info.put("validCheckInFrom", Long.toString(tc.getValidCheckInFrom(event.getZoneId()).toEpochSecond()));
                }
                if (tc.getValidCheckInTo() != null) {
                    info.put("validCheckInTo", Long.toString(tc.getValidCheckInTo(event.getZoneId()).toEpochSecond()));
                }
                if (tc.getTicketValidityStart() != null) {
                    info.put("ticketValidityStart", Long.toString(tc.getTicketValidityStart(event.getZoneId()).toEpochSecond()));
                }
                if (tc.getTicketValidityEnd() != null) {
                    info.put("ticketValidityEnd", Long.toString(tc.getTicketValidityEnd(event.getZoneId()).toEpochSecond()));
                }
                info.put("categoryCheckInStrategy", tc.getTicketCheckInStrategy().name());
                //

                var additionalServicesInfo = getAdditionalServicesForTicket(ticket);
                if(!additionalServicesInfo.isEmpty()) {
                    info.put("additionalServicesInfoJson", Json.toJson(additionalServicesInfo));
                }
                String key = ticket.ticketCode(eventKey, caseInsensitiveQRCode);
                return encrypt(key, Json.toJson(info));
            };
            return ticketRepository.findAllFullTicketInfoAssignedByEventId(event.getId(), ids)
                .stream()
                .collect(toMap(hashedHMAC, encryptedBody));

        }).orElseGet(Collections::emptyMap);
    }

    static CheckInOutputColorConfiguration getOutputColorConfiguration(EventAndOrganizationId event, ConfigurationManager configurationManager) {
        return configurationManager.getFor(CHECK_IN_COLOR_CONFIGURATION, event.getConfigurationLevel()).getValue()
            .flatMap(str -> optionally(() -> Json.fromJson(str, CheckInOutputColorConfiguration.class)))
            .orElse(null);
    }

    private String loadBoxColor(TicketInfoContainer ticket) {
        var eventAndOrganizationId = eventRepository.findEventAndOrganizationIdById(ticket.getEventId());
        return detectBoxColor(getOutputColorConfiguration(eventAndOrganizationId, configurationManager), ticket.getCategoryId());
    }

    private static String detectBoxColor(CheckInOutputColorConfiguration outputColorConfiguration, Integer categoryId) {
        if(outputColorConfiguration == null) {
            return null;
        }
        return outputColorConfiguration.getConfigurations().stream()
            .filter(cc -> cc.getCategories().contains(categoryId))
            .map(CheckInOutputColorConfiguration.ColorConfiguration::getColorName)
            .findFirst()
            .orElse(outputColorConfiguration.getDefaultColorName());
    }

    List<AdditionalServiceInfo> getAdditionalServicesForTicket(TicketInfoContainer ticket) {

        // temporary: return a result only for the first ticket
        String ticketsReservationId = ticket.getTicketsReservationId();
        int firstId = ticketRepository.findFirstTicketIdInReservation(ticketsReservationId).orElseThrow();
        if(ticket.getId() != firstId) {
            return List.of();
        }

        List<BookedAdditionalService> additionalServices = additionalServiceItemRepository.getAdditionalServicesBookedForReservation(ticketsReservationId, ticket.getUserLanguage(), ticket.getEventId());
        boolean additionalServicesEmpty = additionalServices.isEmpty();
        if(!additionalServicesEmpty) {
            List<Integer> additionalServiceIds = additionalServices.stream().map(BookedAdditionalService::additionalServiceId).collect(Collectors.toList());
            Map<Integer, List<TicketFieldValueForAdditionalService>> fields = ticketFieldRepository.loadTicketFieldsForAdditionalService(ticket.getId(), additionalServiceIds)
                .stream().collect(Collectors.groupingBy(TicketFieldValueForAdditionalService::getAdditionalServiceId));

            return additionalServices.stream()
                .map(as -> new AdditionalServiceInfo(as.additionalServiceName(), as.count(), fields.get(as.additionalServiceId())))
                .collect(Collectors.toList());
        }
        return List.of();
    }

    public CheckInStatistics getStatistics(String eventName, String username) {
        return eventRepository.findOptionalByShortName(eventName)
            .filter(this::areStatsEnabled)
            .filter(EventManager.checkOwnership(username, organizationRepository))
            .map(event -> eventRepository.retrieveCheckInStatisticsForEvent(event.getId()))
            .orElse(null);
    }

    public List<CheckInLogEntry> retrieveLogEntries(String eventName, String username) {
        return eventRepository.findOptionalEventAndOrganizationIdByShortName(eventName)
            .filter(EventManager.checkOwnership(username, organizationRepository))
            .map(event -> scanAuditRepository.loadEntries(event.getId()))
            .orElse(List.of());
    }

    private boolean areStatsEnabled(EventAndOrganizationId event) {
        return configurationManager.getFor(CHECK_IN_STATS, event.getConfigurationLevel()).getValueAsBooleanOrDefault();
    }

}
