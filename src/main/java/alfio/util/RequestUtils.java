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

import alfio.model.ContentLanguage;
import alfio.model.Event;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.IteratorUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.context.request.ServletWebRequest;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@Log4j2
@UtilityClass
public class RequestUtils {

    public static Optional<String> readRequest(HttpServletRequest request) {
        try (ServletInputStream is = request.getInputStream()){
            return Optional.ofNullable(is.readAllBytes()).map(b -> new String(b, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("exception during request conversion", e);
            return Optional.empty();
        }
    }

    private static final Pattern SOCIAL_MEDIA_UA = Pattern.compile("facebookexternalhit/|XING-contenttabreceiver/|LinkedInBot/|Twitterbot/|WhatsApp|Slackbot|TelegramBot");

    public static boolean isSocialMediaShareUA(String ua) {
        return ua != null && SOCIAL_MEDIA_UA.matcher(ua).find();
    }


    public static Locale getMatchingLocale(ServletWebRequest request, List<String> allowedLanguages) {
        var l = requireNonNull(request.getNativeRequest(HttpServletRequest.class)).getLocales();
        List<Locale> locales = l != null ? IteratorUtils.toList(l.asIterator()) : Collections.emptyList();
        var selectedLocale = locales.stream().map(Locale::getLanguage).filter(allowedLanguages::contains).findFirst()
            .orElseGet(() -> allowedLanguages.stream().findFirst().orElseThrow());
        return LocaleUtil.forLanguageTag(selectedLocale);
    }

    /**
     * From a given request, return the best locale for the user
     *
     * @param request
     * @param event
     * @return
     */
    public static Locale getMatchingLocale(ServletWebRequest request, Event event) {
        var allowedLanguages = event.getContentLanguages().stream().map(ContentLanguage::getLanguage).collect(Collectors.toList());
        return getMatchingLocale(request, allowedLanguages);
    }

    public static boolean isAdmin(Principal principal) {
        if (principal instanceof Authentication) {
            return hasRole((Authentication) principal, "ROLE_ADMIN");
        }
        return false;
    }

    private static boolean hasRole(Authentication principal, String role) {
        return principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(role::equals);
    }

    public static boolean isSystemApiKey(Principal principal) {
        if (principal instanceof Authentication) {
            return hasRole((Authentication)principal, "ROLE_SYSTEM_API_CLIENT");
        }
        return false;
    }
}
