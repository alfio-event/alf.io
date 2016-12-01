package alfiov2;

import java.util.Date;
import java.util.List;

/**
 * Created by yanke on 01.12.16.
 */
public class Domain2 {

    class Hotel {
        List<Category> categories;
    }

    class Category {
        List<Price> prices;
        List<Room> rooms;
    }

    class Room {
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
        Room room;
        Date startDate;
        Date endDate;
        double price;
    }
}
