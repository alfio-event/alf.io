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
package alfio.model.subscription;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public class LinkEventsToSubscriptionRequest {
    private final String slug;
    private final List<Integer> categories;

    @JsonCreator
    public LinkEventsToSubscriptionRequest(@JsonProperty("slug") String slug,
                                           @JsonProperty("categories") List<Integer> categories) {
        this.slug = slug;
        this.categories = categories;
    }

    public String getSlug() {
        return slug;
    }

    public List<Integer> getCategories() {
        return Objects.requireNonNullElse(categories, List.of());
    }
}
