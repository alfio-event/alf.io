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

import alfio.model.Configurable;
import alfio.repository.user.OrganizationRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.env.Environment;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.MAIL_REPLY_TO;
import static alfio.model.system.ConfigurationKeys.MAIL_SET_ORG_REPLY_TO;

@Log4j2
class MockMailer extends BaseMailer {

    private final ConfigurationManager configurationManager;
    private final Environment environment;

    MockMailer(ConfigurationManager configurationManager,
               Environment environment,
               OrganizationRepository organizationRepository) {
        super(organizationRepository);
        this.configurationManager = configurationManager;
        this.environment = environment;
    }

    @Override
    public void send(Configurable configurable, String fromName, String to, List<String> cc, String subject, String text, Optional<String> html, Attachment... attachments) {

        subject = decorateSubjectIfDemo(subject, environment);

        String printedAttachments = Optional.ofNullable(attachments)
            .map(Arrays::asList)
            .orElse(Collections.emptyList())
            .stream().map(a -> "{filename:" +a.getFilename() + ", contentType: " + a.getContentType() + "}")
            .collect(Collectors.joining(", "));

        var conf = configurationManager.getFor(EnumSet.of(MAIL_REPLY_TO, MAIL_SET_ORG_REPLY_TO), configurable.getConfigurationLevel());
        var replyTo = new AtomicReference<String>(null);
        setReplyToIfPresent(conf, configurable.getOrganizationId(), replyTo::set);

        log.info("Email: from: {}, replyTo: {}, to: {}, cc: {}, subject: {}, text: {}, html: {}, attachments: {}",
            fromName,
            replyTo.get(),
            to, cc, subject, text,
            html.orElse("no html"), printedAttachments);
    }
}