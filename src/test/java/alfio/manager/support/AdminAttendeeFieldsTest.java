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
import alfio.model.PurchaseContextFieldConfiguration.Context;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdminAttendeeFieldsTest {

    private static final int CATEGORY_ID = 10;
    private static final int OTHER_CATEGORY_ID = 20;

    private final List<PurchaseContextFieldConfiguration> fields = List.of(
        field(1, "company", "input:text", 3, 10, null, null, Context.ATTENDEE, -1),
        field(2, "diet", "select", null, null, "[\"vegan\",\"standard\"]", null, Context.ATTENDEE, -1),
        field(3, "topics", "checkbox", null, null, "[\"java\",\"web\",\"cloud\"]", null, Context.ATTENDEE, -1),
        field(4, "vip", "input:text", null, null, null, "[" + OTHER_CATEGORY_ID + "]", Context.ATTENDEE, -1),
        field(5, "size", "select", null, null, "[\"S\",\"M\"]", null, Context.ADDITIONAL_SERVICE, 7)
    );

    @Test
    void resolvesTheFieldNamesInAnyCase() {
        var result = AdminAttendeeFields.resolve(Map.of("Company", List.of("ACME"), "DIET", List.of("vegan")), fields, CATEGORY_ID);
        assertTrue(result.isSuccess());
        assertEquals(Map.of("company", List.of("ACME"), "diet", List.of("vegan")), result.getData());
    }

    @Test
    void keepsTheEmptyValues() {
        // an empty value removes the stored one
        var result = AdminAttendeeFields.resolve(Map.of("company", List.of(""), "topics", List.of()), fields, CATEGORY_ID);
        assertTrue(result.isSuccess());
        assertEquals(Map.of("company", List.of(""), "topics", List.of()), result.getData());
    }

    @Test
    void rejectsUnknownFields() {
        assertErrors(AdminAttendeeFields.resolve(Map.of("unknown", List.of("x")), fields, CATEGORY_ID), "error.notfound.unknown");
    }

    @Test
    void rejectsTheFieldsOfAdditionalItems() {
        assertErrors(AdminAttendeeFields.resolve(Map.of("size", List.of("S")), fields, CATEGORY_ID), "error.notfound.size");
    }

    @Test
    void rejectsValuesThatAreNotOptions() {
        assertErrors(AdminAttendeeFields.resolve(Map.of("diet", List.of("meat")), fields, CATEGORY_ID), "error.value.diet");
    }

    @Test
    void acceptsCheckboxValuesByPosition() {
        // one value for each option, empty if not selected
        var topics = Arrays.asList("java", null, "cloud");
        var result = AdminAttendeeFields.resolve(Map.of("topics", topics), fields, CATEGORY_ID);
        assertTrue(result.isSuccess());
        assertEquals(topics, result.getData().get("topics"));
    }

    @Test
    void rejectsMoreCheckboxValuesThanOptions() {
        var topics = List.of("java", "web", "cloud", "java");
        assertErrors(AdminAttendeeFields.resolve(Map.of("topics", topics), fields, CATEGORY_ID), "error.value.topics");
    }

    @Test
    void rejectsMoreValuesForSingleValueFields() {
        assertErrors(AdminAttendeeFields.resolve(Map.of("company", List.of("A", "B")), fields, CATEGORY_ID), "error.value.company");
    }

    @Test
    void checksTheLengthOfTexts() {
        assertErrors(AdminAttendeeFields.resolve(Map.of("company", List.of("A very long company name")), fields, CATEGORY_ID), "error.length.company");
        assertErrors(AdminAttendeeFields.resolve(Map.of("company", List.of("AB")), fields, CATEGORY_ID), "error.length.company");
    }

    @Test
    void rejectsFieldsOfOtherCategories() {
        assertErrors(AdminAttendeeFields.resolve(Map.of("vip", List.of("yes")), fields, CATEGORY_ID), "error.category.vip");
        assertTrue(AdminAttendeeFields.resolve(Map.of("vip", List.of("yes")), fields, OTHER_CATEGORY_ID).isSuccess());
    }

    @Test
    void newCategoriesGetOnlyTheFieldsOfAllCategories() {
        assertErrors(AdminAttendeeFields.resolve(Map.of("vip", List.of("yes")), fields, null), "error.category.vip");
        assertTrue(AdminAttendeeFields.resolve(Map.of("company", List.of("ACME")), fields, null).isSuccess());
    }

    @Test
    void ignoresEmptyValuesOfFieldsOfOtherCategories() {
        var result = AdminAttendeeFields.resolve(Map.of("vip", List.of("")), fields, CATEGORY_ID);
        assertTrue(result.isSuccess());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    void decodesTheStoredValues() {
        var topics = fields.get(2);
        assertEquals(Arrays.asList("java", null, "cloud"), AdminAttendeeFields.decode(topics, "[\"java\",null,\"cloud\"]"));
        assertEquals(List.of("ACME"), AdminAttendeeFields.decode(fields.getFirst(), "ACME"));
        assertEquals(List.of(), AdminAttendeeFields.decode(fields.getFirst(), null));
    }

    private static void assertErrors(Result<?> result, String... codes) {
        assertFalse(result.isSuccess());
        assertEquals(List.of(codes), result.getErrors().stream().map(ErrorCode::getCode).toList());
    }

    private static PurchaseContextFieldConfiguration field(int id, String name, String type, Integer minLength, Integer maxLength,
                                                           String restrictedValues, String categoryIds, Context context,
                                                           Integer additionalServiceId) {
        return new PurchaseContextFieldConfiguration(id, 1, null, name, id, type, maxLength, minLength, false, true,
            restrictedValues, context, additionalServiceId, categoryIds, null, false);
    }
}
