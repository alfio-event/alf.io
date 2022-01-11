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


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateManagerTest {

    private static final StaticMessageSource messageSource = new StaticMessageSource();
    static {
        messageSource.addMessage("locale", Locale.ENGLISH, "en");
        messageSource.addMessage("middle", Locale.ENGLISH, "middle-en");

        messageSource.addMessage("middle1", Locale.ENGLISH, "middle-1-resolved");
        messageSource.addMessage("middle2", Locale.ENGLISH, "middle-2-resolved");

        messageSource.addMessage("nested", Locale.ENGLISH, "nested-1");
        messageSource.addMessage("nested-1", Locale.ENGLISH, "nested-resolved");
        messageSource.addMessage("a", Locale.ENGLISH, "a-resolved");
        messageSource.addMessage("b", Locale.ENGLISH, "b-resolved");
        messageSource.addMessage("1a-resolved-middle-b-resolved2", Locale.ENGLISH, "complete-resolved");

        messageSource.addMessage("parameter", Locale.ENGLISH, "{2}-{1}-{0}");
    }

    @Test
    void parseEmptyString() {
        assertEquals("", TemplateManager.translate("", Locale.ENGLISH, messageSource));
    }

    @Test
    void parseString() {
        assertEquals("test", TemplateManager.translate("test", Locale.ENGLISH, messageSource));
    }

    @Test
    void parseOnlyI18N() {
        assertEquals("en", TemplateManager.translate("{{#i18n}}locale{{/i18n}}", Locale.ENGLISH, messageSource));
    }

    @Test
    void parseMixedI18N() {
        assertEquals("before middle-en after", TemplateManager.translate("before {{#i18n}}middle{{/i18n}} after", Locale.ENGLISH, messageSource));
    }

    @Test
    void parseMultipleMixedI18N() {
        assertEquals("before1middle-1-resolvedafter1before2middle-2-resolvedafter2", TemplateManager.translate("before1{{#i18n}}middle1{{/i18n}}after1before2{{#i18n}}middle2{{/i18n}}after2", Locale.ENGLISH, messageSource));
    }

    @Test
    void parseNestedI18N() {
        assertEquals("nested-resolved", TemplateManager.translate("{{#i18n}}{{#i18n}}nested{{/i18n}}{{/i18n}}", Locale.ENGLISH, messageSource));
    }

    @Test
    void parseNested2I18N() {
        assertEquals("0complete-resolved3", TemplateManager.translate("0{{#i18n}}1{{#i18n}}a{{/i18n}}-middle-{{#i18n}}b{{/i18n}}2{{/i18n}}3", Locale.ENGLISH, messageSource));
    }

    @Test
    void simpleParams() {
        assertEquals("3-2-1", TemplateManager.translate("{{#i18n}}parameter [1] [2] [3]{{/i18n}}", Locale.ENGLISH, messageSource));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{{#i18n}}parameter[1][2][3]{{/i18n}}",
        "{{#i18n}} parameter[1][2][3]{{/i18n}}",
        "{{#i18n}}parameter [1][2][3]{{/i18n}}",
        "{{#i18n}}parameter [1] [2][3]{{/i18n}}",
        "{{#i18n}}parameter [1] [2] [3]{{/i18n}}",
        "{{#i18n}}parameter [1] [2] [3] {{/i18n}}",
        "{{#i18n}} parameter [1] [2] [3] {{/i18n}}",
    })
    void simpleParams(String input) {
        assertEquals("3-2-1", TemplateManager.translate(input, Locale.ENGLISH, messageSource));
    }
}