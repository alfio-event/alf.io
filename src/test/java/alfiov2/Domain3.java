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
package alfiov2;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

public class Domain3 {

    class ParkingLot {
        List<Category> categories;
    }

    class Category {
        List<Price> prices;
        List<Place> places;
    }

    class Place {
        int id;
    }

    class Price {
        double price;
        Date validityStart;
        Date validityEnd;
    }

    class PriceStrategy {

    }

    class Reservation {
        List<ReservationLine> lines;
    }

    class ReservationLine {
        Place place;
        LocalDate startDate;
        LocalDate endDate;
        double price;
    }
}
