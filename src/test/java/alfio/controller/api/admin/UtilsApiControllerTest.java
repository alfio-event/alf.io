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
package alfio.controller.api.admin;

import alfio.util.MonetaryUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UtilsApiControllerTest {

    @Test
    void getCurrencies() {
        new UtilsApiController(null, null, null)
            .getCurrencies()
            .forEach(currency -> {
                assertFalse(currency.getFractionDigits() < 0);
                assertEquals(MonetaryUtil.unitToCents(BigDecimal.TEN, currency.getCode()), 10 * Math.pow(10, currency.getFractionDigits()));
            });
    }
}