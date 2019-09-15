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
package alfio.model.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

@Getter
public class CheckInOutputColorConfiguration {
    private final String defaultColorName;
    private final List<ColorConfiguration> configurations;

    @JsonCreator
    public CheckInOutputColorConfiguration(@JsonProperty("defaultColorName") String defaultColorName,
                                           @JsonProperty("configurations") List<ColorConfiguration> configurations) {
        this.defaultColorName = defaultColorName;
        this.configurations = configurations;
    }

    @Getter
    public static class ColorConfiguration {
        private final String colorName;
        private final List<Integer> categories;

        public ColorConfiguration(@JsonProperty("colorName") String colorName,
                                   @JsonProperty("categories") List<Integer> categories) {
            this.colorName = colorName;
            this.categories = Optional.ofNullable(categories).orElse(List.of());
        }
    }
}

