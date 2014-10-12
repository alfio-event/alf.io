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
import java.util.Properties;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Component;

/**
 * See https://javamail.java.net/nonav/docs/api/com/sun/mail/smtp/package-summary.html for additional parameters.
 * 
 * TODO: to be refactored...
 */
@Component
public class MailManager {

	private final Environment environment;
	private final ConfigurationManager configurationManager;

	@Autowired
	public MailManager(Environment environment, ConfigurationManager configurationManager) {
		this.environment = environment;
		this.configurationManager = configurationManager;
	}

	public Mailer getMailer() {
		if (ArrayUtils.contains(environment.getActiveProfiles(), "dev")) {
			return new DevMailer();
		} else {
			//TODO: configurationManager should return in a single call/query a key,value map.
			return new ProdMailer(configurationManager.getRequiredValue(ConfigurationKeys.SMTP_HOST),//
					Integer.valueOf(configurationManager.getRequiredValue(ConfigurationKeys.SMTP_PORT)),//
					configurationManager.getRequiredValue(ConfigurationKeys.SMTP_PROTOCOL),//
					configurationManager.getStringConfigValue(ConfigurationKeys.SMTP_USERNAME, null),//
					configurationManager.getStringConfigValue(ConfigurationKeys.SMTP_PASSWORD, null),//
					configurationManager.getRequiredValue(ConfigurationKeys.SMTP_FROM_EMAIL),//
					configurationManager.getStringConfigValue(ConfigurationKeys.SMTP_PROPERTIES, null));
		}
	}

	public interface Mailer {
		void send(final String to, final String subject, final String text, Attachment... attachment);

		void send(final String to, final String subject, final String text, final String html, Attachment... attachment);
	}

	@Log4j2
	public static class DevMailer implements Mailer {
		@Override
		public void send(String to, String subject, String text, Attachment... attachments) {
			send(to, subject, text, null, attachments);
		}

		@Override
		public void send(String to, String subject, String text, String html, Attachment... attachments) {
			log.info("Email: to: {}, subject: {}, text: {}, html: {}, attachments amount: {}", to, subject, text, html,
					ArrayUtils.getLength(attachments));
		}

	}

	@AllArgsConstructor
	public static class Attachment {
		private final String filename;
		private final InputStreamSource source;
		private final String contentType;
	}

	@Log4j2
	@AllArgsConstructor
	public static class ProdMailer implements Mailer {
		private final String host;
		private final Integer port;
		private final String protocol;
		private final String username;
		private final String password;
		private final String from;
		private final String properties;

		@Override
		public void send(final String to, final String subject, final String text, Attachment... attachments) {
			send(to, subject, text, null, attachments);
		}

		@Override
		public void send(final String to, final String subject, final String text, final String html,
				Attachment... attachments) {

			MimeMessagePreparator preparator = (mimeMessage) -> {
				MimeMessageHelper message = html == null ? new MimeMessageHelper(mimeMessage, "UTF-8")
						: new MimeMessageHelper(mimeMessage, true, "UTF-8");
				message.setSubject(subject);
				message.setFrom(from);
				message.setTo(to);
				if (html == null) {
					message.setText(text, false);
				} else {
					message.setText(text, html);
				}

				if (attachments != null) {
					for (Attachment a : attachments) {
						message.addAttachment(a.filename, a.source, a.contentType);
					}
				}
			};
			toMailSender().send(preparator);
		}

		private JavaMailSender toMailSender() {
			JavaMailSenderImpl r = new JavaMailSenderImpl();
			r.setDefaultEncoding("UTF-8");
			r.setHost(host);
			r.setPort(port);
			r.setProtocol(protocol);
			r.setUsername(username);
			r.setPassword(password);
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
}
