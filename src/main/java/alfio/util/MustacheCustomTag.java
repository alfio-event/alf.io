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

import alfio.controller.api.support.TicketHelper;
import com.samskivert.mustache.Mustache;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.security.web.util.UrlUtils;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.substring;

/**
 * For formatting date in a mustache template.
 *
 * <p>
 * Syntax is: {{#format-date}}{{yourDate}} FORMAT locale:YOUR_LOCALE{{/format-date}}.
 * </p>
 * <p>
 * Where
 * </p>
 * <ul>
 * <li>yourDate has been formatted following the java.time.ZonedDateTime
 * <li>FORMAT is a format understood by {@link DateTimeFormatter}</li>
 * <li>optional: locale:YOUR_LOCALE you can define the locale</li>
 * </ul>
 */
@Log4j2
public class MustacheCustomTag {

    private MustacheCustomTag() {}

    private static final Pattern ARG_PATTERN = Pattern.compile("\\[(.*?)]");
    private static final String LOCALE_LABEL = "locale:";

    static final Mustache.Lambda FORMAT_DATE = (frag, out) -> {
        String execution = frag.execute().trim();
        ZonedDateTime d = ZonedDateTime.parse(substring(execution, 0, execution.indexOf(' ')));
        Pair<String, Optional<Locale>> p = parseParams(execution);
        if (p.getRight().isPresent()) {
            out.write(DateTimeFormatter.ofPattern(p.getLeft(), p.getRight().get()).format(d));
        } else {
            out.write(DateTimeFormatter.ofPattern(p.getLeft()).format(d));
        }
    };

    /**
     * {{#render-markdown}}[markdown][.html|.text]{{/render-markdown}}
     * The string must end with either .html or .text, otherwise Markdown won't be parsed
     * e.g.
     * {{#render-markdown}}(link)[description].html{{/render-markdown}} will produce HTML output
     * {{#render-markdown}}(link)[description].text{{/render-markdown}} will produce text/plain output
     */
    static final Mustache.Lambda RENDER_MARKDOWN = (frag, out) -> {
        String execution = frag.execute().strip();
        if(execution.endsWith(".html")) {
            out.write(renderToHtmlCommonmarkEscaped(StringUtils.removeEnd(execution, ".html")));
        } else if(execution.endsWith(".text")) {
            out.write(renderToTextCommonmark(StringUtils.removeEnd(execution, ".text")));
        } else {
            out.write(execution);
        }
    };

    static final Mustache.Lambda COUNTRY_NAME = (frag, out) -> {
        String execution = frag.execute().trim();
        String code = substring(execution, 0, 2);
        Pair<String, Optional<Locale>> p = parseParams(execution);
        out.write(translateCountryCode(code, p.getRight().orElse(null)));
    };

    static String translateCountryCode(String code, Locale locale) {
        Locale lang = locale != null ? locale : Locale.ENGLISH;
        return Stream.concat(TicketHelper.getLocalizedCountries(lang).stream(), TicketHelper.getLocalizedCountriesForVat(lang).stream())
            .filter(p -> p.getKey().equalsIgnoreCase(code))
            .map(Pair::getValue)
            .findFirst()
            .orElse(code);
    }

    /**
     * {{#additional-field-value}}[Prefix][name][suffix]{{/additional-field-value}}
     * prefix is optional, unless a suffix is needed.
     */
    static final Function<Object, Mustache.Lambda> ADDITIONAL_FIELD_VALUE = obj -> (frag, out) -> {
        if( !(obj instanceof Map) || ((Map<?,?>)obj).isEmpty()) {
            log.warn("map not found or empty. Skipping additionalFieldValue tag");
            return;
        }
        Map<?, ?> fieldNamesAndValues = (Map<?, ?>) obj;
        String execution = frag.execute().trim();
        Matcher matcher = ARG_PATTERN.matcher(execution);
        List<String> args = new ArrayList<>();
        while(matcher.find()) {
            args.add(matcher.group(1));
        }
        if(args.isEmpty()) {
            return;
        }
        String name = args.get(args.size() > 1 ? 1 : 0);
        String prefix = args.size() > 1 ? args.get(0) + " " : "";
        String suffix = args.size() > 2 ? " "+args.get(2) : "";

        if(fieldNamesAndValues.containsKey(name)) {
            out.write(prefix + fieldNamesAndValues.get(name) + suffix);
        }
    };

