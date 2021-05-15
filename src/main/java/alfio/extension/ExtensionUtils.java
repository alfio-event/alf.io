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
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;

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
        return Json.GSON.toJson(unwrap(o));
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
            }
        } else if (o instanceof CharSequence) {
            return o.toString();
        }
        return o;
    }
}
