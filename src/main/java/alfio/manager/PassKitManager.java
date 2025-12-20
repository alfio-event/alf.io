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

import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.Mailer;
import alfio.model.*;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.EventDescriptionRepository;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.Json;
import alfio.util.LocaleUtil;
import alfio.util.MustacheCustomTag;
import com.ryantenney.passkit4j.Pass;
import com.ryantenney.passkit4j.PassResource;
import com.ryantenney.passkit4j.PassSerializer;
import com.ryantenney.passkit4j.model.*;
import com.ryantenney.passkit4j.sign.PassSigner;
import com.ryantenney.passkit4j.sign.PassSignerImpl;
import com.ryantenney.passkit4j.sign.PassSigningException;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.MiscUtils.removeTabsAndNewlines;

@Component
@AllArgsConstructor
public class PassKitManager {

    private static final Logger log = LoggerFactory.getLogger(PassKitManager.class);
    private static final String APPLE_PASS = "ApplePass";
    private static final String PASSKIT_SECTION = "passkit";
    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final ConfigurationManager configurationManager;
    private final FileUploadManager fileUploadManager;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final FileBlobCacheManager fileBlobCacheManager;


    public boolean writePass(Ticket ticket, EventAndOrganizationId event, OutputStream out) throws IOException, PassSigningException {
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        Map<ConfigurationKeys, String> passConf = getConfigurationKeys(event);
        if(!passConf.isEmpty()) {
            buildPass(ticket, eventRepository.findById(event.getId()), organization, passConf, out);
            return true;
        } else {
            log.trace("Cannot generate Pass. Missing configuration keys, check if all 5 are presents");
            return false;
        }
    }

    byte[] getPass(Map<String, String> model) {
        try {
            if (BooleanUtils.TRUE.equals(model.get(Mailer.SKIP_PASSBOOK))) {
                log.trace("HTML email enabled. Skipping passbook generation");
                return null;
            }
            Ticket ticket = Json.fromJson(model.get("ticket"), Ticket.class);
            int eventId = ticket.getEventId();
            Event event = eventRepository.findById(eventId);
            Organization organization = organizationRepository.getById(Integer.valueOf(model.get("organizationId"), 10));

            Map<ConfigurationKeys, String> passConf = getConfigurationKeys(event);
            //check if all are set
            if (passConf.isEmpty()) {
                log.trace("Cannot generate Passbook. Missing configuration keys, check if all 5 are presents");
                return null;
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()){
                buildPass(ticket, event, organization, passConf, out);
                return out.toByteArray();
            }
        } catch (Exception ex) {
            log.warn("Got Exception while generating Passbook. Please check configuration.", ex);
            return null;
        }
    }

