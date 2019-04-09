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
package alfio.controller.support;

import alfio.manager.ExtensionManager;
import alfio.manager.FileUploadManager;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.model.*;
import alfio.model.user.Organization;
import alfio.util.LocaleUtil;
import alfio.util.TemplateManager;
import alfio.util.TemplateResource;
import ch.digitalfondue.jfiveparse.Parser;
import ch.digitalfondue.jfiveparse.W3CDom;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.log4j.Log4j2;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.core.io.ClassPathResource;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
public final class TemplateProcessor {

    private static final Cache<String, File> FONT_CACHE = Caffeine.newBuilder()
        .removalListener((String key, File value, RemovalCause cause) -> {
            if(value != null) {
                value.delete();
            }
        })
        .build();

    private TemplateProcessor() {}


    public static PartialTicketTextGenerator buildPartialEmail(Event event,
                                                               Organization organization,
                                                               TicketReservation ticketReservation,
                                                               TicketCategory category,
                                                               TemplateManager templateManager,
                                                               String ticketURL,
                                                               HttpServletRequest request) {
        return (ticket) -> {
            Map<String, Object> model = TemplateResource.buildModelForTicketEmail(organization, event, ticketReservation, ticketURL, ticket, category);
            Locale language = LocaleUtil.getTicketLanguage(ticket, request);
            return templateManager.renderTemplate(event, TemplateResource.TICKET_EMAIL, model, language);
        };
    }

    public static PartialTicketTextGenerator buildEmailForOwnerChange(Event e,
                                                                      Ticket oldTicket,
                                                                      Organization organization,
                                                                      String ticketUrl,
                                                                      TemplateManager templateManager,
                                                                      Locale language) {
        return (newTicket) -> {
            Map<String, Object> emailModel = TemplateResource.buildModelForTicketHasChangedOwner(organization, e, oldTicket, newTicket, ticketUrl);
            return templateManager.renderTemplate(e, TemplateResource.TICKET_HAS_CHANGED_OWNER, emailModel, language);
        };
    }

    public static void renderPDFTicket(Locale language,
                                       Event event,
                                       TicketReservation ticketReservation,
                                       Ticket ticket,
                                       TicketCategory ticketCategory,
                                       Organization organization,
                                       TemplateManager templateManager,
                                       FileUploadManager fileUploadManager,
                                       String reservationID,
                                       OutputStream os,
                                       Function<Ticket, List<TicketFieldConfigurationDescriptionAndValue>> retrieveFieldValues,
                                       ExtensionManager extensionManager) throws IOException {
        Optional<TemplateResource.ImageData> imageData = extractImageModel(event, fileUploadManager);
        List<TicketFieldConfigurationDescriptionAndValue> fields = retrieveFieldValues.apply(ticket);
        Map<String, Object> model = TemplateResource.buildModelForTicketPDF(organization, event, ticketReservation, ticketCategory, ticket, imageData, reservationID,
            fields.stream().collect(Collectors.toMap(TicketFieldConfigurationDescriptionAndValue::getName, TicketFieldConfigurationDescriptionAndValue::getValueDescription)));

        String page = templateManager.renderTemplate(event, TemplateResource.TICKET_PDF, model, language);
        renderToPdf(page, os, extensionManager, event);
    }

