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


import org.junit.Test;

public class TemplateManagerTest {

    @Test
    public void parseEmptyString() {
        TemplateManager.translate("");
    }

    @Test
    public void parseString() {
        TemplateManager.translate("test");
    }

    @Test
    public void parseOnlyI18N() {
        TemplateManager.translate("{{#i18n}}{{/i18n}}");
    }

    @Test
    public void parseMixedI18N() {
        TemplateManager.translate("before{{#i18n}}middle{{/i18n}}after");
    }

    @Test
    public void parseMultipleMixedI18N() {
        TemplateManager.translate("before1{{#i18n}}middle1{{/i18n}}after1 before2{{#i18n}}middle2{{/i18n}}after2");
    }


    @Test
    public void parseNestedI18N() {
        TemplateManager.translate("{{#i18n}}{{#i18n}}{{/i18n}}{{/i18n}}");
    }

    @Test
    public void parseNested2I18N() {
        TemplateManager.translate("0{{#i18n}}1{{#i18n}}a{{/i18n}}-middle-{{#i18n}}b{{/i18n}}2{{/i18n}}3");
    }
}