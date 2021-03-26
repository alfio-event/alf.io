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

import static alfio.util.ItalianTaxIdValidator.fiscalCodeMatchesWithName;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItalianTaxIdValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = { "63828920585", "58148510561", "61579460223" })
    // note: the numbers above are random and as of 2021-02-06 they are not present in the italian database.
    void businessCodeValidationSuccess(String number) {
        assertTrue(ItalianTaxIdValidator.validateFiscalCode(number));
    }

    @ParameterizedTest
    @ValueSource(strings = { "63828920583", "58148510562", "61579460221" })
    void businessCodeValidationFailure(String number) {
        assertFalse(ItalianTaxIdValidator.validateFiscalCode(number));
    }

    @ParameterizedTest
    @ValueSource(strings = { "63828920585", "58148510561", "61579460223" })
    // note: the numbers above are random and as of 2021-02-05 they are not present in the italian database.
    void vatIdValidationSuccess(String number) {
        assertTrue(ItalianTaxIdValidator.validateVatId(number));
    }

    @ParameterizedTest
    @ValueSource(strings = { "63828920583", "58148510562", "61579460221" })
    void vatIdValidationFailure(String number) {
        assertFalse(ItalianTaxIdValidator.validateVatId(number));
    }

    @ParameterizedTest
    @ValueSource(strings = { "SMPHMR66A01B602I", "SMPLSI96L50C770S", "PRSBTL38H18H826X", "prsbtl38h18h826x" })
    // note: the numbers above are random
    void personalCodeValidationSuccess(String number) {
        assertTrue(ItalianTaxIdValidator.validateFiscalCode(number));
    }

    @ParameterizedTest
    @ValueSource(strings = { "SMPHMR66A01B602F", "SMPLSI96L50C770A", "PRSBTL38H18H826S", "PRSBTL38H18H826", "PRSBTL38H18H826SX" })
    void personalCodeValidationFailure(String number) {
        assertFalse(ItalianTaxIdValidator.validateFiscalCode(number));
    }

    @Test
    void validateNamePart() {
        assertTrue(fiscalCodeMatchesWithName("Homer", "Simpson", "SMPHMR66A01B602I"));
        assertTrue(fiscalCodeMatchesWithName("Lisa", "Simpson", "SMPLSI96L50C770S"));
        assertTrue(fiscalCodeMatchesWithName("Lisà", "Sìmpsön", "SMPLSI96L50C770S"));
        assertTrue(fiscalCodeMatchesWithName("Lisa Mary", "Simpson", "SMPLMR96S50F205L"));
        assertTrue(fiscalCodeMatchesWithName("Gi", "Ma", "MAXGIX80E02F205R"));
        assertTrue(fiscalCodeMatchesWithName("First", "Last", "63828920585"));
    }

    @Test
    void namePartValidationFailure() {
        assertFalse(fiscalCodeMatchesWithName("Homer", "Simenon", "SMPHMR66A01B602I")); // should be: Simpson
        assertFalse(fiscalCodeMatchesWithName("Lissa", "Simpson", "SMPLSI96L50C770S")); // should be: Lisa
        assertFalse(fiscalCodeMatchesWithName("Lisa", "Simpson", "SMPLMR96S50F205L")); // should be: Lisa Mary
        assertFalse(fiscalCodeMatchesWithName("Gi", "Mar", "MAXGIX80E02F205R")); // should be: Ma Gi
        assertFalse(fiscalCodeMatchesWithName("First", "Last", "61579460221"));
    }

}