    private Map<ConfigurationKeys, String> getConfigurationKeys(EventAndOrganizationId event) {

        var conf = configurationManager.getFor(Set.of(ENABLE_PASS,
            PASSBOOK_TYPE_IDENTIFIER, PASSBOOK_KEYSTORE, PASSBOOK_KEYSTORE_PASSWORD,
            PASSBOOK_TEAM_IDENTIFIER, PASSBOOK_PRIVATE_KEY_ALIAS), event.getConfigurationLevel());

        if(!conf.get(ENABLE_PASS).getValueAsBooleanOrDefault()) {
            return Map.of();
        }
        var configValues = Map.of(
            PASSBOOK_TYPE_IDENTIFIER, conf.get(PASSBOOK_TYPE_IDENTIFIER).getValue(),
            PASSBOOK_KEYSTORE, conf.get(PASSBOOK_KEYSTORE).getValue(),
            PASSBOOK_KEYSTORE_PASSWORD, conf.get(PASSBOOK_KEYSTORE_PASSWORD).getValue(),
            PASSBOOK_TEAM_IDENTIFIER, conf.get(PASSBOOK_TEAM_IDENTIFIER).getValue(),
            PASSBOOK_PRIVATE_KEY_ALIAS, conf.get(PASSBOOK_PRIVATE_KEY_ALIAS).getValue());

        if(configValues.values().stream().anyMatch(Optional::isEmpty)) {
            return Map.of();
        }

        return configValues
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().orElseThrow()));
    }

    private void buildPass(Ticket ticket,
                           Event event,
                           Organization organization,
                           Map<ConfigurationKeys, String> config,
                           OutputStream out) throws IOException, PassSigningException {

        // from example: https://github.com/ryantenney/passkit4j/blob/master/src/test/java/com/ryantenney/passkit4j/EventTicketExample.java
        // specification: https://developer.apple.com/library/archive/documentation/UserExperience/Conceptual/PassKit_PG/Creating.html#//apple_ref/doc/uid/TP40012195-CH4-SW6

        var ticketLocale = LocaleUtil.forLanguageTag(ticket.getUserLanguage());
        String teamIdentifier = config.get(PASSBOOK_TEAM_IDENTIFIER);
        String typeIdentifier = config.get(PASSBOOK_TYPE_IDENTIFIER);
        byte[] keystoreRaw = Base64.getDecoder().decode(config.get(PASSBOOK_KEYSTORE));
        String keystorePwd = config.get(PASSBOOK_KEYSTORE_PASSWORD);
        String privateKeyAlias = config.get(PASSBOOK_PRIVATE_KEY_ALIAS);


        String eventDescription = MustacheCustomTag.renderToTextCommonmark(
            eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(event.getId(), EventDescription.EventDescriptionType.DESCRIPTION, ticket.getUserLanguage()).orElse(""));
        TicketCategory category = ticketCategoryRepository.getById(ticket.getCategoryId());
        var ticketValidityStart = Optional.ofNullable(category.getTicketValidityStart(event.getZoneId())).orElse(event.getBegin());
        Pass pass = new Pass()
            .teamIdentifier(teamIdentifier)
            .passTypeIdentifier(typeIdentifier)
            .organizationName(organization.getName())
            .groupingIdentifier(organization.getEmail())
            .description(event.getDisplayName())
            .serialNumber(ticket.getUuid())
            //.authenticationToken(buildAuthenticationToken(ticket, event, event.getPrivateKey()))
            //.webServiceURL(StringUtils.removeEnd(configurationManager.getRequiredValue(Configuration.getSystemConfiguration(BASE_URL)), "/") + "/api/pass/event/" + event.getShortName() +"/")
            .relevantDate(Date.from(ticketValidityStart.toInstant()))
            .expirationDate(Date.from(Optional.ofNullable(category.getTicketValidityEnd(event.getZoneId())).orElse(event.getEnd()).toInstant()))

            .barcode(new Barcode(BarcodeFormat.QR, ticket.ticketCode(event.getPrivateKey(), event.supportsQRCodeCaseInsensitive())))
            .labelColor(Color.BLACK)
            .foregroundColor(Color.BLACK)
            .backgroundColor(Color.WHITE)
            .passInformation(
                new EventTicket()
                    .headerFields(List.of(
                        new TextField("eventStartDate", "Date", ticketValidityStart.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(ticketLocale)))
                    ))
                    .primaryFields(List.of(
                        new TextField("categoryId", event.getDisplayName(), category.getName())
                    ))
                    .secondaryFields(
                        new TextField("location", "Venue", event.getLocation())
                    )
                    .auxiliaryFields(
                        getAuxiliaryFields(ticket)
                    )
                    .backFields(
                        new TextField("desc", "Event Description", eventDescription),
                        new TextField("credits", "Powered by", "Alf.io, the Open Source ticket reservation System.")
                    )
            );

        if (event.getLatitude() != null && event.getLongitude() != null) {
            pass.locations(new Location(Double.parseDouble(event.getLatitude()), Double.parseDouble(event.getLongitude())).altitude(0D));
        }

        List<PassResource> passResources = new ArrayList<>(6);

        passResources.add(new PassResource("icon.png", () -> new ClassPathResource("/alfio/icon/icon.png").getInputStream()));
        passResources.add(new PassResource("icon@2x.png", () -> new ClassPathResource("/alfio/icon/icon@2x.png").getInputStream()));
        passResources.add(new PassResource("icon@3x.png", () -> new ClassPathResource("/alfio/icon/icon@3x.png").getInputStream()));

        fileUploadManager.findMetadata(event.getFileBlobId()).ifPresent(metadata -> {
            if (metadata.getContentType().equals("image/png") || metadata.getContentType().equals("image/jpeg")) {
                var fullSizeLogo = fileUploadManager.getFile(event.getFileBlobId());
                var id = event.getFileBlobId();
                try {
                    var logo1x = fileBlobCacheManager.getFile(PASSKIT_SECTION, id + "scale-1", () -> scaleLogo(fullSizeLogo, 1));
                    var logo2x = fileBlobCacheManager.getFile(PASSKIT_SECTION, id + "scale-2", () -> scaleLogo(fullSizeLogo, 2));
                    var logo3x = fileBlobCacheManager.getFile(PASSKIT_SECTION, id + "scale-3", () -> scaleLogo(fullSizeLogo, 3));
                    passResources.add(new PassResource("logo.png", logo1x));
                    passResources.add(new PassResource("logo@2x.png", logo2x));
                    passResources.add(new PassResource("logo@3x.png", logo3x));
                } catch (IllegalStateException e) { // handle various failures related to the file blob layer
                    log.warn("was not able to add the logo to the passkit for ticket {} in event {}", ticket.getUuid(), event.getShortName(), e);
                }
            }
        });

        pass.files(passResources.toArray(new PassResource[0]));
        try(InputStream appleCert = new ClassPathResource("/alfio/certificates/AppleWWDRCAG4.cer").getInputStream()) {
            PassSigner signer = PassSignerImpl.builder()
                .keystore(new ByteArrayInputStream(keystoreRaw), keystorePwd)
                .alias(privateKeyAlias)
                .intermediateCertificate(appleCert)
                .build();
            PassSerializer.writePkPassArchive(pass, signer, out);
        }
    }

    private String buildAuthenticationToken(Ticket ticket, EventAndOrganizationId event, String privateKey) {
        var code = event.getId() + "/" + ticket.getTicketsReservationId() + "/" + ticket.getUuid();
        return Ticket.hmacSHA256Base64(privateKey, code);
    }

    public Optional<Pair<EventAndOrganizationId, Ticket>> retrieveTicketDetails(String eventName, String ticketUuid) {
        return eventRepository.findOptionalEventAndOrganizationIdByShortName(eventName)
            .flatMap(e -> ticketRepository.findOptionalByPublicUUID(UUID.fromString(ticketUuid))
                .filter(t -> e.getId() == t.getEventId())
                .map(t -> Pair.of(e, t))
            );
    }

    public Optional<Pair<EventAndOrganizationId, Ticket>> validateToken(String eventName, String typeIdentifier, String ticketUuid, String authorizationHeader) {
        String token;
        if(authorizationHeader.startsWith(APPLE_PASS)) {
            // From the specs:
            // The Authorization header is supplied; its value is the word ApplePass, followed by a space,
            // followed by the pass’s authorization token as specified in the pass.
            token = authorizationHeader.substring(APPLE_PASS.length() + 1);
        } else {
            log.trace("Authorization Header does not start with ApplePass");
            return Optional.empty();
        }

        var eventOptional = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventName);
        if(eventOptional.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("event {} not found", removeTabsAndNewlines(eventName));
            }
            return Optional.empty();
        }

        var event = eventOptional.get();
        var typeIdentifierOptional = configurationManager.getFor(PASSBOOK_TYPE_IDENTIFIER, event.getConfigurationLevel());
        if(!typeIdentifierOptional.isPresent() || !typeIdentifier.equals(typeIdentifierOptional.getValueOrNull())) {
            if (log.isTraceEnabled()) {
                log.trace("typeIdentifier does not match. Expected {}, got {}", typeIdentifierOptional.getValueOrDefault("not-found"), removeTabsAndNewlines(typeIdentifier));
            }
            return Optional.empty();
        }
        return ticketRepository.findOptionalByUUID(ticketUuid)
            .filter(t -> t.getEventId() == event.getId())
            .filter(t -> buildAuthenticationToken(t, event, eventRepository.getPrivateKey(event.getId())).equals(token))
            .map(t -> Pair.of(event, t));
    }

    private List<Field<?>> getAuxiliaryFields(Ticket ticket) {
        //TODO add additional options here.
        return null;
    }

    private static File scaleLogo(File sourceImage, int factor) {
        try {
            // base image is 160 x 50 points.
            // On retina displays, a point can be two or three pixels, depending on the device model
            int finalWidth = 160 * factor;
            int finalHeight = 50 * factor;
            var thumbImg = Scalr.resize(ImageIO.read(sourceImage), Scalr.Method.ULTRA_QUALITY, Scalr.Mode.AUTOMATIC, finalWidth, finalHeight);
            var tmpFile = File.createTempFile("passkit", ""+factor);
            ImageIO.write(thumbImg, "png", tmpFile);
            return tmpFile;
        } catch (IOException e) {
            throw new IllegalStateException("was not able to scale logo from file " + sourceImage + " with factor " + factor, e);
        }
    }
}
