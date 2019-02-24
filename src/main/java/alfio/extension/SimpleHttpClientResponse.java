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
package alfio.extension;

import alfio.util.Json;
import com.google.gson.JsonSyntaxException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class SimpleHttpClientResponse {
    private final boolean successful;
    private final int code;
    private final String message;
    private final Map<String, List<String>> headers;
    private final String body;


    public Object getJsonBody() {
        return tryParse(body, Object.class);
    }

    private static Object tryParse(String body, Class<?> clazz) {
        try {
            return Json.GSON.fromJson(body, clazz);
        } catch (JsonSyntaxException jse) {
            return null;
        }
    }
}
