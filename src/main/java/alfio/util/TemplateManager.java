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

import alfio.manager.PurchaseContextFieldManager;
import alfio.manager.UploadedResourceManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.PurchaseContext;
import alfio.model.PurchaseContextFieldDescription;
import alfio.model.system.ConfigurationKeys;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Template;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static alfio.util.MustacheCustomTag.*;

/**
 * For hiding the ugliness :)
 * */
@Log4j2
public class TemplateManager {


    public static final String METADATA_ATTRIBUTES_KEY = "metadata-attributes";
    public static final String ADDITIONAL_FIELDS_KEY = "additional-fields";
    public static final String VAT_TRANSLATION_TEMPLATE_KEY = "vatTranslation";
    public static final String MAIL_FOOTER = "mailFooter";

    public enum TemplateOutput {
        TEXT, HTML
    }

    private final MessageSourceManager messageSourceManager;
    private final Map<TemplateOutput, Compiler> compilers;
    private final UploadedResourceManager uploadedResourceManager;
    private final ConfigurationManager configurationManager;
    private final PurchaseContextFieldManager purchaseContextFieldManager;


    public TemplateManager(MessageSourceManager messageSourceManager,
                           UploadedResourceManager uploadedResourceManager,
                           ConfigurationManager configurationManager,
                           PurchaseContextFieldManager purchaseContextFieldManager) {
        this.messageSourceManager = messageSourceManager;
        this.uploadedResourceManager = uploadedResourceManager;
        this.configurationManager = configurationManager;
        this.purchaseContextFieldManager = purchaseContextFieldManager;

        this.compilers = new EnumMap<>(TemplateOutput.class);
        this.compilers.put(TemplateOutput.TEXT, Mustache.compiler()
            .escapeHTML(false)
            .standardsMode(false)
            .defaultValue("")
            .nullValue("")
            .withFormatter(TemplateManager::dateFormatter));
        this.compilers.put(TemplateOutput.HTML, Mustache.compiler()
            .escapeHTML(true)
            .standardsMode(false)
            .defaultValue("")
            .nullValue("")
            .withFormatter(TemplateManager::dateFormatter));
    }

    private static String dateFormatter(Object o) {
        if(o instanceof ZonedDateTime) {
            return DateTimeFormatter.ISO_ZONED_DATE_TIME.format((ZonedDateTime) o);
        }
        return String.valueOf(o);
    }

    
    private RenderedTemplate renderMultipartTemplate(PurchaseContext purchaseContext, TemplateResource templateResource, Map<String, Object> model, Locale locale) {
    	var enrichedModel = modelEnricher(model, purchaseContext, locale);
        var options = configurationManager.getFor(EnumSet.of(ConfigurationKeys.MAIL_FOOTER, ConfigurationKeys.ENABLE_HTML_EMAILS), purchaseContext.getConfigurationLevel());
        var mailFooter = options.get(ConfigurationKeys.MAIL_FOOTER);
        enrichedModel.put("hasMailFooter", mailFooter.isPresent());
        enrichedModel.put(MAIL_FOOTER, mailFooter.getValueOrNull());
    	var isMultipart = templateResource.isMultipart();
    	
        var textRender = render(new ClassPathResource(templateResource.classPath()), enrichedModel, locale, purchaseContext, isMultipart ? TemplateOutput.TEXT : templateResource.getTemplateOutput());
        
        boolean htmlEnabled = options.get(ConfigurationKeys.ENABLE_HTML_EMAILS).getValueAsBooleanOrDefault();

        String htmlRender = null;

        if(isMultipart && htmlEnabled) {
            htmlRender = render(new ClassPathResource(templateResource.htmlClassPath()), enrichedModel, locale, purchaseContext, TemplateOutput.HTML);
        }

    	return RenderedTemplate.multipart(textRender, htmlRender, model);
    }

    public RenderedTemplate renderTemplate(PurchaseContext purchaseContext, TemplateResource templateResource, Map<String, Object> model, Locale locale) {
        Map<String, Object> updatedModel = modelEnricher(model, purchaseContext, locale);
        return uploadedResourceManager.findCascading(purchaseContext.getOrganizationId(), purchaseContext.event().map(Event::getId).orElse(null), templateResource.getSavedName(locale))
            .map(resource -> RenderedTemplate.plaintext(render(new ByteArrayResource(resource), updatedModel, locale, purchaseContext, templateResource.getTemplateOutput()), model))
            .orElseGet(() -> renderMultipartTemplate(purchaseContext, templateResource, updatedModel, locale));
    }

    public String renderString(PurchaseContext purchaseContext, String template, Map<String, Object> model, Locale locale, TemplateOutput templateOutput) {
        return render(new ByteArrayResource(template.getBytes(StandardCharsets.UTF_8)), modelEnricher(model, purchaseContext, locale), locale, purchaseContext, templateOutput);
    }

