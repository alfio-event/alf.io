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
import alfio.manager.TicketReservationManager;
import alfio.manager.support.PDFTemplateGenerator;
import alfio.manager.support.PartialTicketPDFGenerator;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.model.*;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.user.OrganizationRepository;
import alfio.util.LocaleUtil;
import alfio.util.TemplateManager;
import alfio.util.TemplateManager.TemplateOutput;
import com.openhtmltopdf.DOMBuilder;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Jsoup;
import org.springframework.core.io.ClassPathResource;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static alfio.util.ImageUtil.createQRCode;

@Log4j2
public final class TemplateProcessor {

    private TemplateProcessor() {}

    public static PartialTicketTextGenerator buildPartialEmail(Event event,
                                                   OrganizationRepository organizationRepository,
                                                   TicketReservation ticketReservation,
                                                   TemplateManager templateManager,
                                                   String ticketURL,
                                                   HttpServletRequest request) {
        return (ticket) -> {
            Map<String, Object> model = new HashMap<>();
            model.put("organization", organizationRepository.getById(event.getOrganizationId()));
            model.put("event", event);
            model.put("ticketReservation", ticketReservation);
            model.put("ticketUrl", ticketURL);
            model.put("ticket", ticket);
            Locale language = LocaleUtil.getTicketLanguage(ticket, request);
            return templateManager.renderClassPathResource("/alfio/templates/ticket-email-txt.ms", model, language, TemplateOutput.TEXT);
        };
    }

    public static PartialTicketTextGenerator buildEmailForOwnerChange(Event e,
                                                                 Ticket oldTicket,
                                                                 OrganizationRepository organizationRepository,
                                                                 TicketReservationManager ticketReservationManager,
                                                                 TemplateManager templateManager,
                                                                 Locale language) {
        return (newTicket) -> {
            Map<String, Object> emailModel = new HashMap<>();
            emailModel.put("ticket", oldTicket);
            emailModel.put("organization", organizationRepository.getById(e.getOrganizationId()));
            emailModel.put("eventName", e.getDisplayName());
            emailModel.put("previousEmail", oldTicket.getEmail());
            emailModel.put("newEmail", newTicket.getEmail());
            emailModel.put("ticketUrl", ticketReservationManager.ticketUpdateUrl(oldTicket.getTicketsReservationId(), e, oldTicket.getUuid()));
            return templateManager.renderClassPathResource("/alfio/templates/ticket-has-changed-owner-txt.ms", emailModel, language, TemplateOutput.TEXT);
        };
    }

    public static PDFTemplateGenerator buildPDFTicket(Locale language,
                                                      Event event,
                                                      TicketReservation ticketReservation,
                                                      Ticket ticket,
                                                      TicketCategory ticketCategory,
                                                      Organization organization,
                                                      TemplateManager templateManager,
                                                      FileUploadManager fileUploadManager) {
        
        return () -> {
            String qrCodeText =  ticket.ticketCode(event.getPrivateKey());
            //
            Map<String, Object> model = new HashMap<>();
            model.put("ticket", ticket);
            model.put("reservation", ticketReservation);
            model.put("ticketCategory", ticketCategory);
            model.put("event", event);
            model.put("organization", organization);

            model.put("qrCodeDataUri", "data:image/png;base64," + Base64.getEncoder().encodeToString(createQRCode(qrCodeText)));
            if(event.getFileBlobIdIsPresent()) {
                fileUploadManager.findMetadata(event.getFileBlobId()).ifPresent(m -> fillWithImageData(m, fileUploadManager, model));
            }
            model.put("deskPaymentRequired", Optional.ofNullable(ticketReservation.getPaymentMethod()).orElse(PaymentProxy.STRIPE).isDeskPaymentRequired());

            String page = templateManager.renderClassPathResource("/alfio/templates/ticket.ms", model, language, TemplateOutput.HTML);

            return prepareItextRenderer(page);
        };
    }

    private static PdfBoxRenderer prepareItextRenderer(String page) {

        PdfRendererBuilder builder = new PdfRendererBuilder();

        builder.withW3cDocument(DOMBuilder.jsoup2DOM(Jsoup.parse(page)), "");
        PdfBoxRenderer renderer = builder.buildPdfRenderer();
        try (InputStream is = new ClassPathResource("/alfio/font/DejaVuSansMono.ttf").getInputStream()) {
            renderer.getFontResolver().addFont(is, "DejaVu Sans Mono");
        } catch(IOException e) {
            log.warn("error while loading DejaVuSansMono.ttf font", e);
        }
        renderer.layout();
        return renderer;
    }

    static void fillWithImageData(FileBlobMetadata m, FileUploadManager fileUploadManager, Map<String, Object> model) {
        Map<String, String> attributes = m.getAttributes();
        if (attributes.containsKey(FileUploadManager.ATTR_IMG_WIDTH) && attributes.containsKey(FileUploadManager.ATTR_IMG_HEIGHT)) {
            final int width = Integer.parseInt(attributes.get(FileUploadManager.ATTR_IMG_WIDTH));
            final int height = Integer.parseInt(attributes.get(FileUploadManager.ATTR_IMG_HEIGHT));
            //in the PDF the image can be maximum 300x150
            int resizedWidth = width;
            int resizedHeight = height;
            if (resizedHeight > 150) {
                resizedHeight = 150;
                resizedWidth = width * resizedHeight / height;
            }
            if (resizedWidth > 300) {
                resizedWidth = 300;
                resizedHeight = height * resizedWidth / width;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            fileUploadManager.outputFile(m.getId(), baos);
            model.put("eventImage", "data:" + m.getContentType() + ";base64," + Base64.getEncoder().encodeToString(baos.toByteArray()));
            model.put("imageWidth", resizedWidth);
            model.put("imageHeight", resizedHeight);

        }
    }

    public static PartialTicketPDFGenerator buildPartialPDFTicket(Locale language,
                                                                  Event event,
                                                                  TicketReservation ticketReservation,
                                                                  TicketCategory ticketCategory,
                                                                  Organization organization,
                                                                  TemplateManager templateManager,
                                                                  FileUploadManager fileUploadManager) {
        return (ticket) -> buildPDFTicket(language, event, ticketReservation, ticket, ticketCategory, organization, templateManager, fileUploadManager).generate();
    }

    public static Optional<byte[]> buildReceiptPdf(Event event, FileUploadManager fileUploadManager, Locale language, TemplateManager templateManager, Map<String, Object> model) {

        if(event.getFileBlobIdIsPresent()) {
            fileUploadManager.findMetadata(event.getFileBlobId()).ifPresent(m -> fillWithImageData(m, fileUploadManager, model));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String page = templateManager.renderClassPathResource("/alfio/templates/receipt.ms", model, language, TemplateOutput.HTML);
        try {
            prepareItextRenderer(page).createPDF(baos);
            return Optional.of(baos.toByteArray());
        } catch (IOException ioe) {
            return Optional.empty();
        }
    }
}
