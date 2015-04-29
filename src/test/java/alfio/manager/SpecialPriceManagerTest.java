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

import alfio.manager.support.TextTemplateGenerator;
import alfio.model.Event;
import alfio.model.SpecialPrice;
import alfio.model.TicketCategory;
import alfio.model.modification.SendCodeModification;
import alfio.model.user.Organization;
import alfio.repository.SpecialPriceRepository;
import alfio.util.TemplateManager;
import com.insightfullogic.lambdabehave.Block;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import com.insightfullogic.lambdabehave.specifications.Specification;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.springframework.context.MessageSource;

import java.util.*;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.mockito.Mockito.*;

@RunWith(JunitSuiteRunner.class)
@SuppressWarnings("unchecked")
public class SpecialPriceManagerTest {{

    describe("SpecialPriceManager", a -> {
        List<SpecialPrice> specialPrices = asList(new SpecialPrice(0, "123", 0, 0, "FREE", null), new SpecialPrice(0, "456", 0, 0, "FREE", null));
        EventManager eventManager = a.usesMock(EventManager.class);
        Event event = a.usesMock(Event.class);
        Organization organization = a.usesMock(Organization.class);
        TicketCategory ticketCategory = a.usesMock(TicketCategory.class);
        NotificationManager notificationManager = a.usesMock(NotificationManager.class);
        SpecialPriceRepository specialPriceRepository = a.usesMock(SpecialPriceRepository.class);
        TemplateManager templateManager = a.usesMock(TemplateManager.class);
        MessageSource messageSource = a.usesMock(MessageSource.class);
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("text");
        when(eventManager.getSingleEvent(anyString(), anyString())).thenReturn(event);
        when(eventManager.loadTicketCategories(eq(event))).thenReturn(Collections.singletonList(ticketCategory));
        when(ticketCategory.getId()).thenReturn(0);
        when(specialPriceRepository.findActiveByCategoryId(eq(0))).thenReturn(specialPrices);
        when(eventManager.getEventUrl(eq(event))).thenReturn("http://my-event");
        when(eventManager.loadOrganizer(eq(event), anyString())).thenReturn(organization);
        when(event.getShortName()).thenReturn("eventName");

        final SpecialPriceManager specialPriceManager = new SpecialPriceManager(eventManager, notificationManager, specialPriceRepository, templateManager, messageSource);

        describe("linkAssigneeToCode", b -> {
            describe("success code assignment", it -> {
                it.initializesWith(setRestricted(ticketCategory, true));
                it.should("link assignees to codes (empty request)", testAssigneeLink(specialPriceManager, CODES_NOT_REQUESTED));
                it.should("link assignees to codes (partial request)", testAssigneeLink(specialPriceManager, CODES_PARTIALLY_REQUESTED));
                it.should("link assignees to codes (full request)", testAssigneeLink(specialPriceManager, CODES_REQUESTED));
            });

            describe("validation error (category not restricted)", it -> {
                it.initializesWith(setRestricted(ticketCategory, false));
                it.should("throw an exception (category not restricted)", expect -> {
                    expect.exception(IllegalArgumentException.class, () -> specialPriceManager.linkAssigneeToCode(Collections.<SendCodeModification>emptyList(), "test", 0, "username"));
                });
            });

            describe("validation error (too much codes requested)", it -> {
                it.initializesWith(setRestricted(ticketCategory, true));
                it.should("throw an exception (too much codes requested)", expect -> {
                    List<SendCodeModification> oneMore = new ArrayList<>();
                    oneMore.addAll(CODES_REQUESTED);
                    oneMore.add(new SendCodeModification("123", "", "", ""));
                    expect.exception(IllegalArgumentException.class, () -> specialPriceManager.linkAssigneeToCode(oneMore, "test", 0, "username"));
                });
            });

            describe("validation error (not available code requested)", it -> {
                it.initializesWith(setRestricted(ticketCategory, true));
                it.should("throw an exception (not available code requested)", expect -> {
                    List<SendCodeModification> notExistingCode = asList(new SendCodeModification("AAA", "A 123", "123@123", "it"), new SendCodeModification("456", "A 456", "456@456", "en"));
                    expect.exception(IllegalArgumentException.class, () -> specialPriceManager.linkAssigneeToCode(notExistingCode, "test", 0, "username"));
                });
            });

            describe("validation error (code requested twice)", it -> {
                it.initializesWith(setRestricted(ticketCategory, true));
                it.should("throw an exception (code requested twice)", expect -> {
                    List<SendCodeModification> duplicatedCodes = asList(new SendCodeModification("123", "A 123", "123@123", "it"), new SendCodeModification("123", "A 456", "456@456", "en"));
                    expect.exception(IllegalArgumentException.class, () -> specialPriceManager.linkAssigneeToCode(duplicatedCodes, "test", 0, "username"));
                });
            });
        });

        describe("sendCodeToAssignee", b -> {
            describe("send successful", it -> {
                it.initializesWith(setRestricted(ticketCategory, true));
                it.should("send all the codes", expect -> {
                    expect.that(specialPriceManager.sendCodeToAssignee(CODES_REQUESTED, "", 0, "")).is(true);
                    verify(notificationManager, times(CODES_REQUESTED.size())).sendSimpleEmail(eq(event), anyString(), anyString(), Matchers.<TextTemplateGenerator>any());
                });
                it.isConcludedWith(() -> reset(notificationManager));//Really don't like it, but it would be far worse to re-initialize all the mocks...
            });

            describe("send successful - detailed test", it -> {
                it.initializesWith(setRestricted(ticketCategory, true));
                it.should("send the given code", expect -> {
                    expect.that(specialPriceManager.sendCodeToAssignee(singletonList(new SendCodeModification("123", "me", "me@domain.com", "it")), "", 0, "")).is(true);
                    ArgumentCaptor<TextTemplateGenerator> templateCaptor = ArgumentCaptor.forClass(TextTemplateGenerator.class);
                    verify(notificationManager).sendSimpleEmail(eq(event), eq("me@domain.com"), anyString(), templateCaptor.capture());
                    templateCaptor.getValue().generate();
                    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
                    verify(templateManager).renderClassPathResource(endsWith("send-reserved-code-txt.ms"), captor.capture(), eq(Locale.ITALIAN), eq(TemplateManager.TemplateOutput.TEXT));
                    Map<String, Object> model = captor.getValue();
                    expect.that(model.get("code")).isEqualTo("123");
                    expect.that(model.get("event")).isEqualTo(event);
                    expect.that(model.get("organization")).isEqualTo(organization);
                    expect.that(model.get("eventPage")).isEqualTo("http://my-event");
                    expect.that(model.get("assignee")).isEqualTo("me");
                    verify(messageSource).getMessage(eq("email-code.subject"), eq(new Object[]{"eventName"}), eq(Locale.ITALIAN));
                });
                it.isConcludedWith(() -> reset(notificationManager));
            });
        });

    });
}

