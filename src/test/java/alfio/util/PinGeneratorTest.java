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
package alfio.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PinGeneratorTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "0cea7af7-899d-4de4-9ba1-414469b7d69c",
        "c09644d4-3694-4183-ba85-77a7a5f77829",
        "00000000-0000-0000-0000-000000000000",
        "ffffffff-3694-4183-ba85-77a7a5f77829",
        "ffffffff-ffff-4183-ba85-77a7a5f77829"
    })
    void uuidToPinAndBack(String uuid) {
        // we convert the first 7 characters
        var pin = PinGenerator.uuidToPin(uuid);
        assertTrue(PinGenerator.isPinValid(pin));
        var partialUuid = PinGenerator.pinToPartialUuid(pin);
        assertTrue(uuid.indexOf(partialUuid) == 0);
    }

    @Test
    void checkValue() {
        assertEquals("CEURQ3", PinGenerator.uuidToPin("0cea7af7-899d-4de4-9ba1-414469b7d69c"));
        assertEquals("U96U6T", PinGenerator.uuidToPin("c09644d4-3694-4183-ba85-77a7a5f77829"));
        assertEquals("AAAAAA", PinGenerator.uuidToPin("00000000-0000-0000-0000-000000000000"));
        assertEquals("4TM34T", PinGenerator.uuidToPin("ffffffff-3694-4183-ba85-77a7a5f77829"));
        assertEquals("4TM34T", PinGenerator.uuidToPin("ffffffff-ffff-4183-ba85-77a7a5f77829"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "AAAAAA",
        "aaaaaa",
        "CEURQ3",
        "U96U6T",
        "4TM34T"
    })
    void successfulPinValidation(String pin) {
        assertTrue(PinGenerator.isPinValid(pin));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "AAAAA",
        " AAAAA",
        "aaaaa ",
        "CEURZ3",
        "000000"
    })
    void failedPinValidation(String pin) {
        assertFalse(PinGenerator.isPinValid(pin));
    }
}