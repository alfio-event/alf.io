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
import alfio.model.Event;
import alfio.model.EventDescription;
import alfio.model.Ticket;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.EventDescriptionRepository;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.Json;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ryantenney.passkit4j.Pass;
import com.ryantenney.passkit4j.PassResource;
import com.ryantenney.passkit4j.PassSerializer;
import com.ryantenney.passkit4j.model.Color;
import com.ryantenney.passkit4j.model.TextField;
import com.ryantenney.passkit4j.model.*;
import com.ryantenney.passkit4j.sign.PassSigner;
import com.ryantenney.passkit4j.sign.PassSignerImpl;
import com.ryantenney.passkit4j.sign.PassSigningException;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.imgscalr.Scalr;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static alfio.model.system.ConfigurationKeys.*;

@Component
@AllArgsConstructor
@Log4j2
class PassBookManager {

    private final Cache<String, Optional<byte[]>> passbookLogoCache = Caffeine.newBuilder()
        .maximumSize(20)
        .expireAfterWrite(20, TimeUnit.MINUTES)
        .build();
    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final ConfigurationManager configurationManager;
    private final FileUploadManager fileUploadManager;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final TicketCategoryRepository ticketCategoryRepository;


    byte[] getPassBook(Map<String, String> model) {
        try {
            Ticket ticket = Json.fromJson(model.get("ticket"), Ticket.class);
            int eventId = ticket.getEventId();
            Event event = eventRepository.findById(eventId);
            Organization organization = organizationRepository.getById(Integer.valueOf(model.get("organizationId"), 10));

            Function<ConfigurationKeys, Configuration.ConfigurationPathKey> partial = Configuration.from(event);
            Map<ConfigurationKeys, Optional<String>> pbookConf = configurationManager.getStringConfigValueFrom(
                partial.apply(PASSBOOK_TYPE_IDENTIFIER),
                partial.apply(PASSBOOK_KEYSTORE),
                partial.apply(PASSBOOK_KEYSTORE_PASSWORD),
                partial.apply(PASSBOOK_TEAM_IDENTIFIER),
                partial.apply(PASSBOOK_PRIVATE_KEY_ALIAS));
            //check if all are set
            if(pbookConf.values().stream().anyMatch(Optional::isEmpty)) {
                log.trace("Cannot generate Passbook. Missing configuration keys, check if all 5 are presents");
                return null;
            }

            //
            String teamIdentifier = pbookConf.get(PASSBOOK_TEAM_IDENTIFIER).orElseThrow();
            String typeIdentifier = pbookConf.get(PASSBOOK_TYPE_IDENTIFIER).orElseThrow();
            byte[] keystoreRaw = Base64.getDecoder().decode(pbookConf.get(PASSBOOK_KEYSTORE).orElseThrow());
            String keystorePwd = pbookConf.get(PASSBOOK_KEYSTORE_PASSWORD).orElseThrow();
            String privateKeyAlias = pbookConf.get(PASSBOOK_PRIVATE_KEY_ALIAS).orElseThrow();

            return buildPass(ticket, event, organization, teamIdentifier, typeIdentifier, keystorePwd, privateKeyAlias, new ByteArrayInputStream(keystoreRaw));
        } catch (Exception ex) {
            log.warn("Got Exception while generating Passbook. Please check configuration.", ex);
            return null;
        }
    }

