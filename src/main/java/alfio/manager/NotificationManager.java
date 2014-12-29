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

import alfio.manager.support.PartialTicketPDFGenerator;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.manager.support.TextTemplateGenerator;
import alfio.manager.system.Mailer;
import alfio.model.Event;
import alfio.model.Ticket;
import com.lowagie.text.DocumentException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Optional;

@Component
public class NotificationManager {

    private final Mailer mailer;
    private final MessageSource messageSource;

    @Autowired
    public NotificationManager(Mailer mailer,
                               MessageSource messageSource) {
        this.mailer = mailer;
        this.messageSource = messageSource;
    }

    public void sendTicketByEmail(Ticket ticket, Event event, Locale locale, PartialTicketTextGenerator textBuilder, PartialTicketPDFGenerator ticketBuilder) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ticketBuilder.generate(ticket).createPDF(baos);
        Mailer.Attachment attachment = new Mailer.Attachment("ticket-" + ticket.getUuid() + ".pdf", new ByteArrayResource(baos.toByteArray()), "application/pdf");
        mailer.send(ticket.getEmail(), messageSource.getMessage("ticket-email-subject", new Object[] {event.getShortName()}, locale), textBuilder.generate(ticket), Optional.empty(), attachment);
    }

    public void sendSimpleEmail(String recipient, String subject, TextTemplateGenerator textBuilder) {
        mailer.send(recipient, subject, textBuilder.generate(), Optional.empty());
    }

    public void sendSimpleEmail(String recipient, String subject, String text) {
        mailer.send(recipient, subject, text, Optional.empty());
    }


}
