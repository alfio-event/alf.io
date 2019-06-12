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

import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Custom resource bundle that expose all the defined keys
 *
 */
public class CustomResourceBundleMessageSource extends ResourceBundleMessageSource {
    public Set<String> getKeys(String basename, Locale locale) {
        ResourceBundle bundle = getResourceBundle(basename, locale);
        return bundle.keySet();
    }
}
