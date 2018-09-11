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

import alfio.manager.FileUploadManager;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.model.*;
import alfio.model.user.Organization;
import alfio.util.LocaleUtil;
import alfio.util.TemplateManager;
import alfio.util.TemplateResource;
import com.openhtmltopdf.DOMBuilder;
import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.log4j.Log4j2;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
                                       Function<Ticket, List<TicketFieldConfigurationDescriptionAndValue>> retrieveFieldValues) throws IOException {
        Optional<TemplateResource.ImageData> imageData = extractImageModel(event, fileUploadManager);
        List<TicketFieldConfigurationDescriptionAndValue> fields = retrieveFieldValues.apply(ticket);
        Map<String, Object> model = TemplateResource.buildModelForTicketPDF(organization, event, ticketReservation, ticketCategory, ticket, imageData, reservationID,
            fields.stream().collect(Collectors.toMap(TicketFieldConfigurationDescriptionAndValue::getName, TicketFieldConfigurationDescriptionAndValue::getValueDescription)));

        String page = templateManager.renderTemplate(event, TemplateResource.TICKET_PDF, model, language);
        renderToPdf(page, os);
    }

    public static void renderToPdf(String page, OutputStream os) throws IOException {

        PdfRendererBuilder builder = new PdfRendererBuilder();
        PDDocument doc = new PDDocument(MemoryUsageSetting.setupTempFileOnly());
        builder.usePDDocument(doc);
        builder.toStream(os);
        builder.useProtocolsStreamImplementation(new AlfioInternalFSStreamFactory(), "alfio-internal");
        builder.useProtocolsStreamImplementation(new InvalidProtocolFSStreamFactory(), "http", "https", "file", "jar");


        Document parsedDocument = Jsoup.parse(page);
        //add <meta name="fast-renderer" content="true"> in the html document to enable  the fast renderer
        if (!parsedDocument.select("meta[name=fast-renderer][content=true]").isEmpty()) {
            builder.useFastMode();
        }

        builder.withW3cDocument(DOMBuilder.jsoup2DOM(parsedDocument), "");
        PdfBoxRenderer renderer = builder.buildPdfRenderer();
        try (InputStream is = new ClassPathResource("/alfio/font/DejaVuSansMono.ttf").getInputStream()) {
            renderer.getFontResolver().addFont(() -> is, "DejaVu Sans Mono", null, null, false);
        } catch(IOException e) {
            log.warn("error while loading DejaVuSansMono.ttf font", e);
        }
        try {
            renderer.layout();
            renderer.createPDF();
        } finally {
            renderer.close();
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
            return fileUploadManager.findMetadata(event.getFileBlobId()).map((metadata) -> {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                fileUploadManager.outputFile(metadata.getId(), baos);
                return TemplateResource.fillWithImageData(metadata, baos.toByteArray());
            });
        } else {
            return Optional.empty();
        }
    }

    private static Optional<byte[]> buildReceiptOrInvoicePdf(Event event, FileUploadManager fileUploadManager, Locale language, TemplateManager templateManager, Map<String, Object> model, TemplateResource templateResource) {
        extractImageModel(event, fileUploadManager).ifPresent(imageData -> {
            model.put("eventImage", imageData.getEventImage());
            model.put("imageWidth", imageData.getImageWidth());
            model.put("imageHeight", imageData.getEventImage());
        });

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String page = templateManager.renderTemplate(event, templateResource, model, language);
        try {
            renderToPdf(page, baos);
            return Optional.of(baos.toByteArray());
        } catch (IOException ioe) {
            return Optional.empty();
        }
    }


    public static Optional<byte[]> buildReceiptPdf(Event event, FileUploadManager fileUploadManager, Locale language, TemplateManager templateManager, Map<String, Object> model) {
        return buildReceiptOrInvoicePdf(event, fileUploadManager, language, templateManager, model, TemplateResource.RECEIPT_PDF);
    }

    public static Optional<byte[]> buildInvoicePdf(Event event, FileUploadManager fileUploadManager, Locale language, TemplateManager templateManager, Map<String, Object> model) {
        return buildReceiptOrInvoicePdf(event, fileUploadManager, language, templateManager, model, TemplateResource.INVOICE_PDF);
    }
}
