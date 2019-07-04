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
package alfio.manager.i18n;

import alfio.model.EventAndOrganizationId;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class MessageSourceManager {

    private final MessageSource messageSource;

    public MessageSourceManager(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public MessageSource getMessageSourceForEvent(EventAndOrganizationId eventAndOrganizationId) {
        return messageSource;
    }

    public MessageSource getRootMessageSource() {
        return messageSource;
    }
}
