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
package alfio.controller.api.support;

import alfio.model.PurchaseContextFieldConfiguration;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Getter
public class AdditionalField {
    private final String name;
    private final String value;
    private final String type;
    private final boolean required;
    private final boolean editable;
    private final Integer minLength;
    private final Integer maxLength;
    private final List<String> restrictedValues;
    private final List<Field> fields;
    private final boolean beforeStandardFields;
    private final Map<String, Description> description;

    public static AdditionalField fromFieldConfiguration(PurchaseContextFieldConfiguration tfc,
                                                         String value,
                                                         List<Field> fields,
                                                         boolean isBeforeStandardFields,
                                                         Map<String, Description> description) {
        return new AdditionalField(tfc.getName(),
            value,
            tfc.getType(),
            tfc.isRequired(),
            tfc.isEditable(),
            tfc.getMinLength(),
            tfc.getMaxLength(),
            tfc.getRestrictedValues(),
            fields,
            isBeforeStandardFields,
            description);
    }
}
