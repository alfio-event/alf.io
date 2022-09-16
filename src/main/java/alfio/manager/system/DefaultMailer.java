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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.*;

import static alfio.model.system.ConfigurationKeys.MAILER_TYPE;

@Component
public class DefaultMailer implements Mailer {

    private final ConfigurationManager configurationManager;
    private final Map<String, Mailer> mailers;
    private final Mailer defaultMailer;
    private final Environment environment;

    @Autowired
    public DefaultMailer(ConfigurationManager configurationManager,
                         Environment environment,
                         HttpClient httpClient,
                         OrganizationRepository organizationRepository) {
        this.configurationManager = configurationManager;
        this.environment = environment;
        this.mailers = new HashMap<>();
        this.defaultMailer = new SmtpMailer(configurationManager, organizationRepository);
        mailers.put("smtp", defaultMailer);
        mailers.put("mailgun", new MailgunMailer(httpClient, configurationManager, organizationRepository));
        mailers.put("mailjet", new MailjetMailer(httpClient, configurationManager, organizationRepository));
        mailers.put("sendgrid", new SendGridMailer(httpClient, configurationManager, organizationRepository));
        mailers.put("disabled", new MockMailer(configurationManager, environment, organizationRepository));
    }

    @Override
    public void send(Configurable configurable, String fromName, String to, List<String> cc, String subject, String text,
                     Optional<String> html, Attachment... attachments) {

        subject = decorateSubjectIfDemo(subject, environment);

        String mailerType = configurationManager.getFor(MAILER_TYPE, configurable.getConfigurationLevel())
            .getValueOrDefault("disabled").toLowerCase(Locale.ENGLISH);

        mailers.getOrDefault(mailerType, defaultMailer)
                .send(configurable, fromName, to, cc, subject, text, html, attachments);
    }

}