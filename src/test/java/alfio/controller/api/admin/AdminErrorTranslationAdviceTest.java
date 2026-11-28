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
package alfio.controller.api.admin;

import alfio.manager.i18n.MessageSourceManager;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.result.ValidationResult.ErrorDescriptor;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.CustomResourceBundleMessageSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminErrorTranslationAdviceTest {

    private AdminErrorTranslationAdvice advice;
    private ConfigurationRepository configurationRepository;

    @BeforeEach
    void setUp() {
        var source = new CustomResourceBundleMessageSource();
        source.setBasenames("alfio.i18n.public");
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setFallbackToSystemLocale(false);
        source.setAlwaysUseMessageFormat(true);
        configurationRepository = mock(ConfigurationRepository.class);
        advice = new AdminErrorTranslationAdvice(new MessageSourceManager(source, configurationRepository));
    }

    @Test
    void translatesValidationErrorsByTheirCode() {
        var result = Result.<String>error(new ErrorDescriptor("shortName", "", "error.shortname"));

        var error = advice.translate(result, Locale.GERMAN).getFirstErrorOrNull();

        assertEquals("Name der Veranstaltung ist ungültig", error.getDescription());
        assertEquals("error.shortname", error.getCode(), "the code must not change: the clients check it");
        assertEquals("shortName", error.getLocation());
    }

    @Test
    void fillsThePlaceholders() {
        var result = Result.<String>error(ErrorCode.translatable("error.promoCode.maxUsage", "admin.error.promo-code.max-usage",
            "The discount code SUMMER can be used for 2 more tickets", "SUMMER", 2));

        var error = advice.translate(result, Locale.ENGLISH).getFirstErrorOrNull();

        assertEquals("The discount code SUMMER can be used for 2 more tickets", error.getDescription());
    }

    @Test
    void fallsBackToEnglishWhenTheLanguageHasNoTranslation() {
        var result = Result.<String>error(ErrorCode.ReservationError.NOT_FOUND);

        assertEquals("No reservation has been found", advice.translate(result, Locale.GERMAN).getFirstErrorOrNull().getDescription());
    }

    @Test
    void keepsTheDescriptionOfErrorsWithoutKey() {
        var result = Result.<String>error(List.of(
            ErrorCode.custom("internal_server_error", "Can't connect to 'the' server {0}"),
            new ErrorDescriptor("name", "a description", "unknown.code.without.translation")
        ));

        var errors = advice.translate(result, Locale.ENGLISH).getErrors();

        assertEquals("Can't connect to 'the' server {0}", errors.get(0).getDescription());
        assertEquals("a description", errors.get(1).getDescription());
    }

    @Test
    void ignoresTheOverridesOfTheOrganizers() {
        advice.translate(Result.<String>error(ErrorCode.EventError.NOT_FOUND), Locale.ENGLISH);

        verifyNoInteractions(configurationRepository);
    }
}
