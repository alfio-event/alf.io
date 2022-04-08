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
package alfio.manager.support;

import alfio.manager.system.Mailer;
import alfio.util.TemplateResource;

import java.util.List;
import java.util.Map;

public class ConfirmationEmailConfiguration {
    private final TemplateResource templateResource;
    private final String emailAddress;
    private final Map<String, Object> model;
    private final List<Mailer.Attachment> attachments;

    public ConfirmationEmailConfiguration(TemplateResource templateResource,
                                          String emailAddress,
                                          Map<String, Object> model,
                                          List<Mailer.Attachment> attachments) {
        this.templateResource = templateResource;
        this.emailAddress = emailAddress;
        this.model = model;
        this.attachments = attachments;
    }

    public TemplateResource getTemplateResource() {
        return templateResource;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public Map<String, Object> getModel() {
        return model;
    }

    public List<Mailer.Attachment> getAttachments() {
        return attachments;
    }
}