    private static Pair<String, Optional<Locale>> parseParams(String r) {

        int indexLocale = r.indexOf(LOCALE_LABEL);
        int end = Math.min(r.length(), indexLocale != -1 ? indexLocale : r.length());
        String format = substring(r, r.indexOf(' '), end);

        //
        String[] res = r.split("\\s+");
        Optional<Locale> locale = Arrays.stream(res).filter(s -> s.startsWith(LOCALE_LABEL)).findFirst()
                .map(l -> LocaleUtil.forLanguageTag(substring(l, LOCALE_LABEL.length())));
        //

        return Pair.of(format, locale);
    }


    private static final List<Extension> COMMONMARK_EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser COMMONMARK_PARSER = Parser.builder().extensions(COMMONMARK_EXTENSIONS).build();
    private static final HtmlRenderer COMMONMARK_RENDERER = HtmlRenderer.builder().extensions(COMMONMARK_EXTENSIONS).attributeProviderFactory((ctx) -> new TargetBlankProvider()).build();
    private static final TextContentRenderer COMMONMARK_TEXT_RENDERER = TextContentRenderer.builder().extensions(COMMONMARK_EXTENSIONS).build();
    private static final ThreadLocal<String> A11Y_NEW_TAB_LABEL = new ThreadLocal<>();

    //Open in a new window if the link contains an absolute url
    private static class TargetBlankProvider implements AttributeProvider {
        @Override
        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
            if (node instanceof Link) {
                Link l = (Link) node;
                String destination = StringUtils.trimToEmpty(l.getDestination());
                var scheme = getScheme(destination);
                scheme.ifPresent(resolvedScheme -> {
                    if (!Set.of("http", "https").contains(resolvedScheme)) {
                        log.info("User tried to set an url with scheme {}, only http/https are accepted, href has been removed", resolvedScheme);
                        attributes.remove("href");
                    }
                });
                if (UrlUtils.isAbsoluteUrl(destination)) {
                    // accept only http or https protocols if we have an absolute link, else we override with an empty string
                    attributes.put("target", "_blank");
                    attributes.put("rel", "nofollow noopener noreferrer");
                    var newTabLabel = A11Y_NEW_TAB_LABEL.get();
                    if (newTabLabel != null) {
                        attributes.put("aria-label", ((Text)node.getFirstChild()).getLiteral() + " " + newTabLabel);
                    }
                }
            }
        }
    }
    public static String renderToHtmlCommonmarkEscaped(String input) {
        return renderToHtmlCommonmarkEscaped(input, null);
    }

    /**
     * return lowercase scheme if present
     */
    private static Optional<String> getScheme(String uri) {
        var s = StringUtils.trimToEmpty(uri).toLowerCase(Locale.ROOT);
        return s.indexOf(':') >= 0 ? Optional.of(StringUtils.substringBefore(s, ':')) : Optional.empty();
    }

    public static String renderToHtmlCommonmarkEscaped(String input, String localizedNewWindowLabel) {
        try {
            A11Y_NEW_TAB_LABEL.set(localizedNewWindowLabel);
            Node document = COMMONMARK_PARSER.parse(StringEscapeUtils.escapeHtml4(input));
            return COMMONMARK_RENDERER.render(document);
        } finally {
            A11Y_NEW_TAB_LABEL.remove();
        }
    }

    public static String renderToTextCommonmark(String input) {
        Node document = COMMONMARK_PARSER.parse(input);
        return COMMONMARK_TEXT_RENDERER.render(document);
    }
}
