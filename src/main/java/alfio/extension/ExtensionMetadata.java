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

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ExtensionMetadata {
    String id;
    String displayName;
    Integer version;
    boolean async;
    List<String> events;
    Parameters parameters;

    @Getter
    @AllArgsConstructor
    public static class Parameters {
        List<Field> fields;
        List<String> configurationLevels;
    }


    @Getter
    @AllArgsConstructor
    public static class Field {
        String name;
        String description;
        String type;
        boolean required;
    }
}
