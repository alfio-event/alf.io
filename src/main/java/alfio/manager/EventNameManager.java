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

import alfio.repository.EventRepository;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class EventNameManager {

    private static final Pattern NUMBER_MATCHER = Pattern.compile("^\\d+$");
    public static final String SPACES_AND_PUNCTUATION = "[\\s\\p{Punct}]";
    private final EventRepository eventRepository;

    @Autowired
    public EventNameManager(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Generates and returns a short name based on the given display name.<br>
     * The generated short name will be returned only if it was not already used.<br>
     *
     * 1) if the {@code displayName} is a one-word name, then no further calculation will be done and it will be returned as it is, to lower case
     * 2) the {@code displayName} will be split by word and transformed to lower case. If the total length is less than 15, then it will be joined using "-" and returned
     * 3) the first letter of each word will be taken, excluding numbers
     * 4) a random code will be returned
     *
     * @param displayName
     * @return
     */
    public String generateShortName(String displayName) {
        Validate.isTrue(StringUtils.isNotBlank(displayName));
        String cleanDisplayName = StringUtils.normalizeSpace(displayName).toLowerCase(Locale.ENGLISH);
        if(!StringUtils.containsWhitespace(cleanDisplayName) && isUnique(cleanDisplayName)) {
            return cleanDisplayName;
        }
        Optional<String> dashedName = getDashedName(cleanDisplayName);
        if(dashedName.isPresent()) {
            return dashedName.get();
        }
        Optional<String> croppedName = getCroppedName(cleanDisplayName);
        if (croppedName.isPresent()) {
            return croppedName.get();
        }
        return generateRandomName();
    }

    private String generateRandomName() {
        return IntStream.range(0, 5)
                .mapToObj(i -> RandomStringUtils.randomAlphanumeric(15))
                .filter(this::isUnique)
                .limit(1)
                .findFirst()
                .orElse("");
    }

    private Optional<String> getCroppedName(String cleanDisplayName) {
        String candidate = Arrays.stream(cleanDisplayName.split(SPACES_AND_PUNCTUATION))
                .map(w -> Pair.of(NUMBER_MATCHER.matcher(w).matches(), w))
                .map(p -> p.getKey() ? p.getValue() : StringUtils.left(p.getValue(), 1))
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
        return eventRepository.countByShortName(shortName) == 0;
    }
}
