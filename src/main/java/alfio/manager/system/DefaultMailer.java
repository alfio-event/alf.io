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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.MAILER_TYPE;

@Component
@Profile(Initializer.PROFILE_LIVE)
public class DefaultMailer implements Mailer {

    private final ConfigurationManager configurationManager;
    private final Map<String, Mailer> mailers;
    private final Mailer defaultMailer;

    @Autowired
    public DefaultMailer(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
        this.mailers = new HashMap<>();
        this.defaultMailer = new SmtpMailer(configurationManager);
        mailers.put("smtp", defaultMailer);
        mailers.put("mailgun", new MailgunMailer(configurationManager));
    }

    @Override
    public void send(Event event, String to, String subject, String text,
            Optional<String> html, Attachment... attachments) {

        String mailerType = configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), MAILER_TYPE), "smtp").toLowerCase(Locale.ENGLISH);

        mailers.getOrDefault(mailerType, defaultMailer)
                .send(event, to, subject, text, html, attachments);
    }

}