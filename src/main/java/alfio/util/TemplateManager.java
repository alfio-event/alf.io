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
import alfio.manager.UploadedResourceManager;
import alfio.model.Event;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Mustache.Formatter;
import com.samskivert.mustache.Template;
import org.apache.commons.lang3.tuple.Pair;
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
        GOOGLE_ANALYTICS("/alfio/templates/google-analytics.ms", false, "text/plain", TemplateOutput.TEXT),
        CONFIRMATION_EMAIL_FOR_ORGANIZER("/alfio/templates/confirmation-email-for-organizer-txt.ms", true, "text/plain", TemplateOutput.TEXT),
        SEND_RESERVED_CODE("/alfio/templates/send-reserved-code-txt.ms", true, "text/plain", TemplateOutput.TEXT),
        CONFIRMATION_EMAIL("/alfio/templates/confirmation-email-txt.ms", true, "text/plain", TemplateOutput.TEXT),
        OFFLINE_RESERVATION_EXPIRED_EMAIL("/alfio/templates/offline-reservation-expired-email-txt.ms", true, "text/plain", TemplateOutput.TEXT),
        REMINDER_EMAIL("/alfio/templates/reminder-email-txt.ms", true, "text/plain", TemplateOutput.TEXT),
        REMINDER_TICKET_ADDITIONAL_INFO("/alfio/templates/reminder-ticket-additional-info.ms", true, "text/plain", TemplateOutput.TEXT),
        REMINDER_TICKETS_ASSIGNMENT_EMAIL("/alfio/templates/reminder-tickets-assignment-email-txt.ms", true, "text/plain", TemplateOutput.TEXT),


        TICKET_EMAIL("/alfio/templates/ticket-email-txt.ms", true, "text/plain", TemplateOutput.TEXT) {


            public Map<String,Object> prepareModel() {
                Map<String, Object> m  = new HashMap<>();
                return m;
            }
        },
        TICKET_HAS_CHANGED_OWNER("/alfio/templates/ticket-has-changed-owner-txt.ms", true, "text/plain", TemplateOutput.TEXT),

        TICKET_HAS_BEEN_CANCELLED("/alfio/templates/ticket-has-been-cancelled-txt.ms", true, "text/plain", TemplateOutput.TEXT),
        TICKET_PDF("/alfio/templates/ticket.ms", true, "application/pdf", TemplateOutput.HTML),
        RECEIPT_PDF("/alfio/templates/receipt.ms", true, "application/pdf", TemplateOutput.HTML),

        WAITING_QUEUE_JOINED("/alfio/templates/waiting-queue-joined.ms", true, "text/plain", TemplateOutput.TEXT),
        WAITING_QUEUE_RESERVATION_EMAIL("/alfio/templates/waiting-queue-reservation-email-txt.ms", true, "text/plain", TemplateOutput.TEXT);

        private final String classPathUrl;
        private final boolean overridable;
        private final String renderedContentType;
        private final TemplateOutput templateOutput;

        TemplateResource(String classPathUrl, boolean overridable, String renderedContentType, TemplateOutput templateOutput) {
            this.classPathUrl = classPathUrl;
            this.overridable = overridable;
            this.renderedContentType = renderedContentType;
            this.templateOutput = templateOutput;
        }

        String getSavedName(Locale locale) {
            return name() + "_" + locale.getLanguage() + ".ms";
        }

        public boolean overridable() {
            return overridable;
        }

        public String classPath() {
            return classPathUrl;
        }

        public TemplateOutput getTemplateOutput() {
            return templateOutput;
        }
    }

    private final MessageSource messageSource;

    public enum TemplateOutput {
        TEXT, HTML
    }

    private final Map<TemplateOutput, Compiler> compilers;

    private final UploadedResourceManager uploadedResourceManager;

    @Autowired
    public TemplateManager(JMustacheTemplateLoader templateLoader,
                           MessageSource messageSource,
                           UploadedResourceManager uploadedResourceManager) {
        this.messageSource = messageSource;
        this.uploadedResourceManager = uploadedResourceManager;
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

    public String renderTemplate(TemplateResource templateResource, Map<String, Object> model, Locale locale) {
        return render(new ClassPathResource(templateResource.classPath()), model, locale, templateResource.templateOutput);
    }

    public String renderTemplate(Event event, TemplateResource templateResource, Map<String, Object> model, Locale locale) {
        return uploadedResourceManager.findCascading(event.getOrganizationId(), event.getId(), templateResource.getSavedName(locale))
            .map(resource -> render(new ByteArrayResource(resource), model, locale, templateResource.templateOutput))
            .orElseGet(() -> renderTemplate(templateResource, model, locale));
    }

    public String renderString(String template, Map<String, Object> model, Locale locale, TemplateOutput templateOutput) {
        return render(new ByteArrayResource(template.getBytes(StandardCharsets.UTF_8)), model, locale, templateOutput);
    }

    //TODO: to be removed when only the rest api will be exposed
    public String renderServletContextResource(String servletContextResource, Map<String, Object> model, HttpServletRequest request, TemplateOutput templateOutput) {
        model.put("request", request);
        model.put(WebSecurityConfig.CSRF_PARAM_NAME, request.getAttribute(CsrfToken.class.getName()));
        return render(new ServletContextResource(request.getServletContext(), servletContextResource), model, RequestContextUtils.getLocale(request), templateOutput);
    }

    private String render(AbstractResource resource, Map<String, Object> model, Locale locale, TemplateOutput templateOutput) {
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

    private static final Pattern KEY_PATTERN = Pattern.compile("(.*?)[\\s\\[]");
    private static final Pattern ARGS_PATTERN = Pattern.compile("\\[(.*?)\\]");

    /**
     * Split key from (optional) arguments.
     *
     * @param key
     * @return localization key
     */
    private static String extractKey(String key) {
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
    private static List<String> extractParameters(String key) {
        final Matcher matcher = ARGS_PATTERN.matcher(key);
        final List<String> args = new ArrayList<>();
        while (matcher.find()) {
            args.add(matcher.group(1));
        }
        return args;
    }

    private static class CustomLocalizationMessageInterceptor {

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
    }

    private static final String START_TAG = "{{#i18n}}";
    private static final String END_TAG = "{{/i18n}}";

    private enum ParserState {
        START {
            @Override
            public Pair<ParserState, Integer> next(String template, int idx, AST ast) {
                int startTagIdx = template.indexOf(START_TAG, idx);
                if (startTagIdx == -1) {
                    ast.addText(template);
                    return Pair.of(END, idx);
                } else {
                    ast.addText(template.substring(0, startTagIdx));
                    ast.addI18NNode(startTagIdx + START_TAG.length());
                    return Pair.of(OPEN_TAG, startTagIdx + START_TAG.length());
                }
            }
        }, OPEN_TAG {
            @Override
            public Pair<ParserState, Integer> next(String template, int idx, AST ast) {
                int startTagIdx = template.indexOf(START_TAG, idx);
                int endTagIdx = template.indexOf(END_TAG, idx);

                if (endTagIdx != -1 && startTagIdx != -1 && startTagIdx < endTagIdx) {
                    ast.addText(template.substring(idx, startTagIdx));
                    int startTagIdxBoundary = startTagIdx + START_TAG.length();
                    ast.addI18NNode(startTagIdxBoundary);
                    return Pair.of(OPEN_TAG, startTagIdxBoundary);
                } else if (endTagIdx == -1 && startTagIdx == -1) {
                    ast.addText(template.substring(idx));
                    return Pair.of(END, idx);
                } else if (endTagIdx != -1) {
                    ast.addText(template.substring(idx, endTagIdx));
                    return Pair.of(CLOSE_TAG, endTagIdx);
                } else {
                    throw new IllegalStateException("should not be reached");
                }
            }
        }, CLOSE_TAG {
            @Override
            public Pair<ParserState, Integer> next(String template, int idx, AST ast) {

                //
                ast.focusToParent();
                //
                return Pair.of(OPEN_TAG, idx + END_TAG.length());
            }
        }, END {
            @Override
            public Pair<ParserState, Integer> next(String template, int idx, AST ast) {

                if (ast.currentLevel != ast.root) {
                    throw new IllegalStateException("unbalanced tags");
                }

                return Pair.of(END, idx);
            }
        };

        public abstract Pair<ParserState, Integer> next(String template, int idx, AST ast);
    }

    static class AST {
        Node root = new Node();
        Node currentLevel = root;

        void addChild(Node node) {
            node.parent = currentLevel;
            currentLevel.addChild(node);
        }

        void addText(String text) {
            if (text.length() > 0) {
                addChild(new TextNode(text));
            }
        }

        void addI18NNode(int startIdx) {
            addChild(new I18NNode(startIdx));
            currentLevel = currentLevel.children.get(currentLevel.children.size() - 1);
        }

        void focusToParent() {
            currentLevel = currentLevel.parent;
        }

        public void visit(StringBuilder sb, Locale locale, MessageSource messageSource) {
            root.visit(sb, locale, messageSource);
        }
    }

    static class Node {

        Node parent;
        List<Node> children = new ArrayList<>(1);

        void addChild(Node node) {
            children.add(node);
        }

        public void visit(StringBuilder sb, Locale locale, MessageSource messageSource) {
            for (Node node : children) {
                node.visit(sb, locale, messageSource);
            }
        }
    }

    static class TextNode extends Node {
        String text;

        TextNode(String text) {
            this.text = text;
        }

        @Override
        public void visit(StringBuilder sb, Locale locale, MessageSource messageSource) {
            sb.append(text);
        }
    }

    static class I18NNode extends Node {
        int startIdx;

        I18NNode(int startIdx) {
            this.startIdx = startIdx;
        }

        @Override
        public void visit(StringBuilder sb, Locale locale, MessageSource messageSource) {
            StringBuilder internal = new StringBuilder();
            for (Node node : children) {
                node.visit(internal, locale, messageSource);
            }

            String childTemplate = internal.toString();
            String key = extractKey(childTemplate);
            List<String> args = extractParameters(childTemplate);
            String text = messageSource.getMessage(key, args.toArray(), locale);

            sb.append(text);
        }
    }

    public static String translate(String template, Locale locale, MessageSource messageSource) {
        StringBuilder sb = new StringBuilder(template.length());

        AST ast = new AST();

        ParserState state = ParserState.START;
        int idx = 0;
        while (true) {
            Pair<ParserState, Integer> stateAndIdx = state.next(template, idx, ast);
            state = stateAndIdx.getKey();
            idx = stateAndIdx.getValue();
            if (state == ParserState.END) {
                break;
            }
        }

        ast.visit(sb, locale, messageSource);

        return sb.toString();
    }
}
