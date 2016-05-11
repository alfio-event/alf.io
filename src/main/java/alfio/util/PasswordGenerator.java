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

import java.util.*;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public final class PasswordGenerator {

    private static final char[] PASSWORD_CHARACTERS;
    private static final boolean DEV_MODE;
    private static final int MAX_LENGTH = 14;
    private static final int MIN_LENGTH = 10;
    private static final Pattern VALIDATION_PATTERN;

    static {
        List<Character> chars = new LinkedList<>();
        IntConsumer addToList = c -> chars.add((char) c);
        IntStream.rangeClosed('a', 'z').forEach(addToList);
        IntStream.rangeClosed('A', 'Z').forEach(addToList);
        IntStream.rangeClosed('0','9').forEach(addToList);
        chars.add('#');
        chars.add('~');
        chars.add('!');
        chars.add('-');
        chars.add('_');
        chars.add('/');
        chars.add('^');
        chars.add('&');
        chars.add('+');
        chars.add('%');
        chars.add('(');
        chars.add(')');
        chars.add('=');

        PASSWORD_CHARACTERS = ArrayUtils.toPrimitive(chars.toArray(new Character[chars.size()]));
        DEV_MODE = Arrays.stream(Optional.ofNullable(System.getProperty("spring.profiles.active")).map(p -> p.split(",")).orElse(new String[0]))
            .map(StringUtils::trim)
            .anyMatch(Initializer.PROFILE_DEV::equals);
        VALIDATION_PATTERN = Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*\\p{Punct})(?=\\S+$).{"+MIN_LENGTH+",}$");//source: http://stackoverflow.com/a/3802238
    }

    private PasswordGenerator() {
    }

    public static String generateRandomPassword() {
        if(DEV_MODE) {
            return "abcd";
        }
        Random r = new Random();
        int length = MIN_LENGTH + r.nextInt(MAX_LENGTH - MIN_LENGTH + 1);
        return RandomStringUtils.random(length, PASSWORD_CHARACTERS);
    }

    public static boolean isValid(String password) {
        return StringUtils.isNotBlank(password) && VALIDATION_PATTERN.matcher(password).matches();
    }
}
