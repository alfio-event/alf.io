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

import lombok.Getter;

import java.util.Map;

@Getter
public class RenderedTemplate {
    private final String textPart;
    private final String htmlPart;
    private final Map<String, Object> srcModel;

    private RenderedTemplate(String textPart, String htmlPart, Map<String, Object> model) {
        this.textPart = textPart;
        this.htmlPart = htmlPart;
        this.srcModel = model;
    }

    public boolean isMultipart() {
        return htmlPart != null;
    }

    public static RenderedTemplate plaintext(String textPart, Map<String, Object> model) {
        return new RenderedTemplate(textPart, null, model);
    }

    public static RenderedTemplate multipart(String textPart, String htmlPart, Map<String, Object> model) {
        return new RenderedTemplate(textPart, htmlPart, model);
    }
}
