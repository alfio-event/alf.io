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
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.Map;

public final class JSON {

    private JSON() {
    }

    private static final Logger log = LoggerFactory.getLogger(JSON.class);

    private static final Type PARSE_RETURN_TYPE = new TypeToken<Map<String, Object>>(){}.getType();

    public static String stringify(Object o) {
        return ExtensionUtils.convertToJson(o);
    }

    public static String stringify(Object o, Object replacer) {
        log.warn("JSON.stringify: ignoring replacer param");
        return stringify(o);
    }

    public static String stringify(Object o, Object replacer, Object space) {
        log.warn("JSON.stringify: ignoring replacer and space params");
        return stringify(o);
    }

    public static Map<String, ?> parse(String s) {
        try {
            return Json.GSON.fromJson(s, PARSE_RETURN_TYPE);
        } catch(Exception e) {
            log.error("malformed JSON", e);
            return null;
        }
    }

    public static Map<String, ?> parse(String s, Object reviver) {
        log.warn("JSON.parse: Ignoring reviver param");
        return parse(s);
    }
}