    private static Block setRestricted(TicketCategory ticketCategory, boolean restricted) {
        return () -> when(ticketCategory.isAccessRestricted()).thenReturn(restricted);
    }

    private static Specification testAssigneeLink(SpecialPriceManager specialPriceManager, List<SendCodeModification> modifications) {
        return expect -> {
            List<SendCodeModification> sendCodeModifications = specialPriceManager.linkAssigneeToCode(modifications, "test", 0, "username");
            expect.that(sendCodeModifications.isEmpty()).isEqualTo(false);
            expect.that(sendCodeModifications.size()).isEqualTo(2);
            sendCodeModifications.forEach(m -> expect.that(m.getAssignee()).isEqualTo("A " + m.getCode()));
        };
    }

    private static final List<SendCodeModification> CODES_REQUESTED = asList(new SendCodeModification("123", "A 123", "123@123", "it"), new SendCodeModification("456", "A 456", "456@456", "en"));
    private static final List<SendCodeModification> CODES_NOT_REQUESTED = asList(new SendCodeModification(null, "A 123", "123@123", "it"), new SendCodeModification(null, "A 456", "456@456", "en"));
    private static final List<SendCodeModification> CODES_PARTIALLY_REQUESTED = asList(new SendCodeModification(null, "A 123", "123@123", "it"), new SendCodeModification("456", "A 456", "456@456", "en"));
}