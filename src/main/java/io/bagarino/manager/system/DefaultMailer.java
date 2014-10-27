/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.manager.system;

import io.bagarino.model.system.ConfigurationKeys;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

import lombok.extern.log4j.Log4j2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@Profile("!dev")
public class DefaultMailer implements Mailer {

	private final ConfigurationManager configurationManager;

	@Autowired
	public DefaultMailer(ConfigurationManager configurationManager) {
		this.configurationManager = configurationManager;
	}

	@Override
	public void send(String to, String subject, String text, Optional<String> html, Attachment... attachments) {

		MimeMessagePreparator preparator = (mimeMessage) -> {
			MimeMessageHelper message = html.isPresent() ? new MimeMessageHelper(mimeMessage, true, "UTF-8")
					: new MimeMessageHelper(mimeMessage, "UTF-8");
			message.setSubject(subject);
			message.setFrom(configurationManager.getRequiredValue(ConfigurationKeys.SMTP_FROM_EMAIL));
			message.setTo(to);
			if (html.isPresent()) {
				message.setText(text, html.get());
			} else {
				message.setText(text, false);
			}

			if (attachments != null) {
				for (Attachment a : attachments) {
					message.addAttachment(a.getFilename(), a.getSource(), a.getContentType());
				}
			}
		};
		toMailSender().send(preparator);
	}

	private JavaMailSender toMailSender() {
		JavaMailSenderImpl r = new JavaMailSenderImpl();
		r.setDefaultEncoding("UTF-8");
		r.setHost(configurationManager.getRequiredValue(ConfigurationKeys.SMTP_HOST));
		r.setPort(Integer.valueOf(configurationManager.getRequiredValue(ConfigurationKeys.SMTP_PORT)));
		r.setProtocol(configurationManager.getRequiredValue(ConfigurationKeys.SMTP_PROTOCOL));
		r.setUsername(configurationManager.getStringConfigValue(ConfigurationKeys.SMTP_USERNAME, null));
		r.setPassword(configurationManager.getStringConfigValue(ConfigurationKeys.SMTP_PASSWORD, null));

		String properties = configurationManager.getStringConfigValue(ConfigurationKeys.SMTP_PROPERTIES, null);

		if (properties != null) {
			try {
				Properties prop = PropertiesLoaderUtils.loadProperties(new EncodedResource(new ByteArrayResource(
						properties.getBytes("UTF-8")), "UTF-8"));
				r.setJavaMailProperties(prop);
			} catch (IOException e) {
				log.warn("error while setting the mail sender properties", e);
			}
		}
		return r;
	}
}