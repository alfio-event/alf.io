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

import alfio.config.Initializer;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public final class VoucherGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] VOUCHER_CHARACTERS;
    private static final int MAX_LENGTH = 14;
    private static final int MIN_LENGTH = 10;
    private static final Pattern VALIDATION_PATTERN;

    static {
        List<Character> chars = new ArrayList<>();
        IntConsumer addToList = c -> chars.add((char) c);
        IntStream.rangeClosed('A', 'Z').forEach(addToList);
        IntStream.rangeClosed('0','9').forEach(addToList);
         chars.add('!');
        chars.add('-');
        chars.add('_');
        chars.add('*');
        chars.add('$');

        VOUCHER_CHARACTERS = ArrayUtils.toPrimitive(chars.toArray(new Character[0]));
        VALIDATION_PATTERN = Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*\\p{Punct})(?=\\S+$).{"+MIN_LENGTH+",}$");//source: http://stackoverflow.com/a/3802238
    }

    private VoucherGenerator() {
    }

    /***
     * TODO: We could find a more clear logic than "copied generatePassword". It's okay for now
     * @return
     */
    public static String generateRandomVoucher() {
        int length = MIN_LENGTH + RANDOM.nextInt(MAX_LENGTH - MIN_LENGTH + 1);
        return RandomStringUtils.random(length, 0, VOUCHER_CHARACTERS.length, false, false, VOUCHER_CHARACTERS, RANDOM).toUpperCase();
    }

    public static boolean isValid(String voucher) {
        return StringUtils.isNotBlank(voucher) && VALIDATION_PATTERN.matcher(voucher).matches();
    }
}
