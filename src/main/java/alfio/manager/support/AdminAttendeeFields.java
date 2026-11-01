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
package alfio.manager.support;

import alfio.model.PurchaseContextFieldConfiguration;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.util.Json;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.type.TypeReference;

import java.util.*;

/**
 * Values of the attendees' additional fields, entered by an organizer.
 * <p>
 * Organizers can leave the required fields empty, since the attendees can fill them in later, and can change the
 * read-only ones. The values must still fit the field: an option of the field, no longer than its maximum length.
 */
public final class AdminAttendeeFields {

    private static final String ERROR_VALUE = "error.value.";

    private AdminAttendeeFields() {
    }

    /**
     * Validates the values entered for a ticket, and resolves the field names, which the client can write in any
     * case, to the configured ones.
     *
     * @param values     field name -> values. Checkboxes have one value for each option, empty if not selected
     * @param fields     the additional fields of the event
     * @param categoryId the category of the ticket, null for a category that doesn't exist yet
     * @return the values by configured field name, or the reasons why they have been rejected
     */
    public static Result<Map<String, List<String>>> resolve(Map<String, List<String>> values,
                                                            List<PurchaseContextFieldConfiguration> fields,
                                                            Integer categoryId) {
        Map<String, PurchaseContextFieldConfiguration> attendeeFields = new HashMap<>();
        for (var field : fields) {
            if (isAttendeeField(field)) {
                attendeeFields.putIfAbsent(field.getName().toLowerCase(Locale.ROOT), field);
            }
        }
        Map<String, List<String>> resolved = new LinkedHashMap<>();
        List<ErrorCode> errors = new ArrayList<>();
        values.forEach((name, fieldValues) -> {
            var field = attendeeFields.get(name.toLowerCase(Locale.ROOT));
            if (field == null) {
                errors.add(ErrorCode.custom("error.notfound." + name, name + " not found"));
                return;
            }
            var nonNullValues = Objects.requireNonNullElse(fieldValues, List.<String>of());
            var error = validate(field, nonNullValues, categoryId);
            if (error.isPresent()) {
                errors.add(error.get());
            } else if (field.rulesApply(categoryId)) {
                resolved.put(field.getName(), nonNullValues);
            }
        });
        if (errors.isEmpty()) {
            return Result.success(resolved);
        }
        return Result.error(errors);
    }

    /**
     * The fields describing the attendee, without the ones collected for an additional item
     */
    public static boolean isAttendeeField(PurchaseContextFieldConfiguration field) {
        // the fields not linked to an additional item are stored with -1 as additional item ID
        var additionalServiceId = field.getAdditionalServiceId();
        return field.getContext() == PurchaseContextFieldConfiguration.Context.ATTENDEE
            && (additionalServiceId == null || additionalServiceId < 0);
    }

    /**
     * The values of a field, as stored by {@link alfio.repository.PurchaseContextFieldRepository#updateOrInsert}:
     * a checkbox with more options stores a JSON list, any other field a single value
     */
    public static List<String> decode(PurchaseContextFieldConfiguration field, String value) {
        if (StringUtils.isBlank(value)) {
            return List.of();
        }
        if (field.isCheckboxField() && value.startsWith("[")) {
            return Json.fromJson(value, new TypeReference<List<String>>() {});
        }
        return List.of(value);
    }

    private static Optional<ErrorCode> validate(PurchaseContextFieldConfiguration field, List<String> values, Integer categoryId) {
        var name = field.getName();
        var filled = values.stream().filter(StringUtils::isNotBlank).toList();
        if (filled.isEmpty()) {
            // nothing to check: the value is removed
            return Optional.empty();
        }
        if (!field.rulesApply(categoryId)) {
            return Optional.of(ErrorCode.custom("error.category." + name, name + " is not available for the ticket category"));
        }
        if (field.isCheckboxField()) {
            if (values.size() > field.getCount()) {
                return Optional.of(ErrorCode.custom(ERROR_VALUE + name, name + " has more values than options"));
            }
        } else if (values.size() > 1) {
            return Optional.of(ErrorCode.custom(ERROR_VALUE + name, name + " accepts a single value"));
        }
        if (hasOptions(field) && !new HashSet<>(field.getRestrictedValues()).containsAll(filled)) {
            return Optional.of(ErrorCode.custom(ERROR_VALUE + name, name + " contains a value which is not one of its options"));
        }
        if (field.isTextField()) {
            var value = filled.getFirst();
            if (field.isMaxLengthDefined() && value.length() > field.getMaxLength()) {
                return Optional.of(ErrorCode.custom("error.length." + name, name + " must be at most " + field.getMaxLength() + " characters long"));
            }
            if (field.isMinLengthDefined() && value.length() < field.getMinLength()) {
                return Optional.of(ErrorCode.custom("error.length." + name, name + " must be at least " + field.getMinLength() + " characters long"));
            }
        }
        return Optional.empty();
    }

    private static boolean hasOptions(PurchaseContextFieldConfiguration field) {
        return field.isCheckboxField() || field.isSelectField() || "radio".equals(field.getType());
    }
}