    public void renderHtml(Resource resource, Map<String, Object> model, OutputStream os) {
        try (var osw = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            compile(resource, TemplateOutput.HTML).execute(model, osw);
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    private Map<String, Object> modelEnricher(Map<String, Object> model, PurchaseContext purchaseContext, Locale locale) {
        Map<String, Object> toEnrich = new HashMap<>(model);
        if(!toEnrich.containsKey("purchaseContext")) {
            // this is necessary to support older model format
            toEnrich.put("purchaseContext", purchaseContext);
        }
        toEnrich.put(VAT_TRANSLATION_TEMPLATE_KEY, messageSourceManager.getMessageSourceFor(purchaseContext).getMessage("common.vat", null, locale));
        return toEnrich;
    }

    private String render(Resource resource, Map<String, Object> model, Locale locale, PurchaseContext purchaseContext, TemplateOutput templateOutput) {
        try {
            var messageSource = messageSourceManager.getMessageSourceFor(purchaseContext);
            var configuration = configurationManager.getFor(EnumSet.of(ConfigurationKeys.USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL, ConfigurationKeys.ENABLE_WALLET, ConfigurationKeys.ENABLE_PASS), ConfigurationLevel.purchaseContext(purchaseContext));
            boolean usePartnerCode = Objects.requireNonNull(configuration.get(ConfigurationKeys.USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL))
                .getValueAsBooleanOrDefault();
            Supplier<Map<String, String>> descriptionSupplier =
                () -> purchaseContextFieldManager.findDescriptions(purchaseContext).stream()
                    .filter(d -> d.getLocale().equals(locale.getLanguage()))
                    .collect(Collectors.toMap(PurchaseContextFieldDescription::getFieldName, d -> String.valueOf(d.getDescription().getOrDefault("label", d.getFieldName()))));
            ModelAndView mv = new ModelAndView();
            mv.getModelMap().addAllAttributes(model);
            mv.addObject("format-date", MustacheCustomTag.FORMAT_DATE);
            mv.addObject("country-name", COUNTRY_NAME);
            mv.addObject("render-markdown", RENDER_MARKDOWN);
            mv.addObject("additional-field-value", ADDITIONAL_FIELD_VALUE.apply(model.get(ADDITIONAL_FIELDS_KEY)));
            mv.addObject("print-additional-fields", MustacheCustomTag.PRINT_ADDITIONAL_FIELDS.apply(model.get(ADDITIONAL_FIELDS_KEY), descriptionSupplier));
            mv.addObject("metadata-value", ADDITIONAL_FIELD_VALUE.apply(model.get(METADATA_ATTRIBUTES_KEY)));
            mv.addObject("i18n", new CustomLocalizationMessageInterceptor(locale, messageSource).createTranslator());
            mv.addObject("discountCodeDescription", messageSource.getMessage("show-event.promo-code-type." + (usePartnerCode ? "partner" : "promotional"), null, locale));
            mv.addObject("subscriptionDescription", MustacheCustomTag.subscriptionDescriptionGenerator(messageSource, model, locale));
            var updatedModel = mv.getModel();
            updatedModel.putIfAbsent("custom-header-text", "");
            updatedModel.putIfAbsent("custom-body-text", "");
            updatedModel.putIfAbsent("custom-footer-text", "");
            boolean googleWalletEnabled = configuration.get(ConfigurationKeys.ENABLE_WALLET).getValueAsBooleanOrDefault();
            boolean appleWalletEnabled = configuration.get(ConfigurationKeys.ENABLE_PASS).getValueAsBooleanOrDefault();
            updatedModel.putIfAbsent("googleWalletEnabled", googleWalletEnabled);
            updatedModel.putIfAbsent("appleWalletEnabled", appleWalletEnabled);
            updatedModel.putIfAbsent("walletEnabled", googleWalletEnabled || appleWalletEnabled);
            return compile(resource, templateOutput).execute(mv.getModel());
        } catch (Exception e) {
            log.error("TemplateManager: got exception while generating a template", e);
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

    private static final Pattern KEY_PATTERN = Pattern.compile("^([^\\[]+)[\\s\\[]");
    private static final Pattern ARGS_PATTERN = Pattern.compile("\\[(.*?)]");

    /**
     * Split key from (optional) arguments.
     *
     * @param key key
     * @return localization key
     */
    private static String extractKey(String key) {
        Matcher matcher = KEY_PATTERN.matcher(key);
        if (matcher.find()) {
            return matcher.group(1).strip();
        }

        return key.strip();
    }

    /**
     * Split args from input string.
     * <p/>
     * localization_key [param1] [param2] [param3]
     *
     * @param key key
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
