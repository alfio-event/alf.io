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
import com.google.gson.*;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import org.mozilla.javascript.*;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@UtilityClass
public class ExtensionUtils {

    private static final Gson JSON_SERIALIZER = Json.GSON.newBuilder()
        .registerTypeAdapter(Double.class, new DoubleSerializer())
        .create();

    public static String format(String str, String... params) {
        return String.format(str, (Object[]) params);
    }

    public static String md5(String str) {
        try {
            return Hex.encodeHexString(MessageDigest.getInstance("MD5").digest(str.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException nae) {
            throw new IllegalStateException(nae);
        }
    }

    public static String computeHMAC(String secret, String... parts) {
        if(parts == null || parts.length == 0) {
            return "";
        }
        var text = Arrays.stream(parts).map(StringUtils::trimToEmpty).collect(Collectors.joining(""));
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secret).hmacHex(text);
    }

    /**
     * This method overload exists to allow usage of formatDateTime when retrying a failed execution.
     * In this particular context, ZoneDateTime(s) are serialized as String(s), so we need to deserialize the value
     * before formatting it.
     */
    public static String formatDateTime(String dateTimeAsString, String formatPattern, boolean utc) {
        return formatDateTime(ZonedDateTime.parse(dateTimeAsString), formatPattern, utc);
    }

    public static String formatDateTime(ZonedDateTime dateTime, String formatPattern, boolean utc) {
        var dateTimeToFormat = utc ? dateTime.withZoneSameInstant(ZoneId.of("UTC")) : dateTime;
        return dateTimeToFormat.format(DateTimeFormatter.ofPattern(formatPattern));
    }

    public static String base64UrlSafe(String input) {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Unwrap / convert all Scriptable object to their java native counterpart and transform to json
     * <ul>
     *     <li>NativeArray -> ArrayList</li>
     *     <li>NativeJavaObject -> unwrapped java object</li>
     *     <li>NativeObject -> LinkedHashMap</li>
     *  </ul>
     *
     * @param o
     * @return
     */
    public static String convertToJson(Object o) {
        return JSON_SERIALIZER.toJson(unwrap(o));
    }

    static Object unwrap(Object o) {
        if (o instanceof Scriptable) {
            if (o instanceof NativeArray) {
                List<Object> res = new ArrayList<>();
                var na = (NativeArray) o;
                for (var a : na) {
                    res.add(unwrap(a));
                }
                return res;
            } else if (o instanceof NativeJavaObject) {
                return ((NativeJavaObject) o).unwrap();
            } else if (o instanceof NativeObject) {
                var na = (NativeObject) o;
                Map<Object, Object> res = new LinkedHashMap<>();
                for (var kv : na.entrySet()) {
                    res.put(kv.getKey(), unwrap(kv.getValue()));
                }
                return res;
            } else if (o instanceof IdScriptableObject) {
                return parseIdScriptableObject((IdScriptableObject) o);
            }
        } else if (o instanceof CharSequence) {
            return o.toString();
        }
        return o;
    }

    private static Object parseIdScriptableObject(IdScriptableObject object) {
        var className = object.getClassName();
        switch (className) {
            case "String":
                return ScriptRuntime.toCharSequence(object);
            case "Boolean":
                return Context.jsToJava(object, Boolean.class);
            case "Date": {
                return Context.jsToJava(object, Date.class);
            }
        }
        // better safe than sorry: we ignore all the unknown objects
        return null;
    }

    /**
     * Adapter for Javascript -> Java -> JSON binding.
     * Writes a JSON decimal only if it's strictly necessary.
     * If the number does not have a decimal part, it will be serialized as Long (int64)
     */
    private static class DoubleSerializer implements JsonSerializer<Double> {

        @Override
        public JsonElement serialize(Double src, Type typeOfSrc, JsonSerializationContext context) {

            if(src == null) {
                return null;
            }

            if(Math.floor(src) == src) {
                return new JsonPrimitive(src.longValue());
            } else {
                return new JsonPrimitive(src);
            }
        }
    }
}
