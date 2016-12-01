package alfiov2;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * Created by yanke on 01.12.16.
 */
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
