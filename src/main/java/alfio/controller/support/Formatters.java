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
package alfio.controller.support;

import alfio.model.ContentLanguage;
import alfio.model.Event;
import alfio.model.LocalizedContent;
import alfio.util.MustacheCustomTag;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;

@UtilityClass
@Log4j2
public class Formatters {

    public static final String LINK_NEW_TAB_KEY = "link.new-tab";

    public static Map<String, String> getFormattedDate(LocalizedContent localizedContent, ZonedDateTime date, String code, MessageSource messageSource) {
        if(localizedContent != null && date != null) {
            return getFormattedDate(localizedContent.getContentLanguages(), date, code, messageSource);
        }
        return null;
    }

    private static Map<String, String> getFormattedDate(List<ContentLanguage> languages, ZonedDateTime date, String code, MessageSource messageSource) {
        Map<String, String> formatted = new HashMap<>();
        languages.forEach(cl -> formatDateForLocale(date, code, messageSource, formatted::put, cl, false));
        return formatted;
    }

    static void formatDateForLocale(ZonedDateTime date,
                                    String code,
                                    MessageSource messageSource,
                                    BiConsumer<String, String> storeFunction,
                                    ContentLanguage cl,
                                    boolean notifyError) {
        String pattern = null;
        try {
            pattern = messageSource.getMessage(code, null, cl.getLocale());
            storeFunction.accept(cl.getLanguage(), DateTimeFormatter.ofPattern(pattern, cl.getLocale()).format(date));
        } catch (RuntimeException e) {
            String message = "cannot parse pattern "+code+" ("+pattern+") for language "+ cl.getLanguage();
            if(notifyError) {
                throw new RuntimeException(message, e);
            } else {
                log.warn(message, e);
            }
        }
    }

    public static FormattedEventDates getFormattedDates(Event e, MessageSource messageSource, List<ContentLanguage> contentLanguages) {
        return new FormattedEventDates(
            getFormattedDate(contentLanguages, e.getBegin(), "common.event.date-format", messageSource),
            getFormattedDate(contentLanguages, e.getBegin(), "common.event.time-format", messageSource),
            getFormattedDate(contentLanguages, e.getEnd(), "common.event.date-format", messageSource),
            getFormattedDate(contentLanguages, e.getEnd(), "common.event.time-format", messageSource)
        );
    }

    public static Map<String, String> applyCommonMark(Map<String, String> in) {
        return applyCommonMark(in, null);
    }

    public static Map<String, String> applyCommonMark(Map<String, String> in, MessageSource messageSource) {
        if (in == null) {
            return Collections.emptyMap();
        }

        var res = new HashMap<String, String>();
        in.forEach((k, v) -> {
            var targetBlankMessage = messageSource != null ? messageSource.getMessage(LINK_NEW_TAB_KEY, null, Locale.forLanguageTag(k)) : null;
            res.put(k, MustacheCustomTag.renderToHtmlCommonmarkEscaped(v, targetBlankMessage));
        });
        return res;
    }
}
