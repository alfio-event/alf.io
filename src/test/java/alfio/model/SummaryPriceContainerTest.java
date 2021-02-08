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
package alfio.model;

import alfio.model.decorator.TicketPriceContainer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
		when(ticket.getNetPrice()).thenCallRealMethod();
		
		return ticket;
	}
	
	private TicketPriceContainer vatExcludedPriceContainer() {
		var ticket = createPriceContainer();
		when(ticket.getVatStatus()).thenReturn(PriceContainer.VatStatus.NOT_INCLUDED);
        when(ticket.getNetPrice()).thenCallRealMethod();
		
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
