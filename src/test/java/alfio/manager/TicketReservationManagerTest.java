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

import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.repository.TicketRepository;
import alfio.repository.user.AuthorityRepository;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;
import org.springframework.context.MessageSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(JunitSuiteRunner.class)
public class TicketReservationManagerTest {{
    describe("update Ticket owner", it -> {
        final String ticketId = "abcde";
        final String originalEmail = "me@myaddress.com";
        final String originalName = "First Last";
        Ticket original = mock(Ticket.class);
        Ticket modified = mock(Ticket.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        Event event = mock(Event.class);
        UpdateTicketOwnerForm form = new UpdateTicketOwnerForm();
        when(event.getShortName()).thenReturn("short-name");
        NotificationManager notificationManager = it.usesMock(NotificationManager.class);
        MessageSource messageSource = mock(MessageSource.class);
        TicketReservationManager trm = new TicketReservationManager(null, null, ticketRepository, null, null, null, null, null, null, null, notificationManager, messageSource, null, null);

        it.initializesWith(() -> {
            when(original.getUuid()).thenReturn(ticketId);
            when(original.getEmail()).thenReturn(originalEmail);
            when(original.getFullName()).thenReturn(originalName);
            when(ticketRepository.findByUUID(eq(ticketId))).thenReturn(modified);
            form.setEmail("new@email.tld");
            form.setFullName(originalName);
        });

        it.should("not send the warning e-mail if the current user is admin", expect -> {
            UserDetails userDetails = new User("user", "password", Arrays.asList(new SimpleGrantedAuthority(AuthorityRepository.ROLE_ADMIN)));
            trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null,(b) -> null, (c) -> null, Optional.of(userDetails));
            verify(messageSource, never()).getMessage(eq("ticket-has-changed-owner-subject"), eq(new Object[] {"short-name"}), eq(Locale.ENGLISH));
        });

        it.should("send the warning e-mail otherwise", expect -> {
            PartialTicketTextGenerator ownerChangeTextBuilder = it.usesMock(PartialTicketTextGenerator.class);
            when(ownerChangeTextBuilder.generate(eq(modified))).thenReturn("Hello, world");
            trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null, ownerChangeTextBuilder, (c) -> null, Optional.empty());
            verify(messageSource, times(1)).getMessage(eq("ticket-has-changed-owner-subject"), any(), eq(Locale.ENGLISH));
            verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq(originalEmail), anyString(), eq("Hello, world"));
        });
    });
}}