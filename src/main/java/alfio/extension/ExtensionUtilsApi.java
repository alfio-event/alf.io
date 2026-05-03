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

import io.roastedroot.quickjs4j.annotations.Builtins;
import io.roastedroot.quickjs4j.annotations.HostFunction;

import java.util.List;

@Builtins("ExtensionUtils")
@SuppressWarnings({"rawtypes", "unchecked"})
class ExtensionUtilsApi {

    @HostFunction
    public String md5(String str) {
        return ExtensionUtils.md5(str);
    }

    @HostFunction("base64UrlSafe")
    public String base64UrlSafe(String str) {
        return ExtensionUtils.base64UrlSafe(str);
    }

    @HostFunction
    public String convertToJson(String str) {
        // identity — JS already stringified the object before calling
        return str;
    }

    @HostFunction
    public String format(String str, List params) {
        String[] arr = params != null ? (String[]) params.toArray(new String[0]) : new String[0];
        return ExtensionUtils.format(str, arr);
    }

    @HostFunction
    public String computeHMAC(String secret, List parts) {
        String[] arr = parts != null ? (String[]) parts.toArray(new String[0]) : new String[0];
        return ExtensionUtils.computeHMAC(secret, arr);
    }

    @HostFunction
    public String formatDateTime(String dateTime, String pattern, boolean utc) {
        return ExtensionUtils.formatDateTime(dateTime, pattern, utc);
    }
}
