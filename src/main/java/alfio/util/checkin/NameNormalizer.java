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
package alfio.util.checkin;

// heavily inspired by https://stackoverflow.com/a/5470803
public class NameNormalizer {

    private NameNormalizer() {}

    public static String normalize(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i=0; i < input.length(); i++) {
            sb.append(replaceCharIfNotSupported(input.charAt(i)));
        }
        return sb.toString();
    }

    private static String replaceCharIfNotSupported(char in) {
        if (in == 'Σ') {
            return "σς";
        }
        char lowercase = Character.toLowerCase(in);
        if (lowercase == 'ı') {
            return "i"; // special case: we render the turkish ı as i since its uppercase is represented as Latin I
        }
        return Character.toString(lowercase);
    }
}
