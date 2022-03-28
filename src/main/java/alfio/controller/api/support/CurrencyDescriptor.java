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

import lombok.Getter;

@Getter
public class CurrencyDescriptor {
    private final String code;
    private final String name;
    private final String symbol;
    private final int fractionDigits;

    public CurrencyDescriptor(String code, String name, String symbol, int fractionDigits) {
        this.code = code;
        this.name = name;
        this.symbol = symbol;
        this.fractionDigits = fractionDigits;
    }
}
