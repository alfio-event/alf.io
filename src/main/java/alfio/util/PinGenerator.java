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

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import java.util.Objects;
import java.util.regex.Pattern;

@UtilityClass
public class PinGenerator {

    private static final String ALLOWED_CHARS = "ACDEFGHJKLMNPQRTUVWXY34679";
    private static final Pattern VALIDATION_PATTERN = Pattern.compile("^["+ALLOWED_CHARS+"]+$");
    private static final int UUID_PORTION_LENGTH = 7;

    public static String uuidToPin(String uuid) {
        long src = Long.parseLong(uuid.replaceAll("-", "").substring(0, UUID_PORTION_LENGTH), 16);
        long chars = ALLOWED_CHARS.length();
        var pin = new StringBuilder();
        do {
            long remainder = src % chars;
            pin.append(ALLOWED_CHARS.charAt((int)remainder));
            src /= chars;
        } while (src != 0);

        while(pin.length() < 6) {
            pin.append(ALLOWED_CHARS.charAt(0));
        }

        return pin.reverse().toString();
    }

    public static String pinToPartialUuid(String pin) {
        Assert.isTrue(isPinValid(pin), "the given PIN is not valid");
        var uppercasePin = Objects.requireNonNull(pin).strip().toUpperCase();
        long base = ALLOWED_CHARS.length();
        long num = 0;
        for (int i = 0; i < pin.length(); i++) {
            char c = uppercasePin.charAt(pin.length() - 1 - i);
            num += (long) ALLOWED_CHARS.indexOf(c) * (long) Math.pow(base, i);
        }
        return StringUtils.leftPad(Long.toHexString(num), UUID_PORTION_LENGTH, '0');
    }

    public static boolean isPinValid(String pin) {
        return pin != null
            && pin.strip().length() == 6
            && VALIDATION_PATTERN.matcher(pin.toUpperCase()).matches();
    }

}
