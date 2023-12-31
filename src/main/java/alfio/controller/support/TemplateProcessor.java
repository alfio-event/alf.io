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

import alfio.controller.api.support.AdditionalServiceWithData;
import alfio.manager.ExtensionManager;
import alfio.manager.FileUploadManager;
import alfio.manager.PurchaseContextFieldManager;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.model.*;
import alfio.model.metadata.SubscriptionMetadata;
import alfio.model.subscription.Subscription;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.user.Organization;
import alfio.util.EventUtil;
import alfio.util.ImageUtil;
import alfio.util.TemplateManager;
import alfio.util.TemplateResource;
import ch.digitalfondue.jfiveparse.Parser;
import ch.digitalfondue.jfiveparse.W3CDom;
import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.pdfboxout.PdfBoxFontResolver;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.log4j.Log4j2;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
public final class TemplateProcessor {

    private TemplateProcessor() {}


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
            return templateManager.renderTemplate(event, EventUtil.isAccessOnline(category, event) ? TemplateResource.TICKET_EMAIL_FOR_ONLINE_EVENT : TemplateResource.TICKET_EMAIL, model, language);
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
                                       TicketWithMetadataAttributes ticketWithMetadata,
                                       TicketCategory ticketCategory,
                                       Organization organization,
                                       TemplateManager templateManager,
                                       FileUploadManager fileUploadManager,
                                       String reservationID,
                                       OutputStream os,
                                       BiFunction<Ticket, Event, List<FieldConfigurationDescriptionAndValue>> retrieveFieldValues,
                                       ExtensionManager extensionManager,
                                       Map<String, Object> initialModel,
                                       List<AdditionalServiceWithData> additionalServiceWithData) throws IOException {
        Optional<TemplateResource.ImageData> imageData = extractImageModel(event, fileUploadManager);
        List<FieldConfigurationDescriptionAndValue> fields = retrieveFieldValues.apply(ticketWithMetadata.getTicket(), event);
        var model = new HashMap<>(Objects.requireNonNullElse(initialModel, Map.of()));
        model.putAll(TemplateResource.buildModelForTicketPDF(organization, event, ticketReservation, ticketCategory, ticketWithMetadata, imageData, reservationID,
            fields.stream().collect(Collectors.toMap(FieldConfigurationDescriptionAndValue::getName, FieldConfigurationDescriptionAndValue::getValueDescription)),
            additionalServiceWithData));

        String page = templateManager.renderTemplate(event, TemplateResource.TICKET_PDF, model, language).getTextPart();
        renderToPdf(page, os, extensionManager, event);
    }

    public static Map<String, Object> getSubscriptionDetailsModelForTicket(Ticket ticket,
                                                                           Function<UUID, SubscriptionDescriptor> subscriptionDescriptorLoader,
                                                                           Locale locale) {
        boolean hasSubscription = ticket.getSubscriptionId() != null;
        var result = new HashMap<String, Object>();
        result.put("hasSubscription", hasSubscription);
        if (hasSubscription) {
            var subscriptionDescriptor = subscriptionDescriptorLoader.apply(ticket.getSubscriptionId());
            result.put("subscriptionTitle", subscriptionDescriptor.getLocalizedTitle(locale));
        }
        return result;
    }

    public static void renderSubscriptionPDF(Subscription subscription,
                                             Locale locale,
                                             SubscriptionDescriptor subscriptionDescriptor,
                                             TicketReservation reservation,
                                             SubscriptionMetadata metadata,
                                             Organization organization,
                                             TemplateManager templateManager,
                                             FileUploadManager fileUploadManager,
                                             String reservationId,
                                             ByteArrayOutputStream os,
                                             ExtensionManager extensionManager,
                                             PurchaseContextFieldManager purchaseContextFieldManager) throws IOException {
        Optional<TemplateResource.ImageData> imageData = extractImageModel(subscriptionDescriptor, fileUploadManager);
        var additionalFields = purchaseContextFieldManager.getFieldDescriptionAndValues(subscriptionDescriptor, null, subscription, List.of(), locale.getLanguage(), true);
        Map<String, Object> model = TemplateResource.buildModelForSubscriptionPDF(subscription, subscriptionDescriptor, organization, metadata, imageData, reservationId, locale, reservation, additionalFields);
        String page = templateManager.renderTemplate(subscriptionDescriptor, TemplateResource.SUBSCRIPTION_PDF, model, locale).getTextPart();
        renderToPdf(page, os, extensionManager, subscriptionDescriptor);
    }

    public static void renderToPdf(String page, OutputStream os, ExtensionManager extensionManager, PurchaseContext purchaseContext) throws IOException {

        if(extensionManager.handlePdfTransformation(page, purchaseContext, os)) {
            return;
        }
        PdfRendererBuilder builder = new PdfRendererBuilder();
        PDDocument doc = new PDDocument(MemoryUsageSetting.setupTempFileOnly());
        builder.usePDDocument(doc);
        builder.toStream(os);
        builder.useProtocolsStreamImplementation(new AlfioInternalFSStreamFactory(), "alfio-internal");
        builder.useProtocolsStreamImplementation(new InvalidProtocolFSStreamFactory(), "http", "https", "file", "jar");
        builder.useFastMode();
        builder.usePdfUaAccessbility(true);
        builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_3_U);

        var parser = new Parser();

        builder.withW3cDocument(W3CDom.toW3CDocument(parser.parse(page)), "");
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            File defaultFont = ImageUtil.getDejaVuSansMonoFont();
            if (defaultFont != null) {
                renderer.getFontResolver().addFont(defaultFont, "DejaVu Sans Mono", null, null, false, PdfBoxFontResolver.FontGroup.MAIN);
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

    public static Optional<TemplateResource.ImageData> extractImageModel(PurchaseContext purchaseContext, FileUploadManager fileUploadManager) {
        if(purchaseContext.getFileBlobIdIsPresent()) {
            return fileUploadManager.findMetadata(purchaseContext.getFileBlobId()).map(metadata -> {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                fileUploadManager.outputFile(metadata.getId(), baos);
                return TemplateResource.fillWithImageData(metadata, baos.toByteArray());
            });
        } else {
            return Optional.empty();
        }
    }

    public static boolean buildReceiptOrInvoicePdf(PurchaseContext purchaseContext,
                                                   FileUploadManager fileUploadManager,
                                                   Locale language,
                                                   TemplateManager templateManager,
                                                   Map<String, Object> model,
                                                   TemplateResource templateResource,
                                                   ExtensionManager extensionManager,
                                                   OutputStream os) {
        try {
            String html = renderReceiptOrInvoicePdfTemplate(purchaseContext, fileUploadManager, language, templateManager, model, templateResource);
            renderToPdf(html, os, extensionManager, purchaseContext);
            return true;
        } catch (IOException ioe) {
            return false;
        }
    }

    public static String renderReceiptOrInvoicePdfTemplate(PurchaseContext purchaseContext, FileUploadManager fileUploadManager, Locale language, TemplateManager templateManager, Map<String, Object> model, TemplateResource templateResource) {
        extractImageModel(purchaseContext, fileUploadManager).ifPresent(imageData -> {
            model.put("eventImage", imageData.getEventImage());
            model.put("imageWidth", imageData.getImageWidth());
            model.put("imageHeight", imageData.getImageHeight());
        });
        return templateManager.renderTemplate(purchaseContext, templateResource, model, language).getTextPart();
    }

    public static Optional<byte[]> buildBillingDocumentPdf(BillingDocument.Type documentType, PurchaseContext purchaseContext, FileUploadManager fileUploadManager, Locale language, TemplateManager templateManager, Map<String, Object> model, ExtensionManager extensionManager) {
        switch (documentType) {
            case INVOICE:
                return buildInvoicePdf(purchaseContext, fileUploadManager, language, templateManager, model, extensionManager);
            case RECEIPT:
                return buildReceiptPdf(purchaseContext, fileUploadManager, language, templateManager, model, extensionManager);
            case CREDIT_NOTE:
                return buildCreditNotePdf(purchaseContext, fileUploadManager, language, templateManager, model, extensionManager);
            default:
                throw new IllegalStateException(documentType + " not supported");
        }
    }

    private static Optional<byte[]> buildFrom(PurchaseContext purchaseContext,
                                              FileUploadManager fileUploadManager,
                                              Locale language,
                                              TemplateManager templateManager,
                                              Map<String, Object> model,
                                              TemplateResource templateResource,
                                              ExtensionManager extensionManager) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean res = buildReceiptOrInvoicePdf(purchaseContext, fileUploadManager, language, templateManager, model, templateResource, extensionManager, baos);
        return res ? Optional.of(baos.toByteArray()) : Optional.empty();
    }

    public static Optional<byte[]> buildReceiptPdf(PurchaseContext purchaseContext,
                                                   FileUploadManager fileUploadManager,
                                                   Locale language,
                                                   TemplateManager templateManager,
                                                   Map<String, Object> model,
                                                   ExtensionManager extensionManager) {
        return buildFrom(purchaseContext, fileUploadManager, language, templateManager, model, TemplateResource.RECEIPT_PDF, extensionManager);
    }

    public static Optional<byte[]> buildInvoicePdf(PurchaseContext purchaseContext,
                                                   FileUploadManager fileUploadManager,
                                                   Locale language,
                                                   TemplateManager templateManager,
                                                   Map<String, Object> model,
                                                   ExtensionManager extensionManager) {
        return buildFrom(purchaseContext, fileUploadManager, language, templateManager, model, TemplateResource.INVOICE_PDF, extensionManager);
    }

    public static Optional<byte[]> buildCreditNotePdf(PurchaseContext purchaseContext,
                                                      FileUploadManager fileUploadManager,
                                                      Locale language,
                                                      TemplateManager templateManager,
                                                      Map<String, Object> model,
                                                      ExtensionManager extensionManager) {
        return buildFrom(purchaseContext, fileUploadManager, language, templateManager, model, TemplateResource.CREDIT_NOTE_PDF, extensionManager);
    }
}