    private byte[] buildPass(Ticket ticket,
                             Event event,
                             Organization organization,
                             String teamIdentifier,
                             String typeIdentifier,
                             String keystorePwd,
                             String privateKeyAlias,
                             InputStream keyStore) throws IOException, PassSigningException {

        // from example: https://github.com/ryantenney/passkit4j/blob/master/src/test/java/com/ryantenney/passkit4j/EventTicketExample.java
        // specification: https://developer.apple.com/library/archive/documentation/UserExperience/Conceptual/PassKit_PG/Creating.html#//apple_ref/doc/uid/TP40012195-CH4-SW6

        var ticketLocale = Locale.forLanguageTag(ticket.getUserLanguage());

        Location loc = new Location(Double.parseDouble(event.getLatitude()), Double.parseDouble(event.getLongitude())).altitude(0D);
        String eventDescription = eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(event.getId(), EventDescription.EventDescriptionType.DESCRIPTION, ticket.getUserLanguage()).orElse("");
        Pass pass = new Pass()
            .teamIdentifier(teamIdentifier)
            .passTypeIdentifier(typeIdentifier)
            .organizationName(organization.getName())
            .groupingIdentifier(organization.getEmail())
            .description(event.getDisplayName())
            .serialNumber(ticket.getUuid())
            .relevantDate(Date.from(event.getBegin().toInstant()))
            .expirationDate(Date.from(event.getEnd().toInstant()))
            .locations(loc)
            .barcode(new Barcode(BarcodeFormat.QR, ticket.ticketCode(event.getPrivateKey())))
            .labelColor(Color.BLACK)
            .foregroundColor(Color.BLACK)
            .backgroundColor(Color.WHITE)
            .passInformation(
                new EventTicket()
                    .headerFields(List.of(
                        new TextField("eventStartDate", "Date", event.getBegin().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(ticketLocale)))
                    ))
                    .primaryFields(List.of(
                        new TextField("categoryId", event.getDisplayName(), ticketCategoryRepository.getById(ticket.getCategoryId()).getName())
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

        List<PassResource> passResources = new ArrayList<>(6);

        passResources.add(new PassResource("icon.png", () -> new ClassPathResource("/alfio/icon/icon.png").getInputStream()));
        passResources.add(new PassResource("icon@2x.png", () -> new ClassPathResource("/alfio/icon/icon@2x.png").getInputStream()));
        passResources.add(new PassResource("icon@3x.png", () -> new ClassPathResource("/alfio/icon/icon@3x.png").getInputStream()));

        fileUploadManager.findMetadata(event.getFileBlobId()).ifPresent(metadata -> {
            if(metadata.getContentType().equals("image/png") || metadata.getContentType().equals("image/jpeg")) {
                Optional<byte[]> cachedLogo = passbookLogoCache.get(event.getFileBlobId(), (id) -> {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    fileUploadManager.outputFile(event.getFileBlobId(), baos);
                    return readAndConvertImage(baos);
                });
                if(cachedLogo != null && cachedLogo.isPresent()) {
                    addLogoResources(cachedLogo.get(), passResources);
                }
            }
        });

        pass.files(passResources.toArray(new PassResource[0]));

        try(ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream appleCert = new ClassPathResource("/alfio/certificates/AppleWWDRCA.cer").getInputStream()) {
            PassSigner signer = PassSignerImpl.builder()
                .keystore(keyStore, keystorePwd)
                .alias(privateKeyAlias)
                .intermediateCertificate(appleCert)
                .build();
            PassSerializer.writePkPassArchive(pass, signer, baos);
            return baos.toByteArray();
        }
    }

    private void addLogoResources(byte[] logo, List<PassResource> passResources) {
        try {
            var srcImage = ImageIO.read(new ByteArrayInputStream(logo));
            passResources.add(new PassResource("logo.png", scaleLogo(srcImage, 1)));
            passResources.add(new PassResource("logo@2x.png", scaleLogo(srcImage, 2)));
            passResources.add(new PassResource("logo@3x.png", logo));
        } catch (IOException e) {
            log.warn("Error during image conversion", e);
        }
    }

    private List<Field<?>> getAuxiliaryFields(Ticket ticket) {
        //TODO add additional options here.
        return null;
    }

    private static Optional<byte[]> readAndConvertImage(ByteArrayOutputStream baos) {
        try {
            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
            return Optional.of(scaleLogo(sourceImage, 3));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static byte[] scaleLogo(BufferedImage sourceImage, int factor) throws IOException {
        // base image is 160 x 50 points.
        // On retina displays, a point can be two or three pixels, depending on the device model
        int finalWidth = 160 * factor;
        int finalHeight = 50 * factor;
        var thumbImg = Scalr.resize(sourceImage, Scalr.Method.QUALITY, Scalr.Mode.AUTOMATIC, finalWidth, finalHeight, Scalr.OP_ANTIALIAS);
        var outputStream = new ByteArrayOutputStream();
        ImageIO.write(thumbImg, "png", outputStream);
        return outputStream.toByteArray();
    }
}
