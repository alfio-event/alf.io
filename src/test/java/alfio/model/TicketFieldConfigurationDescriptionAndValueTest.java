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
package alfio.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TicketFieldConfigurationDescriptionAndValueTest {

    private TicketFieldConfiguration configuration;
    private TicketFieldDescription description;

    @BeforeEach
    void setUp() {
        configuration = mock(TicketFieldConfiguration.class);
        description = mock(TicketFieldDescription.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text",
        "tel",
        "textarea",
        "vat:eu"})
    void getValueDescriptionForTextField(String type) {
        var field = new TicketFieldConfigurationDescriptionAndValue(configuration, description, 1, "simple value");
        when(configuration.getType()).thenReturn(type);
        assertEquals("simple value", field.getValueDescription());
    }

    @ParameterizedTest
    @ValueSource(strings = {"select", "radio"})
    void getValueDescriptionForSingleOptionField(String type) {
        when(description.getRestrictedValuesDescription()).thenReturn(Map.of("value1", "simple value"));
        when(configuration.getRestrictedValues()).thenReturn(List.of("value1", "value2"));
        var field = new TicketFieldConfigurationDescriptionAndValue(configuration, description, 1, "value1");
        when(configuration.getType()).thenReturn(type);
        assertEquals("simple value", field.getValueDescription());
    }

    @ParameterizedTest
    @ArgumentsSource(CheckboxArgumentProvider.class)
    void getValueDescriptionForMultipleOptionsField(String value, String expectedResult) {
        when(description.getRestrictedValuesDescription()).thenReturn(Map.of("value1", "first value", "value2", "second value"));
        when(configuration.getRestrictedValues()).thenReturn(List.of("value1", "value2"));
        var field = new TicketFieldConfigurationDescriptionAndValue(configuration, description, 1, value);
        when(configuration.getType()).thenReturn("checkbox");
        when(configuration.isCheckboxField()).thenReturn(true);
        assertEquals(expectedResult, field.getValueDescription());
    }

    static class CheckboxArgumentProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                Arguments.of("[\"value1\",\"value2\"]", "first value, second value"),
                Arguments.of("[\"value1\"]", "first value"),
                Arguments.of("[\"value2\"]", "second value"),
                Arguments.of("[\"value1\",\"null\",\"value2\"]", "first value, second value")
            );
        }
    }
}