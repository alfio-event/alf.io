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

package alfio.util;

import java.util.Locale;

public enum TemplateResource {
    GOOGLE_ANALYTICS("/alfio/templates/google-analytics.ms", false, "text/plain", TemplateManager.TemplateOutput.TEXT),
    CONFIRMATION_EMAIL_FOR_ORGANIZER("/alfio/templates/confirmation-email-for-organizer-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    SEND_RESERVED_CODE("/alfio/templates/send-reserved-code-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    CONFIRMATION_EMAIL("/alfio/templates/confirmation-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    OFFLINE_RESERVATION_EXPIRED_EMAIL("/alfio/templates/offline-reservation-expired-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    REMINDER_EMAIL("/alfio/templates/reminder-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    REMINDER_TICKET_ADDITIONAL_INFO("/alfio/templates/reminder-ticket-additional-info.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    REMINDER_TICKETS_ASSIGNMENT_EMAIL("/alfio/templates/reminder-tickets-assignment-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),


    TICKET_EMAIL("/alfio/templates/ticket-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    TICKET_HAS_CHANGED_OWNER("/alfio/templates/ticket-has-changed-owner-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),

    TICKET_HAS_BEEN_CANCELLED("/alfio/templates/ticket-has-been-cancelled-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    TICKET_PDF("/alfio/templates/ticket.ms", true, "application/pdf", TemplateManager.TemplateOutput.HTML),
    RECEIPT_PDF("/alfio/templates/receipt.ms", true, "application/pdf", TemplateManager.TemplateOutput.HTML),

    WAITING_QUEUE_JOINED("/alfio/templates/waiting-queue-joined.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    WAITING_QUEUE_RESERVATION_EMAIL("/alfio/templates/waiting-queue-reservation-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT);

    private final String classPathUrl;
    private final boolean overridable;
    private final String renderedContentType;
    private final TemplateManager.TemplateOutput templateOutput;

    TemplateResource(String classPathUrl, boolean overridable, String renderedContentType, TemplateManager.TemplateOutput templateOutput) {
        this.classPathUrl = classPathUrl;
        this.overridable = overridable;
        this.renderedContentType = renderedContentType;
        this.templateOutput = templateOutput;
    }

    public String getSavedName(Locale locale) {
        return name() + "_" + locale.getLanguage() + ".ms";
    }

    public boolean overridable() {
        return overridable;
    }

    public String classPath() {
        return classPathUrl;
    }

    public TemplateManager.TemplateOutput getTemplateOutput() {
        return templateOutput;
    }
}