    public static void renderToPdf(String page, OutputStream os, ExtensionManager extensionManager, Event event) throws IOException {

        if(extensionManager.handlePdfTransformation(page, event, os)) {
            return;
        }
        PdfRendererBuilder builder = new PdfRendererBuilder();
        PDDocument doc = new PDDocument(MemoryUsageSetting.setupTempFileOnly());
        builder.usePDDocument(doc);
        builder.toStream(os);
        builder.useProtocolsStreamImplementation(new AlfioInternalFSStreamFactory(), "alfio-internal");
        builder.useProtocolsStreamImplementation(new InvalidProtocolFSStreamFactory(), "http", "https", "file", "jar");
        builder.useFastMode();

        var parser = new Parser();

        builder.withW3cDocument(W3CDom.toW3CDocument(parser.parse(page)), "");
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            File defaultFont = FONT_CACHE.get(DEJA_VU_SANS, LOAD_DEJA_VU_SANS_FONT);
            if (!defaultFont.exists()) { // fallback, the cached font will not be shared though
                FONT_CACHE.invalidate(DEJA_VU_SANS);
                defaultFont = LOAD_DEJA_VU_SANS_FONT.apply(DEJA_VU_SANS);
            }
            if (defaultFont != null) {
                renderer.getFontResolver().addFont(defaultFont, "DejaVu Sans Mono", null, null, false);
            }
            renderer.layout();
            renderer.createPDF();
        }
    }

    private static final String DEJA_VU_SANS = "/alfio/font/DejaVuSansMono.ttf";

    private static final Function<String, File> LOAD_DEJA_VU_SANS_FONT = classPathResource -> {
        try {
            File cachedFile = File.createTempFile("font-cache", ".tmp");
            cachedFile.deleteOnExit();
            try (InputStream is = new ClassPathResource(DEJA_VU_SANS).getInputStream(); OutputStream tmpOs = new FileOutputStream(cachedFile)) {
                is.transferTo(tmpOs);
            }
            return cachedFile;
        } catch (IOException e) {
            log.warn("error while loading DejaVuSansMono.ttf font", e);
            return null;
        }
    };

    private static class AlfioInternalFSStreamFactory implements FSStreamFactory {

        @Override
        public FSStream getUrl(String url) {
            return new FSStream() {
                @Override
                public InputStream getStream() {
                    String urlWithoutProtocol = url.substring("alfio-internal:/".length());
                    try {
                        return new ClassPathResource("/alfio/font/" + urlWithoutProtocol).getInputStream();
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }

                @Override
                public Reader getReader() {
                    return new InputStreamReader(getStream(), StandardCharsets.UTF_8);
                }
            };
        }
    }

    private static class InvalidProtocolFSStreamFactory implements FSStreamFactory {

        @Override
        public FSStream getUrl(String url) {
            throw new IllegalStateException(new TemplateAccessException("Protocol for resource '" + url + "' is not supported"));
        }
    }

    public static class TemplateAccessException  extends IllegalStateException {
        TemplateAccessException(String message) {
            super(message);
        }
    }

    public static Optional<TemplateResource.ImageData> extractImageModel(Event event, FileUploadManager fileUploadManager) {
        if(event.getFileBlobIdIsPresent()) {
            return fileUploadManager.findMetadata(event.getFileBlobId()).map((metadata) -> {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                fileUploadManager.outputFile(metadata.getId(), baos);
                return TemplateResource.fillWithImageData(metadata, baos.toByteArray());
            });
        } else {
            return Optional.empty();
        }
    }

    public static boolean buildReceiptOrInvoicePdf(Event event,
                                                   FileUploadManager fileUploadManager,
                                                   Locale language,
                                                   TemplateManager templateManager,
                                                   Map<String, Object> model,
                                                   TemplateResource templateResource,
                                                   ExtensionManager extensionManager,
                                                   OutputStream os) {
        try {
            String html = renderReceiptOrInvoicePdfTemplate(event, fileUploadManager, language, templateManager, model, templateResource);
            renderToPdf(html, os, extensionManager, event);
            return true;
        } catch (IOException ioe) {
            return false;
        }
    }

    public static String renderReceiptOrInvoicePdfTemplate(Event event, FileUploadManager fileUploadManager, Locale language, TemplateManager templateManager, Map<String, Object> model, TemplateResource templateResource) {
        extractImageModel(event, fileUploadManager).ifPresent(imageData -> {
            model.put("eventImage", imageData.getEventImage());
            model.put("imageWidth", imageData.getImageWidth());
            model.put("imageHeight", imageData.getImageHeight());
        });
        return templateManager.renderTemplate(event, templateResource, model, language);
    }

    public static Optional<byte[]> buildBillingDocumentPdf(BillingDocument.Type documentType, Event event, FileUploadManager fileUploadManager, Locale language, TemplateManager templateManager, Map<String, Object> model, ExtensionManager extensionManager) {
        switch (documentType) {
            case INVOICE:
                return buildInvoicePdf(event, fileUploadManager, language, templateManager, model, extensionManager);
            case RECEIPT:
                return buildReceiptPdf(event, fileUploadManager, language, templateManager, model, extensionManager);
            case CREDIT_NOTE:
                return buildCreditNotePdf(event, fileUploadManager, language, templateManager, model, extensionManager);
            default:
                throw new IllegalStateException(documentType + " not supported");
        }
    }

    private static Optional<byte[]> buildFrom(Event event,
                                              FileUploadManager fileUploadManager,
                                              Locale language,
                                              TemplateManager templateManager,
                                              Map<String, Object> model,
                                              TemplateResource templateResource,
                                              ExtensionManager extensionManager) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean res = buildReceiptOrInvoicePdf(event, fileUploadManager, language, templateManager, model, templateResource, extensionManager, baos);
        return res ? Optional.of(baos.toByteArray()) : Optional.empty();
    }

    public static Optional<byte[]> buildReceiptPdf(Event event,
                                                   FileUploadManager fileUploadManager,
                                                   Locale language,
                                                   TemplateManager templateManager,
                                                   Map<String, Object> model,
                                                   ExtensionManager extensionManager) {
        return buildFrom(event, fileUploadManager, language, templateManager, model, TemplateResource.RECEIPT_PDF, extensionManager);
    }

    public static Optional<byte[]> buildInvoicePdf(Event event,
                                                   FileUploadManager fileUploadManager,
                                                   Locale language,
                                                   TemplateManager templateManager,
                                                   Map<String, Object> model,
                                                   ExtensionManager extensionManager) {
        return buildFrom(event, fileUploadManager, language, templateManager, model, TemplateResource.INVOICE_PDF, extensionManager);
    }

    public static Optional<byte[]> buildCreditNotePdf(Event event,
                                                      FileUploadManager fileUploadManager,
                                                      Locale language,
                                                      TemplateManager templateManager,
                                                      Map<String, Object> model,
                                                      ExtensionManager extensionManager) {
        return buildFrom(event, fileUploadManager, language, templateManager, model, TemplateResource.CREDIT_NOTE_PDF, extensionManager);
    }
}
