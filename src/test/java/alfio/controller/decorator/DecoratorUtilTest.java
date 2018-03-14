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
package alfio.controller.decorator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class DecoratorUtilTest {

    @Test
    public void range0to5() {
        assertArrayEquals(new int[]{0,1,2,3,4,5}, DecoratorUtil.generateRangeOfTicketQuantity(5,5));
    }

    @Test
    public void range0to1() {
        assertArrayEquals(new int[]{0,1}, DecoratorUtil.generateRangeOfTicketQuantity(1, 50));
    }

    @Test
    public void range0to0() {
        assertArrayEquals(new int[]{0}, DecoratorUtil.generateRangeOfTicketQuantity(-1, 50));
    }

}