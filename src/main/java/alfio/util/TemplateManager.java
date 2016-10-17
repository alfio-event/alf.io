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

import alfio.config.WebSecurityConfig;
import alfio.model.Event;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Mustache.Formatter;
import com.samskivert.mustache.Template;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.context.support.ServletContextResource;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.i18n.MustacheLocalizationMessageInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.mustache.jmustache.JMustacheTemplateLoader;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * For hiding the uglyness :)
 * */
public class TemplateManager {

    public enum TemplateResource {
        GOOGLE_ANALYTICS("/alfio/templates/google-analytics.ms"),
        CONFIRMATION_EMAIL_FOR_ORGANIZER("/alfio/templates/confirmation-email-for-organizer-txt.ms"),
        SEND_RESERVED_CODE("/alfio/templates/send-reserved-code-txt.ms"),
        CONFIRMATION_EMAIL("/alfio/templates/confirmation-email-txt.ms"),
        OFFLINE_RESERVATION_EXPIRED_EMAIL("/alfio/templates/offline-reservation-expired-email-txt.ms"),
        REMINDER_EMAIL("/alfio/templates/reminder-email-txt.ms"),
        REMINDER_TICKET_ADDITIONAL_INFO("/alfio/templates/reminder-ticket-additional-info.ms"),
        REMINDER_TICKETS_ASSIGNMENT_EMAIL("/alfio/templates/reminder-tickets-assignment-email-txt.ms"),


        TICKET_EMAIL("/alfio/templates/ticket-email-txt.ms"),
        TICKET_HAS_CHANGED_OWNER("/alfio/templates/ticket-has-changed-owner-txt.ms"),

        TICKET_HAS_BEEN_CANCELLED("/alfio/templates/ticket-has-been-cancelled-txt.ms"),
        TICKET_PDF("/alfio/templates/ticket.ms"),
        RECEIPT_PDF("/alfio/templates/receipt.ms"),

        WAITING_QUEUE_JOINED("/alfio/templates/waiting-queue-joined.ms"),
        WAITING_QUEUE_RESERVATION_EMAIL("/alfio/templates/waiting-queue-reservation-email-txt.ms");

        TemplateResource(String classPathUrl) {
            this.classPathUrl = classPathUrl;
        }

        private final String classPathUrl;

        String classPath() {
            return classPathUrl;
        }
    }

    private final MessageSource messageSource;
    
    public enum TemplateOutput {
        TEXT, HTML
    }
    
    private final Map<TemplateOutput, Compiler> compilers;

    @Autowired
    public TemplateManager(JMustacheTemplateLoader templateLoader,
                           MessageSource messageSource) {
        this.messageSource = messageSource;
        Formatter dateFormatter = (o) -> {
            return (o instanceof ZonedDateTime) ? DateTimeFormatter.ISO_ZONED_DATE_TIME
                    .format((ZonedDateTime) o) : String.valueOf(o);
        };
        this.compilers = new EnumMap<>(TemplateOutput.class);
        this.compilers.put(TemplateOutput.TEXT, Mustache.compiler()
                .escapeHTML(false)
                .standardsMode(false)
                .defaultValue("")
                .nullValue("")
                .withFormatter(dateFormatter)
                .withLoader(templateLoader));
        this.compilers.put(TemplateOutput.HTML, Mustache.compiler()
                .escapeHTML(true)
                .standardsMode(false)
                .defaultValue("")
                .nullValue("")
                .withFormatter(dateFormatter)
                .withLoader(templateLoader));
    }

    public String renderTemplate(TemplateResource templateResource, Map<String, Object> model, Locale locale, TemplateOutput templateOutput) {
        return render(new ClassPathResource(templateResource.classPath()), templateResource.classPath(), model, locale, templateOutput, true);
    }

    public String renderTemplate(Event event, TemplateResource templateResource, Map<String, Object> model, Locale locale, TemplateOutput templateOutput) {
        return renderTemplate(templateResource, model, locale, templateOutput);
    }

    public String renderString(String template, Map<String, Object> model, Locale locale, TemplateOutput templateOutput) {
        return render(new ByteArrayResource(template.getBytes(StandardCharsets.UTF_8)), "", model, locale, templateOutput, false);
    }

    //TODO: to be removed when only the rest api will be exposed
    public String renderServletContextResource(String servletContextResource, Map<String, Object> model, HttpServletRequest request, TemplateOutput templateOutput) {
        model.put("request", request);
        model.put(WebSecurityConfig.CSRF_PARAM_NAME, request.getAttribute(CsrfToken.class.getName()));
        return render(new ServletContextResource(request.getServletContext(), servletContextResource), servletContextResource, model, RequestContextUtils.getLocale(request), templateOutput, true);
    }

    private String render(AbstractResource resource, String key, Map<String, Object> model, Locale locale, TemplateOutput templateOutput, boolean cachingRequested) {
        try {
            ModelAndView mv = new ModelAndView((String) null, model);
            mv.addObject("format-date", MustacheCustomTagInterceptor.FORMAT_DATE);
            mv.addObject(MustacheLocalizationMessageInterceptor.DEFAULT_MODEL_KEY, new CustomLocalizationMessageInterceptor(locale, messageSource).createTranslator());
            return compile(resource, templateOutput).execute(mv.getModel());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Template compile(AbstractResource resource, TemplateOutput templateOutput) {
        try (InputStreamReader tmpl = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return compilers.get(templateOutput).compile(tmpl);
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    private static class CustomLocalizationMessageInterceptor {

        private static final Pattern KEY_PATTERN = Pattern.compile("(.*?)[\\s\\[]");
        private static final Pattern ARGS_PATTERN = Pattern.compile("\\[(.*?)\\]");
        private final Locale locale;
        private final MessageSource messageSource;

        private CustomLocalizationMessageInterceptor(Locale locale, MessageSource messageSource) {
            this.locale = locale;
            this.messageSource = messageSource;
        }

        protected Mustache.Lambda createTranslator() {
            return (frag, out) -> {
                String template = frag.execute();
                final String key = extractKey(template);
                final List<String> args = extractParameters(template);
                final String text = messageSource.getMessage(key, args.toArray(), locale);
                out.write(text);
            };
        }

        /**
         * Split key from (optional) arguments.
         *
         * @param key
         * @return localization key
         */
        private String extractKey(String key) {
            Matcher matcher = KEY_PATTERN.matcher(key);
            if (matcher.find()) {
                return matcher.group(1);
            }

            return key;
        }

        /**
         * Split args from input string.
         * <p/>
         * localization_key [param1] [param2] [param3]
         *
         * @param key
         * @return List of extracted parameters
         */
        private List<String> extractParameters(String key) {
            final Matcher matcher = ARGS_PATTERN.matcher(key);
            final List<String> args = new ArrayList<>();
            while (matcher.find()) {
                args.add(matcher.group(1));
            }
            return args;
        }
    }
}
