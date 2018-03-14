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
import alfio.model.Ticket;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.Json;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ryantenney.passkit4j.Pass;
import com.ryantenney.passkit4j.PassResource;
import com.ryantenney.passkit4j.PassSerializer;
import com.ryantenney.passkit4j.model.*;
import com.ryantenney.passkit4j.model.Color;
import com.ryantenney.passkit4j.model.TextField;
import com.ryantenney.passkit4j.sign.PassSigner;
import com.ryantenney.passkit4j.sign.PassSignerImpl;
import com.ryantenney.passkit4j.sign.PassSigningException;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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


    byte[] getPassBook(Map<String, String> model) {
        try {
            Ticket ticket = Json.fromJson(model.get("ticket"), Ticket.class);
            int eventId = ticket.getEventId();
            Event event = eventRepository.findById(eventId);
            Organization organization = organizationRepository.getById(Integer.valueOf(model.get("organizationId"), 10));
            int organizationId = organization.getId();

            Function<ConfigurationKeys, Configuration.ConfigurationPathKey> partial = Configuration.from(organizationId, eventId);
            Map<ConfigurationKeys, Optional<String>> pbookConf = configurationManager.getStringConfigValueFrom(
                partial.apply(ConfigurationKeys.PASSBOOK_TYPE_IDENTIFIER),
                partial.apply(ConfigurationKeys.PASSBOOK_KEYSTORE),
                partial.apply(ConfigurationKeys.PASSBOOK_KEYSTORE_PASSWORD),
                partial.apply(ConfigurationKeys.PASSBOOK_TEAM_IDENTIFIER));
            //check if all are set
            if(pbookConf.values().stream().anyMatch(o -> !o.isPresent())) {
                log.trace("Cannot generate Passbook. Missing configuration keys, check if all 4 are presents");
                return null;
            }

            //
            String teamIdentifier = pbookConf.get(ConfigurationKeys.PASSBOOK_TEAM_IDENTIFIER).orElseThrow(IllegalStateException::new);
            String typeIdentifier = pbookConf.get(ConfigurationKeys.PASSBOOK_TYPE_IDENTIFIER).orElseThrow(IllegalStateException::new);
            byte[] keystoreRaw = Base64.getDecoder().decode(pbookConf.get(ConfigurationKeys.PASSBOOK_KEYSTORE).orElseThrow(IllegalStateException::new));
            String keystorePwd = pbookConf.get(ConfigurationKeys.PASSBOOK_KEYSTORE_PASSWORD).orElseThrow(IllegalStateException::new);

            //ugly, find an alternative way?
            Optional<KeyStore> ksJks = loadKeyStore(keystoreRaw);
            if(ksJks.isPresent()) {
                return buildPass(ticket, event, organization, teamIdentifier, typeIdentifier, keystorePwd, ksJks.get());
            } else {
                log.warn("Cannot generate Passbook. Not able to load keystore. Please check configuration.");
                return null;
            }
        } catch (Exception ex) {
            log.warn("Got Exception while generating Passbook. Please check configuration.", ex);
            return null;
        }
    }

    private byte[] buildPass(Ticket ticket, Event event, Organization organization, String teamIdentifier, String typeIdentifier, String keystorePwd, KeyStore keyStore) throws IOException, PassSigningException {

        // from example: https://github.com/ryantenney/passkit4j/blob/master/src/test/java/com/ryantenney/passkit4j/EventTicketExample.java

        Location loc = new Location(Double.parseDouble(event.getLatitude()), Double.parseDouble(event.getLongitude())).altitude(0.0);

        Pass pass = new Pass()
            .teamIdentifier(teamIdentifier)
            .passTypeIdentifier(typeIdentifier)
            .organizationName(organization.getName())
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
                    .primaryFields(new TextField("event", "EVENT", event.getDisplayName()))
                    .secondaryFields(new TextField("loc", "LOCATION", event.getLocation()))
            );

        List<PassResource> passResources = new ArrayList<>(4);
        try(InputStream icon = new ClassPathResource("/alfio/icon/icon.png").getInputStream();
            InputStream icon2 = new ClassPathResource("/alfio/icon/icon@2x.png").getInputStream()) {
            passResources.add(new PassResource("icon.png", StreamUtils.copyToByteArray(icon)));
            passResources.add(new PassResource("icon@2x.png", StreamUtils.copyToByteArray(icon2)));
        } catch (IOException e) {
            //
        }


        fileUploadManager.findMetadata(event.getFileBlobId()).ifPresent(metadata -> {
            if(metadata.getContentType().equals("image/png") || metadata.getContentType().equals("image/jpeg")) {
                Optional<byte[]> cachedLogo = passbookLogoCache.get(event.getFileBlobId(), (id) -> {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    fileUploadManager.outputFile(event.getFileBlobId(), baos);
                    return readAndConvertImage(baos);
                });
                if(cachedLogo != null && cachedLogo.isPresent()) {
                    byte[] logo = cachedLogo.get();
                    passResources.add(new PassResource("logo.png", logo));
                    passResources.add(new PassResource("logo@2x.png", logo));
                }
            }
        });

        pass.files(passResources.toArray(new PassResource[passResources.size()]));

        try(ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream appleCert = new ClassPathResource("/alfio/certificates/AppleWWDRCA.cer").getInputStream()) {
            PassSigner signer = PassSignerImpl.builder()
                .keystore(keyStore, keystorePwd)
                .intermediateCertificate(appleCert)
                .build();
            PassSerializer.writePkPassArchive(pass, signer, baos);
            return baos.toByteArray();
        }
    }

    //"jks"
    // -> "pkcs12" don't work ;(
    private static Optional<KeyStore> loadKeyStore(byte[] k) {
        try {
            KeyStore ks = KeyStore.getInstance("jks");
            ks.load(new ByteArrayInputStream(k), null);
            return Optional.of(ks);
        } catch (GeneralSecurityException | IOException e) {
            return Optional.empty();
        }
    }

    private static Optional<byte[]> readAndConvertImage(ByteArrayOutputStream baos) {
        try {
            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
            boolean isWider = sourceImage.getWidth() > sourceImage.getHeight();
            // as defined in https://developer.apple.com/library/content/documentation/UserExperience/Conceptual/PassKit_PG/Creating.html#//apple_ref/doc/uid/TP40012195-CH4-SW8
            // logo max 160*50
            int finalWidth = isWider ? 160 : -1;
            int finalHeight = !isWider ? 50 : -1;
            Image thumb = sourceImage.getScaledInstance(finalWidth, finalHeight, Image.SCALE_SMOOTH);
            BufferedImage bufferedThumbnail = new BufferedImage(thumb.getWidth(null), thumb.getHeight(null), BufferedImage.TYPE_INT_RGB);
            bufferedThumbnail.getGraphics().drawImage(thumb, 0, 0, null);
            ByteArrayOutputStream logoPng = new ByteArrayOutputStream();
            ImageIO.write(bufferedThumbnail, "png", logoPng);
            return Optional.of(logoPng.toByteArray());
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
