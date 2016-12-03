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

import alfio.manager.support.CheckInStatus;
import alfio.manager.support.DefaultCheckInResult;
import alfio.manager.support.OnSitePaymentResult;
import alfio.manager.support.TicketAndCheckInResult;
import alfio.model.Event;
import alfio.model.FullTicketInfo;
import alfio.model.Ticket;
import alfio.model.Ticket.TicketStatus;
import alfio.model.TicketReservation;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.EventRepository;
import alfio.repository.TicketFieldRepository;
import alfio.repository.TicketRepository;
import alfio.repository.TicketReservationRepository;
import alfio.util.Json;
import alfio.util.MonetaryUtil;
import lombok.extern.log4j.Log4j2;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static alfio.manager.support.CheckInStatus.*;
import static alfio.util.OptionalWrapper.optionally;

@Component
@Transactional
@Log4j2
public class CheckInManager {

    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final TicketFieldRepository ticketFieldRepository;

    @Autowired
    public CheckInManager(TicketRepository ticketRepository,
                          EventRepository eventRepository,
                          TicketReservationRepository ticketReservationRepository,
                          TicketFieldRepository ticketFieldRepository) {
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
        this.ticketReservationRepository = ticketReservationRepository;
        this.ticketFieldRepository = ticketFieldRepository;
    }


    private void checkIn(String uuid) {
        Validate.isTrue(ticketRepository.findByUUID(uuid).getStatus() == TicketStatus.ACQUIRED);
        ticketRepository.updateTicketStatusWithUUID(uuid, TicketStatus.CHECKED_IN.toString());
    }

    private void acquire(String uuid) {
        Validate.isTrue(ticketRepository.findByUUID(uuid).getStatus() == TicketStatus.TO_BE_PAID);
        ticketRepository.updateTicketStatusWithUUID(uuid, TicketStatus.ACQUIRED.toString());
    }

    public TicketAndCheckInResult confirmOnSitePayment(String eventName, String ticketIdentifier, Optional<String> ticketCode) {
        return eventRepository.findOptionalByShortName(eventName)
            .flatMap(e -> confirmOnSitePayment(ticketIdentifier).map((String s) -> Pair.of(s, e)))
            .map(p -> checkIn(p.getRight().getId(), ticketIdentifier, ticketCode))
            .orElseGet(() -> new TicketAndCheckInResult(null, new DefaultCheckInResult(CheckInStatus.TICKET_NOT_FOUND, "")));
    }

    public Optional<String> confirmOnSitePayment(String ticketIdentifier) {
        Optional<String> uuid = findAndLockTicket(ticketIdentifier)
            .filter(t -> t.getStatus() == TicketStatus.TO_BE_PAID)
            .map(Ticket::getUuid);

        uuid.ifPresent(this::acquire);
        return uuid;
    }

    public TicketAndCheckInResult checkIn(String shortName, String ticketIdentifier, Optional<String> ticketCode) {
        return eventRepository.findOptionalByShortName(shortName).map(e -> checkIn(e.getId(), ticketIdentifier, ticketCode)).orElseGet(() -> new TicketAndCheckInResult(null, new DefaultCheckInResult(CheckInStatus.EVENT_NOT_FOUND, "event not found")));
    }

    public TicketAndCheckInResult checkIn(int eventId, String ticketIdentifier, Optional<String> ticketCode) {
        TicketAndCheckInResult descriptor = extractStatus(eventId, ticketRepository.findByUUIDForUpdate(ticketIdentifier), ticketIdentifier, ticketCode);
        if(descriptor.getResult().getStatus() == OK_READY_TO_BE_CHECKED_IN) {
            checkIn(ticketIdentifier);
            return new TicketAndCheckInResult(descriptor.getTicket(), new DefaultCheckInResult(SUCCESS, "success"));
        }
        return descriptor;
    }

    public boolean manualCheckIn(String ticketIdentifier) {
        Optional<Ticket> ticket = findAndLockTicket(ticketIdentifier);
        return ticket.map((t) -> {

            if(t.getStatus() == TicketStatus.TO_BE_PAID) {
                acquire(ticketIdentifier);
            }

            checkIn(ticketIdentifier);
            return true;
        }).orElse(false);
    }

    public boolean revertCheckIn(String ticketIdentifier) {
        return findAndLockTicket(ticketIdentifier).map((t) -> {
            if(t.getStatus() == TicketStatus.CHECKED_IN) {
                TicketReservation reservation = ticketReservationRepository.findReservationById(t.getTicketsReservationId());
                TicketStatus revertedStatus = reservation.getPaymentMethod() == PaymentProxy.ON_SITE ? TicketStatus.TO_BE_PAID : TicketStatus.ACQUIRED;
                ticketRepository.updateTicketStatusWithUUID(ticketIdentifier, revertedStatus.toString());
                return true;
            }
            return false;
        }).orElse(false);
    }

    private Optional<Ticket> findAndLockTicket(String uuid) {
        return ticketRepository.findByUUIDForUpdate(uuid);
    }

    public List<FullTicketInfo> findAllFullTicketInfo(int eventId) {
        return ticketRepository.findAllFullTicketInfoAssignedByEventId(eventId);
    }

