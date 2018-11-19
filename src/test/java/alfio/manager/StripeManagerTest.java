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
package alfio.manager;

import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.model.CustomerName;
import alfio.model.Event;
import alfio.model.transaction.token.StripeCreditCardToken;
import alfio.repository.AuditingRepository;
import alfio.repository.TicketRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.user.UserRepository;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StripeManagerTest {

    private TransactionRepository transactionRepository;
    private ConfigurationManager configurationManager;
    private AuditingRepository auditingRepository;
    private UserRepository userRepository;
    private TicketRepository ticketRepository;
    private Event event;
    private CustomerName customerName;

    private final String paymentId = "customer#1";
    private final String error = "errorCode";

    @BeforeEach
    public void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        configurationManager = mock(ConfigurationManager.class);
        auditingRepository = mock(AuditingRepository.class);
        userRepository = mock(UserRepository.class);
        ticketRepository = mock(TicketRepository.class);
        event = mock(Event.class);
        customerName = mock(CustomerName.class);
        when(customerName.getFullName()).thenReturn("ciccio");
    }

    @Test
    public void successFlow() throws StripeException {
        StripeCreditCardManager stripeCreditCardManager = new StripeCreditCardManager( configurationManager, ticketRepository, transactionRepository, null, mock(Environment.class ) ) {
            @Override
            protected Optional<Charge> charge( Event event, Map<String, Object> chargeParams ) throws StripeException {
                return Optional.of( new Charge() {{
                    setId(paymentId);
                    setDescription("description");
                }});
            }
        };
        PaymentSpecification spec = new PaymentSpecification( "", new StripeCreditCardToken(""), 100, event, "", customerName );
        PaymentResult result = stripeCreditCardManager.doPayment(spec);
        assertEquals(result, PaymentResult.successful(paymentId));
    }

    @Test
    void stripeError() {
        StripeCreditCardManager stripeCreditCardManager = new StripeCreditCardManager( configurationManager, ticketRepository, transactionRepository, null, mock(Environment.class ) ) {
            @Override
            protected Optional<Charge> charge( Event event, Map<String, Object> chargeParams ) throws StripeException {
                throw new AuthenticationException("401", "42", "401", 401);
            }
        };
        PaymentSpecification spec = new PaymentSpecification( "", new StripeCreditCardToken(""), 100, event, "", customerName );
        PaymentResult result = stripeCreditCardManager.doPayment(spec);
        assertEquals(result, PaymentResult.failed("error.STEP2_STRIPE_abort"));
    }

    @Test
    public void internalError() throws StripeException {
        StripeCreditCardManager stripeCreditCardManager = new StripeCreditCardManager( configurationManager, ticketRepository, transactionRepository, null, mock(Environment.class ) ) {
            @Override
            protected Optional<Charge> charge( Event event, Map<String, Object> chargeParams ) throws StripeException {
                return Optional.of( new Charge() {{
                    setId(paymentId);
                    setDescription("description");
                }});
            }
        };
        when(event.getCurrency()).thenReturn("CHF");
        when(transactionRepository.insert(anyString(), isNull(), anyString(), any(ZonedDateTime.class), anyInt(), eq("CHF"), anyString(), anyString(), anyLong(), anyLong()))
            .thenThrow(new NullPointerException());

        PaymentSpecification spec = new PaymentSpecification( "", new StripeCreditCardToken(""), 100, event, "", customerName );
        Assertions.assertThrows(IllegalStateException.class, () -> stripeCreditCardManager.doPayment(spec));
    }
}