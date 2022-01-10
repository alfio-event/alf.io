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
package alfio.manager;

import alfio.repository.EventAdminRepository;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.RandomStringGenerator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@AllArgsConstructor
@Transactional(readOnly = true)
public class EventNameManager {

    private static final Pattern NUMBER_MATCHER = Pattern.compile("^\\d+$");
    private static final String SPACES_AND_PUNCTUATION = "[\\s\\p{Punct}]";
    private static final String FIND_EVIL_CHARACTERS = "[^\\sA-Z\\-a-z0-9]";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final RandomStringGenerator RANDOM_STRING_GENERATOR = new RandomStringGenerator.Builder()
        .withinRange(new char[] {'a', 'z'}, new char[] {'A', 'Z'} , new char[] { '0', '9'})
        .usingRandom(RANDOM::nextInt)
        .build();
    private final EventAdminRepository eventAdminRepository;

    /**
     * Generates and returns a short name based on the given display name.<br>
     * The generated short name will be returned only if it was not already used.<br>
     * The input parameter will be clean from "evil" characters such as punctuation and accents
     *
     * 1) if the {@code displayName} is a one-word name, then no further calculation will be done, and it will be returned as it is, to lower case
     * 2) the {@code displayName} will be split by word and transformed to lower case. If the total length is less than 15, then it will be joined using "-" and returned
     * 3) the first letter of each word will be taken, excluding numbers
     * 4) a random code will be returned
     *
     * @param displayName
     * @return
     */
    public String generateShortName(String displayName) {
        Validate.isTrue(StringUtils.isNotBlank(displayName));
        String cleanDisplayName = StringUtils.stripAccents(StringUtils.normalizeSpace(displayName)).toLowerCase(Locale.ENGLISH).replaceAll(FIND_EVIL_CHARACTERS, "-");
        if(!StringUtils.containsWhitespace(cleanDisplayName) && isUnique(cleanDisplayName)) {
            return cleanDisplayName;
        }
        Optional<String> dashedName = getDashedName(cleanDisplayName);
        if(dashedName.isPresent()) {
            return dashedName.get();
        }
        Optional<String> croppedName = getCroppedName(cleanDisplayName);
        return croppedName.orElseGet(this::generateRandomName);
    }

    private String generateRandomName() {
        return IntStream.range(0, 5)
                .mapToObj(i -> RANDOM_STRING_GENERATOR.generate(15))
                .filter(this::isUnique)
                .limit(1)
                .findFirst()
                .orElse("");
    }

    private Optional<String> getCroppedName(String cleanDisplayName) {
        String candidate = Arrays.stream(cleanDisplayName.split(SPACES_AND_PUNCTUATION))
                .map(w -> Pair.of(NUMBER_MATCHER.matcher(w).matches(), w))
                .map(p -> Boolean.TRUE.equals(p.getKey()) ? p.getValue() : StringUtils.left(p.getValue(), 1))
                .collect(Collectors.joining());
        if(isUnique(candidate)) {
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private Optional<String> getDashedName(String cleanDisplayName) {
        if(cleanDisplayName.length() < 15) {
            String candidate = cleanDisplayName.replaceAll(SPACES_AND_PUNCTUATION, "-");
            if(isUnique(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public boolean isUnique(String shortName) {
        return !eventAdminRepository.existsBySlug(shortName);
    }
}
