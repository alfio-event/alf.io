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
import alfio.util.RenderedTemplate;
import alfio.util.ImageUtil;
import alfio.util.TemplateManager;
import alfio.util.TemplateResource;
import ch.digitalfondue.jfiveparse.Parser;
import ch.digitalfondue.jfiveparse.W3CDom;
import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.core.io.ClassPathResource;

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

    private TemplateProcessor() {}

    public static RenderedTemplate buildGenericEmail(TemplateManager templateManager ,
                                                     TemplateResource template,
                                                     Locale language,
                                                     Map<String, Object> model,
                                                     EventAndOrganizationId eventAndOrganizationId
                                                         ) {

        return templateManager.renderTemplate(eventAndOrganizationId, template , model, language);
    }

    public static PartialTicketTextGenerator buildPartialEmail(Event event,
                                                               Organization organization,
                                                               TicketReservation ticketReservation,
                                                               TicketCategory category,
                                                               TemplateManager templateManager,
                                                               String baseUrl,
                                                               String ticketURL,
                                                               String calendarURL,
                                                               Locale language,
                                                               Map<String, Object> additionalOptions) {
        return ticket -> {
            Map<String, Object> model = TemplateResource.buildModelForTicketEmail(organization, event, ticketReservation, baseUrl, ticketURL, calendarURL, ticket, category, additionalOptions);
            var template = TemplateResource.TICKET_EMAIL;
            if (event.getIsOnline()){
                if (additionalOptions.containsKey("promoCode")) {
                    template = TemplateResource.TICKET_EMAIL_FOR_ONLINE_CARNET_EVENT ;
                } else if (additionalOptions.containsKey("promoCodeAmount")) {
                    template = TemplateResource.TICKET_EMAIL_FOR_ONLINE_EVENT_CODE_AMOUNT;
                } else {
                    template = TemplateResource.TICKET_EMAIL_FOR_ONLINE_EVENT;
                }
            }
            return templateManager.renderTemplate(event,template , model, language);
        };
    }

    public static PartialTicketTextGenerator buildEmailForOwnerChange(Event e,
                                                                      Ticket oldTicket,
                                                                      Organization organization,
                                                                      String ticketUrl,
                                                                      TemplateManager templateManager,
                                                                      Locale language) {
        return newTicket -> {
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

        String page = templateManager.renderTemplate(event, TemplateResource.TICKET_PDF, model, language).getTextPart();
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
            File defaultFont = ImageUtil.getDejaVuSansMonoFont();
            if (defaultFont != null) {
                renderer.getFontResolver().addFont(defaultFont, "DejaVu Sans Mono", null, null, false);
            }
            renderer.layout();
            renderer.createPDF();
        }
    }

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
            return fileUploadManager.findMetadata(event.getFileBlobId()).map(metadata -> {
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
        return templateManager.renderTemplate(event, templateResource, model, language).getTextPart();
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
