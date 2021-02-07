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

import java.math.BigInteger;
import java.util.Objects;
import java.util.regex.Pattern;

@UtilityClass
public class PinGenerator {

    private static final String ALLOWED_CHARS = "ACDEFGHJKLMNPQRTUVWXY34679";
    private static final Pattern VALIDATION_PATTERN = Pattern.compile("^["+ALLOWED_CHARS+"]+$");
    private static final int PIN_LENGTH = 6;


    public static String uuidToPin(String uuid, int pinLength) {
        var src = new BigInteger(uuid.replace("-", "").substring(0, pinLength+1), 16);
        var chars = BigInteger.valueOf(ALLOWED_CHARS.length());
        var pin = new StringBuilder();
        do {
            var remainder = src.mod(chars);
            pin.append(ALLOWED_CHARS.charAt(remainder.intValue()));
            src = src.divide(chars);
        } while (!src.equals(BigInteger.ZERO));

        while(pin.length() < pinLength) {
            pin.append(ALLOWED_CHARS.charAt(0));
        }

        return pin.reverse().toString();
    }

    public static String pinToPartialUuid(String pin, int pinLength) {
        Assert.isTrue(isPinValid(pin, pinLength), "the given PIN is not valid");
        var uppercasePin = Objects.requireNonNull(pin).strip().toUpperCase();
        var base = BigInteger.valueOf(ALLOWED_CHARS.length());
        var num = BigInteger.ZERO;
        for (int i = 0; i < pin.length(); i++) {
            char c = uppercasePin.charAt(pin.length() - 1 - i);
            var toAdd = BigInteger.valueOf(ALLOWED_CHARS.indexOf(c)).multiply(base.pow(i));
            num = num.add(toAdd);
        }
        return StringUtils.leftPad(num.toString(16), pinLength+1, '0');
    }

    public static boolean isPinValid(String pin, int pinLength) {
        return pin != null
            && (pin.strip().length() == pinLength || pin.strip().length() == pinLength + 1)
            && VALIDATION_PATTERN.matcher(pin.toUpperCase()).matches();
    }

    public static String uuidToPin(String uuid) {
        return uuidToPin(uuid, PIN_LENGTH);
    }

    public static String pinToPartialUuid(String pin) {
        return pinToPartialUuid(pin, PIN_LENGTH);
    }

    public static boolean isPinValid(String pin) {
        return isPinValid(pin, PIN_LENGTH);
    }

}
