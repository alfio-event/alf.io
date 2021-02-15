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

import lombok.AllArgsConstructor;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;

import static java.util.Map.entry;
import static org.apache.commons.lang3.StringUtils.*;

@UtilityClass
public class ItalianTaxIdValidator {
    private static final char[] CONTROL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final Map<Character, EvenOddValueContainer> VALUES = Map.ofEntries(
        entry('A', new EvenOddValueContainer(0, 1)),
        entry('B', new EvenOddValueContainer(1, 0)),
        entry('C', new EvenOddValueContainer(2, 5)),
        entry('D', new EvenOddValueContainer(3, 7)),
        entry('E', new EvenOddValueContainer(4, 9)),
        entry('F', new EvenOddValueContainer(5, 13)),
        entry('G', new EvenOddValueContainer(6, 15)),
        entry('H', new EvenOddValueContainer(7, 17)),
        entry('I', new EvenOddValueContainer(8, 19)),
        entry('J', new EvenOddValueContainer(9, 21)),
        entry('K', new EvenOddValueContainer(10,2)),
        entry('L', new EvenOddValueContainer(11,4)),
        entry('M', new EvenOddValueContainer(12,18)),
        entry('N', new EvenOddValueContainer(13,20)),
        entry('O', new EvenOddValueContainer(14,11)),
        entry('P', new EvenOddValueContainer(15,3 )),
        entry('Q', new EvenOddValueContainer(16,6 )),
        entry('R', new EvenOddValueContainer(17,8 )),
        entry('S', new EvenOddValueContainer(18,12)),
        entry('T', new EvenOddValueContainer(19,14)),
        entry('U', new EvenOddValueContainer(20,16)),
        entry('V', new EvenOddValueContainer(21,10)),
        entry('W', new EvenOddValueContainer(22,22)),
        entry('X', new EvenOddValueContainer(23,25)),
        entry('Y', new EvenOddValueContainer(24,24)),
        entry('Z', new EvenOddValueContainer(25,23)),
        entry('0', new EvenOddValueContainer(0, 1)),
        entry('1', new EvenOddValueContainer(1,0)),
        entry('2', new EvenOddValueContainer(2,5)),
        entry('3', new EvenOddValueContainer(3,7)),
        entry('4', new EvenOddValueContainer(4,9)),
        entry('5', new EvenOddValueContainer(5,13)),
        entry('6', new EvenOddValueContainer(6,15)),
        entry('7', new EvenOddValueContainer(7,17)),
        entry('8', new EvenOddValueContainer(8,19)),
        entry('9', new EvenOddValueContainer(9,21))
    );

    public static boolean validateFiscalCode(String fiscalCode) {
        var code = StringUtils.upperCase(trimToNull(fiscalCode));
        int length = length(code);
        if(length == 11) {
            // when length is 11 the fiscal code is equal to the VAT Number
            return validateVatId(fiscalCode);
        } else if(isBlank(code) || length != 16) {
            return false;
        }
        var chars = code.toCharArray();
        int sum = 0;
        for (int i = 0; i < chars.length - 1; i++) {
            var valueContainer = Objects.requireNonNull(VALUES.get(chars[i]));
            sum += valueContainer.getValue(i);
        }
        return chars[15] == CONTROL_CHARS[sum % 26];
    }

    public static boolean validateVatId(String vatId) {
        var nr = StringUtils.trimToNull(vatId);
        if(StringUtils.length(nr) != 11 && !StringUtils.isNumeric(nr)) {
            return false;
        }
        int sumEven = 0;
        int sumOdd = 0;
        var chars = nr.toCharArray();
        for (int i=0; i < 10; i++) {
            int val = Character.getNumericValue(chars[i]);
            if((i + 1) % 2 == 0) {
                int product = val * 2;
                sumOdd += (product > 9 ? product - 9 : product);
            } else {
                sumEven += val;
            }
        }
        int controlDigit = (sumEven + sumOdd) % 10;
        if(controlDigit > 0) {
            controlDigit = 10 - controlDigit;
        }
        return controlDigit == Character.getNumericValue(chars[10]);
    }

    @AllArgsConstructor
    private static class EvenOddValueContainer {
        private final int evenValue;
        private final int oddValue;

        private int getValue(int index) {
            return (index + 1) % 2 == 0 ? evenValue : oddValue;
        }
    }
}
