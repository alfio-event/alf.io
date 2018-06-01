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
import alfio.model.transaction.PaymentProxy;
import com.samskivert.mustache.Mustache;
import org.apache.commons.lang3.tuple.Pair;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
public class MustacheCustomTagInterceptor extends HandlerInterceptorAdapter {

    private static final String LOCALE_LABEL = "locale:";

    public static final Mustache.Lambda FORMAT_DATE = (frag, out) -> {
        String execution = frag.execute().trim();
        ZonedDateTime d = ZonedDateTime.parse(substring(execution, 0, execution.indexOf(" ")));
        Pair<String, Optional<Locale>> p = parseParams(execution);
        if (p.getRight().isPresent()) {
            out.write(DateTimeFormatter.ofPattern(p.getLeft(), p.getRight().get()).format(d));
        } else {
            out.write(DateTimeFormatter.ofPattern(p.getLeft()).format(d));
        }
    };

    public static final Mustache.Lambda COUNTRY_NAME = (frag, out) -> {
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

    private static Pair<String, Optional<Locale>> parseParams(String r) {

        int indexLocale = r.indexOf(LOCALE_LABEL), end = Math.min(r.length(),
                indexLocale != -1 ? indexLocale : r.length());
        String format = substring(r, r.indexOf(" "), end);

        //
        String[] res = r.split("\\s+");
        Optional<Locale> locale = Arrays.asList(res).stream().filter((s) -> s.startsWith(LOCALE_LABEL)).findFirst()
                .map((l) -> {
                    return Locale.forLanguageTag(substring(l, LOCALE_LABEL.length()));
                });
        //

        return Pair.of(format, locale);
    }

    private static final Pattern ARG_PATTERN = Pattern.compile("\\[(.*?)\\]");

    private static final Function<ModelAndView, Mustache.Lambda> HAS_ERROR = (mv) -> {
        return (frag, out) -> {
            Errors err = (Errors) mv.getModelMap().get("error");
            String execution = frag.execute().trim();
            Matcher matcher = ARG_PATTERN.matcher(execution);
            if (matcher.find()) {
                String field = matcher.group(1);
                if (err != null && err.hasFieldErrors(field)) {
                    out.write(execution.substring(matcher.end(1) + 1));
                }
            }
        };
    };

    private static final Mustache.Lambda IS_PAYMENT_METHOD = (frag, out) -> {
        String execution = frag.execute().trim();
        Matcher matcher = ARG_PATTERN.matcher(execution);
        if(matcher.find()) {
            String[] values = matcher.group(1).split(",");
            Optional<PaymentProxy> first = PaymentProxy.safeValueOf(values[0]);
            Optional<PaymentProxy> second = PaymentProxy.safeValueOf(values[1]);
            if(first.isPresent() && second.isPresent() && first.get().equals(second.get())) {
                out.write(execution.substring(matcher.end(1) + 1));
            }
        }
    };

    private static final Function<ModelAndView, Mustache.Lambda> FIELD_ERROR = (mv) -> {
        return (frag, out) -> {
            Errors err = (Errors) mv.getModelMap().get("error");
            String field = frag.execute().trim();
            if (err != null && err.hasFieldErrors(field)) {
                FieldError fe = err.getFieldError(field);
                out.write(fe.getCode()
                        + " "
                        + Arrays.stream(Optional.ofNullable(fe.getArguments()).orElse(new Object[] {}))
                                .map(x -> "[" + x.toString() + "]").collect(Collectors.joining(" ")));
            } else {
                out.write("empty");
            }
        };
    };
    
    
    private static final Parser COMMONMARK_PARSER = Parser.builder().build();
    private static final HtmlRenderer COMMONMARK_RENDERER = HtmlRenderer.builder().build();
    
    public static String renderToCommonmark(String input) {
    	Node document = COMMONMARK_PARSER.parse(input);
    	return COMMONMARK_RENDERER.render(document);
    }
    
    private static final Mustache.Lambda RENDER_TO_COMMON_MARK = (frag, out) -> {
    	String content = frag.execute();
    	out.write(renderToCommonmark(content));
    	
    };

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
            ModelAndView modelAndView) throws Exception {

        if (modelAndView != null) {
            modelAndView.addObject("format-date", FORMAT_DATE);
            modelAndView.addObject("field-has-error", HAS_ERROR.apply(modelAndView));
            modelAndView.addObject("field-error", FIELD_ERROR.apply(modelAndView));
            modelAndView.addObject("is-payment-method", IS_PAYMENT_METHOD);
            modelAndView.addObject("commonmark", RENDER_TO_COMMON_MARK);
            modelAndView.addObject("country-name", COUNTRY_NAME);
        }

        super.postHandle(request, response, handler, modelAndView);
    }
}
