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
import alfio.manager.support.PDFTemplateGenerator;
import alfio.manager.support.PartialTicketPDFGenerator;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.user.Organization;
import alfio.util.LocaleUtil;
import alfio.util.TemplateManager;
import alfio.util.TemplateResource;
import com.openhtmltopdf.DOMBuilder;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public final class TemplateProcessor {

    private static final Map<String, File> FONTS_CACHE = new ConcurrentHashMap<>();
    private static final List<Pair<String, String>> FONTS = Arrays.asList(
        Pair.of("DejaVuSansMono.ttf", "DejaVu Sans Mono"),
        Pair.of("DejaVuSans.ttf", "DejaVu Sans"),
        Pair.of("DejaVuSerif.ttf", "DejaVu"),
        Pair.of("DejaVuSansMono-Bold.ttf", "DejaVu Sans Mono Bold"),
        Pair.of("DejaVuSans-Bold.ttf", "DejaVu Sans Bold"),
        Pair.of("DejaVuSerif-Bold.ttf", "DejaVu Bold"));

    static {
        for (Pair<String, String> fontDef : FONTS) {
            FONTS_CACHE.put(fontDef.getKey(), getTemporaryFileForFont(fontDef.getKey()));
        }
    }

    private static File getTemporaryFileForFont(String name) {
        try (InputStream is = new ClassPathResource("/alfio/font/"+name).getInputStream()) {
            File f = File.createTempFile(name, null);
            FileCopyUtils.copy(is, new FileOutputStream(f));
            f.deleteOnExit();
            return f;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

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

    public static PDFTemplateGenerator buildPDFTicket(Locale language,
                                                      Event event,
                                                      TicketReservation ticketReservation,
                                                      Ticket ticket,
                                                      TicketCategory ticketCategory,
                                                      Organization organization,
                                                      TemplateManager templateManager,
                                                      FileUploadManager fileUploadManager,
                                                      String reservationID) {
        
        return () -> {
            Optional<TemplateResource.ImageData> imageData = extractImageModel(event, fileUploadManager);
            Map<String, Object> model = TemplateResource.buildModelForTicketPDF(organization, event, ticketReservation, ticketCategory, ticket, imageData, reservationID);

            String page = templateManager.renderTemplate(event, TemplateResource.TICKET_PDF, model, language);
            return prepareItextRenderer(page);
        };
    }

    public static PDFTemplateGenerator.ClosingRenderer prepareItextRenderer(String page) {

        PdfRendererBuilder builder = new PdfRendererBuilder();

        builder.withW3cDocument(DOMBuilder.jsoup2DOM(Jsoup.parse(page)), "");
        PdfBoxRenderer renderer = builder.buildPdfRenderer();
        try {
            for (Pair<String, String> fontDef : FONTS) {
                renderer.getFontResolver().addFont(FONTS_CACHE.get(fontDef.getKey()), fontDef.getValue(), null, null, false);
            }
        } catch (IOException e) {
            log.warn("error while loading fonts", e);
        }
        renderer.layout();
        return new PDFTemplateGenerator.ClosingRenderer(renderer);
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

    public static PartialTicketPDFGenerator buildPartialPDFTicket(Locale language,
                                                                  Event event,
                                                                  TicketReservation ticketReservation,
                                                                  TicketCategory ticketCategory,
                                                                  Organization organization,
                                                                  TemplateManager templateManager,
                                                                  FileUploadManager fileUploadManager,
                                                                  String reservationID) {
        return (ticket) -> buildPDFTicket(language, event, ticketReservation, ticket, ticketCategory, organization, templateManager, fileUploadManager, reservationID).generate();
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
            prepareItextRenderer(page).createPDF(baos);
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
