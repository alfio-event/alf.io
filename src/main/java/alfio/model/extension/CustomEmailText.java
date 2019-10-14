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
package alfio.model.extension;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class CustomEmailText {
    private String header;
    private String body;
    private String footer;

    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        addIfNotBlank(header, "custom-header-text", result);
        addIfNotBlank(body, "custom-body-text", result);
        addIfNotBlank(footer, "custom-footer-text", result);
        return result;
    }

    private static void addIfNotBlank(String in, String key, Map<String, Object> map) {
        if(in != null && !in.isBlank()) {
            map.put(key, in);
        }
    }
}
