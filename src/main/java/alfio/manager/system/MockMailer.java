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
package alfio.manager.system;

import alfio.config.Initializer;
import alfio.model.Event;
import alfio.model.system.Configuration;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.MAIL_REPLY_TO;

@Log4j2
@Component
@Profile(Initializer.PROFILE_DEV)
public class MockMailer implements Mailer {

    private final ConfigurationManager configurationManager;

    @Autowired
    public MockMailer(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @Override
    public void send(Event event, String to, String subject, String text, Optional<String> html, Attachment... attachments) {

        String printedAttachments = Optional.ofNullable(attachments)
            .map(Arrays::asList)
            .orElse(Collections.emptyList())
            .stream().map(a -> "{filename:" +a.getFilename() + ", contentType: " + a.getContentType() + "}")
            .collect(Collectors.joining(", "));

        log.info("Email: from: {}, replyTo: {}, to: {}, subject: {}, text: {}, html: {}, attachments: {}", event.getDisplayName(), configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), MAIL_REPLY_TO), ""), to, subject, text,
            html.orElse("no html"), printedAttachments);
    }
}