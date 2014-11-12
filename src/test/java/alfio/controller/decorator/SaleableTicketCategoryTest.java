package alfio.controller.decorator;

import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;

import static com.insightfullogic.lambdabehave.Suite.describe;

@RunWith(JunitSuiteRunner.class)
public class SaleableTicketCategoryTest {{
    describe("SaleableTicketCategory.getAmountOfTickets", it -> {

        it.should("return a range from 0 to 5", expect -> expect.that(SaleableTicketCategory.generateRangeOfTicketQuantity(5,5)).is(new int[]{0,1,2,3,4,5}));
        it.should("return a range from 0 to 1", expect -> expect.that(SaleableTicketCategory.generateRangeOfTicketQuantity(1, 50)).is(new int[]{0,1}));
        it.should("return a range from 0 to 0", expect -> expect.that(SaleableTicketCategory.generateRangeOfTicketQuantity(-1, 50)).is(new int[] {0}));
    });
}}