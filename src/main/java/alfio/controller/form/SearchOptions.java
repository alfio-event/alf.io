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
package alfio.controller.form;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.UUID;

@Data
@Log4j2
public class SearchOptions {
    private String subscription;
    private Integer organizer;
    private String organizerSlug;
    private List<String> tags;

    public static SearchOptions empty() {
        return new SearchOptions();
    }

    public boolean isEmpty() {
        return StringUtils.isEmpty(subscription)
            && (organizer == null || organizer == 0 || StringUtils.isEmpty(organizerSlug))
            && CollectionUtils.isEmpty(tags);
    }

    public UUID getSubscriptionCodeUUIDOrNull() {
        try {
            if(StringUtils.isEmpty(subscription)) {
                return null;
            }
            return UUID.fromString(subscription);
        } catch (Exception e) {
            log.warn("invalid UUID received: {}", subscription);
            return null;
        }
    }
}
