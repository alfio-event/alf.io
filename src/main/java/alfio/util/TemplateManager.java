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

import alfio.manager.UploadedResourceManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.model.EventAndOrganizationId;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Mustache.Formatter;
import com.samskivert.mustache.Template;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.ModelAndView;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static alfio.util.MustacheCustomTag.ADDITIONAL_FIELD_VALUE;
import static alfio.util.MustacheCustomTag.COUNTRY_NAME;

/**
 * For hiding the ugliness :)
 * */
public class TemplateManager {


    private final MessageSourceManager messageSourceManager;

    public static final String VAT_TRANSLATION_TEMPLATE_KEY = "vatTranslation";

    public enum TemplateOutput {
        TEXT, HTML
    }

    private final Map<TemplateOutput, Compiler> compilers;

    private final UploadedResourceManager uploadedResourceManager;

    private static final Formatter DATE_FORMATTER = o -> (o instanceof ZonedDateTime) ? DateTimeFormatter.ISO_ZONED_DATE_TIME.format((ZonedDateTime) o) : String.valueOf(o);

    public TemplateManager(MessageSourceManager messageSourceManager,
                           UploadedResourceManager uploadedResourceManager) {
        this.messageSourceManager = messageSourceManager;
        this.uploadedResourceManager = uploadedResourceManager;

        this.compilers = new EnumMap<>(TemplateOutput.class);
        this.compilers.put(TemplateOutput.TEXT, Mustache.compiler()
            .escapeHTML(false)
            .standardsMode(false)
            .defaultValue("")
            .nullValue("")
            .withFormatter(DATE_FORMATTER));
        this.compilers.put(TemplateOutput.HTML, Mustache.compiler()
            .escapeHTML(true)
            .standardsMode(false)
            .defaultValue("")
            .nullValue("")
            .withFormatter(DATE_FORMATTER));
    }

    private String renderTemplate(Optional<? extends EventAndOrganizationId> event, TemplateResource templateResource, Map<String, Object> model, Locale locale) {
        return render(new ClassPathResource(templateResource.classPath()), modelEnricher(model, event, locale), locale, event.orElse(null), templateResource.getTemplateOutput());
    }

    public String renderTemplate(EventAndOrganizationId event, TemplateResource templateResource, Map<String, Object> model, Locale locale) {
        Map<String, Object> updatedModel = modelEnricher(model, Optional.of(event), locale);
        return uploadedResourceManager.findCascading(event.getOrganizationId(), event.getId(), templateResource.getSavedName(locale))
            .map(resource -> render(new ByteArrayResource(resource), updatedModel, locale, event, templateResource.getTemplateOutput()))
            .orElseGet(() -> renderTemplate(Optional.of(event), templateResource, updatedModel, locale));
    }

    public String renderString(EventAndOrganizationId event, String template, Map<String, Object> model, Locale locale, TemplateOutput templateOutput) {
        return render(new ByteArrayResource(template.getBytes(StandardCharsets.UTF_8)), modelEnricher(model, Optional.ofNullable(event), locale), locale, event, templateOutput);
    }

    public void renderHtml(Resource resource, Map<String, Object> model, OutputStream os) {
        try (var osw = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            compile(resource, TemplateOutput.HTML).execute(model, osw);
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    private Map<String, Object> modelEnricher(Map<String, Object> model, Optional<? extends EventAndOrganizationId> event, Locale locale) {
        Map<String, Object> toEnrich = new HashMap<>(model);
        event.ifPresent(ev -> toEnrich.put(VAT_TRANSLATION_TEMPLATE_KEY, messageSourceManager.getMessageSourceForEvent(ev).getMessage("common.vat", null, locale)));
        return toEnrich;
    }

    private String render(Resource resource, Map<String, Object> model, Locale locale, EventAndOrganizationId eventAndOrganizationId, TemplateOutput templateOutput) {
        try {
            ModelAndView mv = new ModelAndView((String) null, model);
            mv.addObject("format-date", MustacheCustomTag.FORMAT_DATE);
            mv.addObject("country-name", COUNTRY_NAME);
            mv.addObject("additional-field-value", ADDITIONAL_FIELD_VALUE.apply(model.get("additional-fields")));
            mv.addObject("i18n", new CustomLocalizationMessageInterceptor(locale, messageSourceManager.getMessageSourceForEvent(eventAndOrganizationId)).createTranslator());
            return compile(resource, templateOutput).execute(mv.getModel());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Template compile(Resource resource, TemplateOutput templateOutput) {
        try (InputStreamReader tmpl = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return compilers.get(templateOutput).compile(tmpl);
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    private static final Pattern KEY_PATTERN = Pattern.compile("(.*?)[\\s\\[]");
    private static final Pattern ARGS_PATTERN = Pattern.compile("\\[(.*?)]");

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
                    ast.addI18NNode();
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
                    ast.addI18NNode();
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

        void addI18NNode() {
            addChild(new I18NNode());
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
        do {
            Pair<ParserState, Integer> stateAndIdx = state.next(template, idx, ast);
            state = stateAndIdx.getKey();
            idx = stateAndIdx.getValue();
        } while (state != ParserState.END);

        ast.visit(sb, locale, messageSource);

        return sb.toString();
    }
}
