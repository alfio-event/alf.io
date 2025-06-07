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
package alfio.model.api.v1.admin;

import org.apache.commons.lang3.StringUtils;

public class DescriptionRequest {
    private final String lang;
    private final String body;

    public DescriptionRequest(String lang, String body) {
        this.lang = lang;
        this.body = body;
    }

    public String getLang() {
        return lang;
    }

    public String getBody() {
        return body;
    }

    public boolean isValid() {
        return StringUtils.isNotEmpty(lang)
            && StringUtils.isNotEmpty(body);
    }
}
