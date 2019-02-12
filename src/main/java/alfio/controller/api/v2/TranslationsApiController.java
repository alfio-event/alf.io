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
package alfio.controller.api.v2;

import alfio.util.CustomResourceBundleMessageSource;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v2/")
public class TranslationsApiController {

    private final CustomResourceBundleMessageSource messageSource;
    private static String[] EMPTY_ARRAY = new String[]{};

    @GetMapping("/public/i18n/{lang}")
    public Map<String, String> getPublicTranslations(@PathVariable("lang") String lang) {
        return getBundleAsMap("alfio.i18n.public", lang);
    }

    @GetMapping("/admin/i18n/{lang}")
    public Map<String, String> getAdminTranslations(@PathVariable("lang") String lang) {
        return getBundleAsMap("alfio.i18n.admin", lang);
    }

    private Map<String, String> getBundleAsMap(String baseName, String lang) {
        var locale = new Locale(lang);
        return messageSource.getKeys(baseName, locale)
            .stream()
            .collect(Collectors.toMap(Function.identity(), k -> messageSource.getMessage(k, EMPTY_ARRAY, locale)
                //replace all placeholder {0} -> {{0}} so it can be consumed by ngx-translate
                .replaceAll("\\{(\\d+)\\}", "{{$1}}")));
    }
}
