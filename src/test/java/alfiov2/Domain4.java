package alfiov2;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * Created by yanke on 01.12.16.
 */
public class Domain4 {

    class Museum {
        List<Category> categories;
    }

    class Exibition {
        LocalDate validityStart;
        LocalDate validityEnd;
        List<Category> categories;
    }

    class Category {
        List<Price> prices;
        List<Ticket> tickets;
    }

    class Ticket {
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
        Ticket place;
        LocalDate startDate;
        LocalDate endDate;
        double price;
    }
}