    public TicketAndCheckInResult evaluateTicketStatus(int eventId, String ticketIdentifier, Optional<String> ticketCode) {
        return extractStatus(optionally(() -> eventRepository.findById(eventId)), optionally(() -> ticketRepository.findByUUID(ticketIdentifier)), ticketIdentifier, ticketCode);
    }

    public TicketAndCheckInResult evaluateTicketStatus(String eventName, String ticketIdentifier, Optional<String> ticketCode) {
        return extractStatus(eventRepository.findOptionalByShortName(eventName), optionally(() -> ticketRepository.findByUUID(ticketIdentifier)), ticketIdentifier, ticketCode);
    }

    private TicketAndCheckInResult extractStatus(int eventId, Optional<Ticket> maybeTicket, String ticketIdentifier, Optional<String> ticketCode) {
        return extractStatus(optionally(() -> eventRepository.findById(eventId)), maybeTicket, ticketIdentifier, ticketCode);
    }

    private TicketAndCheckInResult extractStatus(Optional<Event> maybeEvent, Optional<Ticket> maybeTicket, String ticketIdentifier, Optional<String> ticketCode) {

        if (!maybeEvent.isPresent()) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(EVENT_NOT_FOUND, "Event not found"));
        }

        if (!maybeTicket.isPresent()) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(TICKET_NOT_FOUND, "Ticket with uuid " + ticketIdentifier + " not found"));
        }

        if(!ticketCode.filter(StringUtils::isNotEmpty).isPresent()) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(EMPTY_TICKET_CODE, "Missing ticket code"));
        }

        Ticket ticket = maybeTicket.get();
        Event event = maybeEvent.get();
        String code = ticketCode.get();

        log.trace("scanned code is {}", code);
        log.trace("true code    is {}", ticket.ticketCode(event.getPrivateKey()));

        if (!code.equals(ticket.ticketCode(event.getPrivateKey()))) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(INVALID_TICKET_CODE, "Ticket qr code does not match"));
        }

        final TicketStatus ticketStatus = ticket.getStatus();

        if (ticketStatus == TicketStatus.TO_BE_PAID) {
            return new TicketAndCheckInResult(ticket, new OnSitePaymentResult(MUST_PAY, "Must pay for ticket", MonetaryUtil.centsToUnit(ticket.getFinalPriceCts()), event.getCurrency()));
        }

        if (ticketStatus == TicketStatus.CHECKED_IN) {
            return new TicketAndCheckInResult(ticket, new DefaultCheckInResult(ALREADY_CHECK_IN, "Error: already checked in"));
        }

        if (ticket.getStatus() != TicketStatus.ACQUIRED) {
            return new TicketAndCheckInResult(ticket, new DefaultCheckInResult(INVALID_TICKET_STATE, "Invalid ticket state, expected ACQUIRED state, received " + ticket.getStatus()));
        }

        return new TicketAndCheckInResult(ticket, new DefaultCheckInResult(OK_READY_TO_BE_CHECKED_IN, "Ready to be checked in"));
    }

    private static Pair<Cipher, SecretKeySpec>  getCypher(String key) {
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

    static String encrypt(String key, String payload)  {
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

    static String decrypt(String key, String payload) {
        try {
            Pair<Cipher, SecretKeySpec> cipherAndSecret = getCypher(key);
            Cipher cipher = cipherAndSecret.getKey();
            String[] splitted = payload.split(Pattern.quote("|"));
            byte[] iv = Base64.decodeBase64(splitted[0]);
            byte[] body = Base64.decodeBase64(splitted[1]);
            cipher.init(Cipher.DECRYPT_MODE, cipherAndSecret.getRight(), new IvParameterSpec(iv));
            byte[] decrypted = cipher.doFinal(body);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String,String> getEncryptedAttendeesInformations(int eventId, Set<String> additionalFields) {
        String eventKey = eventRepository.findById(eventId).getPrivateKey();
        Function<FullTicketInfo, String> hashedHMAC = ticket -> DigestUtils.sha256Hex(ticket.hmacTicketInfo(eventKey));

        Function<FullTicketInfo, String> encryptedBody = ticket -> {
            Map<String, String> info = new HashMap<>();
            info.put("firstName", ticket.getFirstName());
            info.put("lastName", ticket.getLastName());
            info.put("fullName", ticket.getFullName());
            info.put("email", ticket.getEmail());
            info.put("status", ticket.getStatus().toString());
            info.put("uuid", ticket.getUuid());
            if(!additionalFields.isEmpty()) {
                ticketFieldRepository.findValueForTicketId(ticket.getId(), additionalFields)
                    .stream()
                    .forEach(field -> info.put(field.getName(), field.getValue()));
            }

            String key = ticket.ticketCode(eventKey);
            String payload = Json.toJson(info);
            String encrypted = encrypt(key, Json.toJson(info));
            //String decrypted = decrypt(key, encrypted);

            return encrypted;
        };
        return ticketRepository.findAllFullTicketInfoAssignedByEventId(eventId)
            .stream()
            .collect(Collectors.toMap(hashedHMAC, encryptedBody));
    }
}
