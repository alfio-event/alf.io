package alfio.model;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import alfio.model.decorator.TicketPriceContainer;

public class SummaryPriceContainerTest {

	@Test
	public void testSummaryPriceBeforeVatIncluded() {
		List<TicketPriceContainer> items = IntStream.range(0, 10)
				.mapToObj(i -> vatIncludedPriceContainer()).collect(Collectors.toList());
		
		int summaryPrice = SummaryPriceContainer.getSummaryPriceBeforeVatCts(items);
		
		assertThat(summaryPrice, is(equalTo(8264)));
	}
	
	@Test
	public void testSummaryPriceBeforeVatExcluded() {
		List<TicketPriceContainer> items = IntStream.range(0, 10)
				.mapToObj(i -> vatExcludedPriceContainer()).collect(Collectors.toList());
		
		int summaryPrice = SummaryPriceContainer.getSummaryPriceBeforeVatCts(items);
		
		assertThat(summaryPrice, is(equalTo(10000)));
	}
	
	private TicketPriceContainer vatIncludedPriceContainer() {
		var ticket = createPriceContainer();
		when(ticket.getVatStatus()).thenReturn(PriceContainer.VatStatus.INCLUDED);
		
		return ticket;
	}
	
	private TicketPriceContainer vatExcludedPriceContainer() {
		var ticket = createPriceContainer();
		when(ticket.getVatStatus()).thenReturn(PriceContainer.VatStatus.NOT_INCLUDED);
		
		return ticket;
	}
	
	private TicketPriceContainer createPriceContainer() {
		var ticket = mock(TicketPriceContainer.class);
		when(ticket.getCurrencyCode()).thenReturn("EUR");
		when(ticket.getSrcPriceCts()).thenReturn(1000);
		when(ticket.getVatPercentageOrZero()).thenReturn(new BigDecimal("21.00"));
		
		return ticket;
	}
}
