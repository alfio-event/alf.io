package alfiov2;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by yanke on 01.12.16.
 */
public class Domain {

    static class Event {
        List<Sector> sectors;
        Date eventDate;
    }


    static class Sector {
        public List<Seat> seats;
        List<Price> prices;
    }

    static class Seat {
        int id;
        Sector sector;
    }

    class Price {
        double price;
        Date validityStart;
        Date validityEnd;
    }

    class Order {
        List<OrderLine> orderLines;
        Date orderDate;
    }

    class OrderLine {
        Seat seat;
        double price;
    }

}
