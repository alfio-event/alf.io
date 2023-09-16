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
package alfio.manager.wallet;

import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.metadata.TicketMetadata;
import alfio.model.metadata.TicketMetadataContainer;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.command.InvalidateAccess;
import alfio.repository.*;
import alfio.util.HttpUtils;
import alfio.util.MustacheCustomTag;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.config.Initializer.*;
import static alfio.model.system.ConfigurationKeys.*;

@Component
@AllArgsConstructor
@Transactional
public class GoogleWalletManager {

    private static final Logger log = LoggerFactory.getLogger(GoogleWalletManager.class);
    private static final String WALLET_OBJECT_ID = "gWalletObjectId";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PLACEHOLDER = "Bearer %s";
    private final EventRepository eventRepository;
    private final ConfigurationManager configurationManager;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final AuditingRepository auditingRepository;

    public Optional<Pair<EventAndOrganizationId, Ticket>> validateTicket(String eventName, String ticketUuid) {
        var eventOptional = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventName);
        if (eventOptional.isEmpty()) {
            log.trace("event {} not found", eventName);
            return Optional.empty();
        }

        var event = eventOptional.get();
        return ticketRepository.findOptionalByUUID(ticketUuid)
            .filter(t -> t.getEventId() == event.getId())
            .map(t -> Pair.of(event, t));
    }

    public String createAddToWalletUrl(Ticket ticket, EventAndOrganizationId event) {
        Map<ConfigurationKeys, String> passConf = getConfigurationKeys(event);
        if (!passConf.isEmpty()) {
            return buildWalletPassUrl(ticket, eventRepository.findById(event.getId()), passConf);
        } else {
            throw new GoogleWalletException("Google Wallet integration is not enabled.");
        }
    }

    @EventListener
    public void invalidateAccessForTicket(InvalidateAccess invalidateAccess) {
        try {
            Map<ConfigurationKeys, String> passConf = getConfigurationKeys(invalidateAccess.getEvent());
            if (!passConf.isEmpty()) {
                invalidateAccess.getTicketMetadataContainer()
                    .getMetadataForKey(TicketMetadataContainer.GENERAL)
                    .map(m -> m.getAttributes().get(WALLET_OBJECT_ID))
                    .ifPresent(s -> invalidateObject(invalidateAccess.getTicket().getUuid(), s, passConf));
            }
        } catch (Exception e) {
            log.warn("Error while invalidating access for ticket " + invalidateAccess.getTicket().getUuid(), e);
        }
    }

    private void invalidateObject(String ticketId, String objectId, Map<ConfigurationKeys, String> passConf) {
        try {
            log.trace("Invalidating access to object ID: {}", objectId);
            var credentials = retrieveCredentials(passConf.get(WALLET_SERVICE_ACCOUNT_KEY));
            URI uriWithId = URI.create(String.format("%s/%s", EventTicketObject.WALLET_URL, objectId));
            HttpRequest expireRequest = HttpRequest.newBuilder()
                .uri(uriWithId)
                .header(AUTHORIZATION, String.format(BEARER_PLACEHOLDER, credentials.refreshAccessToken().getTokenValue()))
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"state\":\"INACTIVE\"}"))
                .build();
            var response = httpClient.send(expireRequest, HttpResponse.BodyHandlers.ofString());
            if (HttpUtils.callSuccessful(response)) {
                log.debug("Access invalidated for ticket {}", ticketId);
            } else {
                logIfWarnEnabled(() -> log.warn("Cannot invalidate access for ticket {}, response: {}", ticketId, response.body()));
            }
        } catch (IOException e) {
            throw new GoogleWalletException(e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleWalletException(e.getMessage(), e);
        }
    }

    private Map<ConfigurationKeys, String> getConfigurationKeys(EventAndOrganizationId event) {
        var conf = configurationManager.getFor(Set.of(
                ENABLE_WALLET,
                WALLET_ISSUER_IDENTIFIER,
                WALLET_SERVICE_ACCOUNT_KEY,
                WALLET_OVERWRITE_PREVIOUS_CLASSES_AND_EVENTS,
                BASE_URL),
            event.getConfigurationLevel());

        if (!conf.get(ENABLE_WALLET).getValueAsBooleanOrDefault()) {
            return Map.of();
        }
        var configValues = Map.of(
            WALLET_ISSUER_IDENTIFIER, conf.get(WALLET_ISSUER_IDENTIFIER).getValue(),
            WALLET_SERVICE_ACCOUNT_KEY, conf.get(WALLET_SERVICE_ACCOUNT_KEY).getValue(),
            WALLET_OVERWRITE_PREVIOUS_CLASSES_AND_EVENTS, conf.get(WALLET_OVERWRITE_PREVIOUS_CLASSES_AND_EVENTS).getValue(),
            BASE_URL, conf.get(BASE_URL).getValue());

        if (configValues.values().stream().anyMatch(Optional::isEmpty)) {
            return Map.of();
        }

        return configValues
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().orElseThrow()));
    }

    private String buildWalletPassUrl(Ticket ticket,
                                      Event event,
                                      Map<ConfigurationKeys, String> config) {
        String baseUrl = config.get(BASE_URL);
        String issuerId = config.get(WALLET_ISSUER_IDENTIFIER);
        String serviceAccountKey = config.get(WALLET_SERVICE_ACCOUNT_KEY);
        boolean overwritePreviousClassesAndEvents = Boolean.parseBoolean(config.get(WALLET_OVERWRITE_PREVIOUS_CLASSES_AND_EVENTS));

        String eventDescription = MustacheCustomTag.renderToTextCommonmark(
            eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(event.getId(), EventDescription.EventDescriptionType.DESCRIPTION, ticket.getUserLanguage()).orElse(""));
        TicketCategory category = ticketCategoryRepository.getById(ticket.getCategoryId());
        var ticketValidityStart = Optional.ofNullable(category.getTicketValidityStart(event.getZoneId())).orElse(event.getBegin());
        var ticketValidityEnd = Optional.ofNullable(category.getTicketValidityEnd(event.getZoneId())).orElse(event.getEnd());

        EventTicketClass.LatitudeLongitudePoint latitudeLongitudePoint = null;
        if (event.getLatitude() != null && event.getLongitude() != null) {
            latitudeLongitudePoint = EventTicketClass.LatitudeLongitudePoint.of(Double.parseDouble(event.getLatitude()), Double.parseDouble(event.getLongitude()));
        }

        var host = URI.create(baseUrl).getHost().replace(".", "-");
        var eventTicketClass = EventTicketClass.builder()
            .id(formatEventTicketClassId(event, issuerId, category, host))
            .eventOrGroupingId(Integer.toString(event.getId()))
            .logoUri(baseUrl + "/file/" + event.getFileBlobId())
            .eventName(event.getDisplayName())
            .description(eventDescription)
            .venue(event.getLocation())
            .location(latitudeLongitudePoint)
            .ticketType(category.getName())
            .start(ticketValidityStart)
            .end(ticketValidityEnd)
            .build();

        String walletTicketId = formatEventTicketObjectId(ticket, event, issuerId, host);
        var eventTicketObject = EventTicketObject.builder()
            .id(walletTicketId)
            .classId(eventTicketClass.getId())
            .ticketHolderName(ticket.getFullName())
            .ticketNumber(ticket.getUuid())
            .barcode(ticket.ticketCode(event.getPrivateKey(), event.supportsQRCodeCaseInsensitive()))
            .build();

        GoogleCredentials credentials = retrieveCredentials(serviceAccountKey);

        createEventClass(credentials, eventTicketClass, overwritePreviousClassesAndEvents);
        String eventObjectId = createEventObject(credentials, eventTicketObject, overwritePreviousClassesAndEvents);
        String walletPassUrl = generateWalletPassUrl(credentials, eventObjectId, baseUrl);
        persistPassId(ticket, eventObjectId);
        return walletPassUrl;
    }

    private static GoogleCredentials retrieveCredentials(String serviceAccountKey) {
        try {
            return GoogleCredentials
                .fromStream(new ByteArrayInputStream(serviceAccountKey.getBytes(StandardCharsets.UTF_8)))
                .createScoped(Collections.singleton("https://www.googleapis.com/auth/wallet_object.issuer"));
        } catch (IOException e) {
            throw new GoogleWalletException("Unable to retrieve Service Account Credentials from configuration", e);
        }
    }

    private void persistPassId(Ticket ticket, String eventObjectId) {
        var metadataContainer = Objects.requireNonNullElseGet(ticketRepository.getTicketMetadata(ticket.getId()), TicketMetadataContainer::empty);
        var existingMetadata = metadataContainer.getMetadataForKey(TicketMetadataContainer.GENERAL);
        var attributesMap = existingMetadata.map(ticketMetadata -> new HashMap<>(ticketMetadata.getAttributes()))
            .orElseGet(HashMap::new);
        attributesMap.put(WALLET_OBJECT_ID, eventObjectId);
        metadataContainer.putMetadata(TicketMetadataContainer.GENERAL, existingMetadata.map(tm -> tm.withAttributes(attributesMap)).orElseGet(() -> new TicketMetadata(null, null, attributesMap)));
        ticketRepository.updateTicketMetadata(ticket.getId(), metadataContainer);
    }

    private String formatEventTicketObjectId(Ticket ticket, Event event, String issuerId, String host) {
        return String.format("%s.%s-%s-object.%s-%s",
            issuerId,
            walletIdPrefix(),
            host,
            event.getShortName().replaceAll("[^\\w.-]", "_"),
            // if the attendee gives their ticket to somebody else, the new ticket holder must be able to add their ticket to the wallet
            // therefore we add count(audit(UPDATE_TICKET)) as suffix for the ticket UUID
            ticket.getUuid()+ "_" + auditingRepository.countAuditsOfTypeForTicket(ticket.getTicketsReservationId(), ticket.getId(), Audit.EventType.TICKET_HOLDER_CHANGED));
    }

    private String formatEventTicketClassId(Event event, String issuerId, TicketCategory category, String host) {
        return String.format("%s.%s-%s-class.%s-%d", issuerId, walletIdPrefix(), host, event.getShortName().replaceAll("[^\\w.-]", "_"), category.getId());
    }

    private String generateWalletPassUrl(GoogleCredentials credentials, String eventObjectId, String url) {
        var objectIdMap = Map.of("id", eventObjectId);
        var payload = Map.of("genericObjects", List.of(objectIdMap));
        var claims = Map.of(
            "iss", ((ServiceAccountCredentials) credentials).getClientEmail(),
            "aud", "google",
            "origins", List.of(url),
            "typ", "savetowallet",
            "payload", payload
        );

        Algorithm algorithm = Algorithm.RSA256(
            null,
            (RSAPrivateKey) ((ServiceAccountCredentials) credentials).getPrivateKey());
        String token = JWT.create()
            .withPayload(claims)
            .sign(algorithm);
        return String.format("https://pay.google.com/gp/v/save/%s", token);
    }

    private String createEventClass(GoogleCredentials credentials, EventTicketClass eventTicketClass, boolean overwritePreviousClassesAndEvents) {
        return createOnWallet(EventTicketClass.WALLET_URL, credentials, eventTicketClass, overwritePreviousClassesAndEvents);
    }

    private String createEventObject(GoogleCredentials credentials, EventTicketObject eventTicketObject, boolean overwritePreviousClassesAndEvents) {
        return createOnWallet(EventTicketObject.WALLET_URL, credentials, eventTicketObject, overwritePreviousClassesAndEvents);
    }

    private String createOnWallet(String uri, GoogleCredentials credentials, WalletEntity entity, boolean overwritePreviousClassesAndEvents) {
        try {
            URI uriWithId = URI.create(String.format("%s/%s", uri, entity.getId()));
            HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(uriWithId)
                .header(AUTHORIZATION, String.format(BEARER_PLACEHOLDER, credentials.refreshAccessToken().getTokenValue()))
                .GET()
                .build();
            log.debug("GET Request: {}", getRequest);

            HttpResponse<String> getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
            log.debug("GET Response: {}", getResponse);
            if (getResponse.statusCode() != 200 && getResponse.statusCode() != 404) {
                logIfWarnEnabled(() -> log.warn("Received {} status code when creating entity but 200 or 404 were expected: {}", getResponse.statusCode(), getResponse.body()));
                throw new GoogleWalletException("Cannot create Wallet class. Response status: " + getResponse.statusCode());
            }

            if (getResponse.statusCode() == 404 || overwritePreviousClassesAndEvents) {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .header(AUTHORIZATION, String.format(BEARER_PLACEHOLDER, credentials.refreshAccessToken().getTokenValue()));
                if (getResponse.statusCode() == 404) {
                    builder = builder
                        .uri(URI.create(uri))
                        .POST(HttpRequest.BodyPublishers.ofString(entity.build(objectMapper)));
                } else {
                    builder = builder
                        .uri(uriWithId)
                        .PUT(HttpRequest.BodyPublishers.ofString(entity.build(objectMapper)));
                }
                HttpRequest postOrPutRequest = builder.build();
                log.debug("POST or PUT Request: {}", postOrPutRequest);
                HttpResponse<String> postOrPutResponse = httpClient.send(postOrPutRequest, HttpResponse.BodyHandlers.ofString());
                log.debug("POST or PUT Response: {}", postOrPutResponse);
                if (postOrPutResponse.statusCode() != 200) {
                    logIfWarnEnabled(() -> log.warn("Received {} status code when creating entity: {}", postOrPutResponse.statusCode(), postOrPutResponse.body()));
                    throw new GoogleWalletException("Cannot create wallet. Response status: " + postOrPutResponse.statusCode());
                }
            }
            return entity.getId();
        } catch (IOException e) {
            throw new GoogleWalletException("Error while communicating with the Google Wallet API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleWalletException("Error while communicating with the Google Wallet API", e);
        }
    }

    private String walletIdPrefix() {
        var profile = Stream.of(PROFILE_DEMO, PROFILE_DEV, PROFILE_LIVE)
            .filter(p -> environment.acceptsProfiles(Profiles.of(p)))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("No suitable Spring Profile found to create a Wallet ID prefix for classes and objects. Must have one of PROFILE_DEMO, PROFILE_DEV, or PROFILE_LIVE"));
        if (profile.equals(PROFILE_LIVE)) {
            return "live";
        }
        return profile;
    }

    private void logIfWarnEnabled(ConditionalLogger logger) {
        if (log.isWarnEnabled()) {
            logger.log();
        }
    }

    @FunctionalInterface
    private interface ConditionalLogger {
        void log();
    }

}
