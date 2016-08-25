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

import alfio.manager.i18n.I18nManager;
import alfio.manager.support.TextTemplateGenerator;
import alfio.model.ContentLanguage;
import alfio.model.Event;
import alfio.model.SpecialPrice;
import alfio.model.TicketCategory;
import alfio.model.modification.SendCodeModification;
import alfio.model.user.Organization;
import alfio.repository.SpecialPriceRepository;
import alfio.util.TemplateManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.MessageSource;

import java.time.ZoneId;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class SpecialPriceManagerTest {

    @Mock
    private EventManager eventManager;
    @Mock
    private Event event;
    @Mock
    private Organization organization;
    @Mock
    private TicketCategory ticketCategory;
    @Mock
    private NotificationManager notificationManager;
    @Mock
    private SpecialPriceRepository specialPriceRepository;
    @Mock
    private TemplateManager templateManager;
    @Mock
    private MessageSource messageSource;
    @Mock
    private I18nManager i18nManager;
    private SpecialPriceManager specialPriceManager;

    @Before
    public void init() {
        List<SpecialPrice> specialPrices = asList(new SpecialPrice(0, "123", 0, 0, "FREE", null, null, null, null), new SpecialPrice(0, "456", 0, 0, "FREE", null, null, null, null));
        when(i18nManager.getEventLanguages(anyInt())).thenReturn(Collections.singletonList(ContentLanguage.ITALIAN));
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("text");
        when(eventManager.getSingleEvent(anyString(), anyString())).thenReturn(event);
        when(eventManager.loadTicketCategories(eq(event))).thenReturn(Collections.singletonList(ticketCategory));
        when(ticketCategory.getId()).thenReturn(0);
        when(specialPriceRepository.findActiveByCategoryId(eq(0))).thenReturn(specialPrices);
        when(eventManager.getEventUrl(eq(event))).thenReturn("http://my-event");
        when(eventManager.loadOrganizer(eq(event), anyString())).thenReturn(organization);
        when(event.getShortName()).thenReturn("eventName");
        when(event.getDisplayName()).thenReturn("Event Name");
        when(event.getLocales()).thenReturn(1);
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
        when(specialPriceRepository.markAsSent(any(), anyString(), anyString(), anyString())).thenReturn(1);
        setRestricted(ticketCategory, true);
        specialPriceManager = new SpecialPriceManager(eventManager, notificationManager, specialPriceRepository, templateManager, messageSource, i18nManager);
    }

    @Test
    public void linkAssigneeToCode() throws Exception {
        testAssigneeLink(specialPriceManager, CODES_NOT_REQUESTED);
        testAssigneeLink(specialPriceManager, CODES_PARTIALLY_REQUESTED);
        testAssigneeLink(specialPriceManager, CODES_REQUESTED);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validationErrorCategoryNotRestricted() throws Exception {
        setRestricted(ticketCategory, false);
        specialPriceManager.linkAssigneeToCode(Collections.emptyList(), "test", 0, "username");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validationErrorTooManyCodesRequested() throws Exception {
        List<SendCodeModification> oneMore = new ArrayList<>();
        oneMore.addAll(CODES_REQUESTED);
        oneMore.add(new SendCodeModification("123", "", "", ""));
        specialPriceManager.linkAssigneeToCode(oneMore, "test", 0, "username");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validationErrorRequestedCodeIsNotAvailable() throws Exception {
        List<SendCodeModification> notExistingCode = asList(new SendCodeModification("AAA", "A 123", "123@123", "it"), new SendCodeModification("456", "A 456", "456@456", "en"));
        specialPriceManager.linkAssigneeToCode(notExistingCode, "test", 0, "username");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validationErrorCodeRequestedTwice() throws Exception {
        List<SendCodeModification> duplicatedCodes = asList(new SendCodeModification("123", "A 123", "123@123", "it"), new SendCodeModification("123", "A 456", "456@456", "en"));
        specialPriceManager.linkAssigneeToCode(duplicatedCodes, "test", 0, "username");
    }

    @Test
    public void sendAllCodes() throws Exception {
        assertTrue(specialPriceManager.sendCodeToAssignee(CODES_REQUESTED, "", 0, ""));
        verify(notificationManager, times(CODES_REQUESTED.size())).sendSimpleEmail(eq(event), anyString(), anyString(), Matchers.any());
    }

    @Test
    public void sendSuccessfulComplete() throws Exception {
        assertTrue(specialPriceManager.sendCodeToAssignee(singletonList(new SendCodeModification("123", "me", "me@domain.com", "it")), "", 0, ""));
        ArgumentCaptor<TextTemplateGenerator> templateCaptor = ArgumentCaptor.forClass(TextTemplateGenerator.class);
        verify(notificationManager).sendSimpleEmail(eq(event), eq("me@domain.com"), anyString(), templateCaptor.capture());
        templateCaptor.getValue().generate();
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(templateManager).renderClassPathResource(endsWith("send-reserved-code-txt.ms"), captor.capture(), eq(Locale.ITALIAN), eq(TemplateManager.TemplateOutput.TEXT));
        Map<String, Object> model = captor.getValue();
        assertEquals("123", model.get("code"));
        assertEquals(event, model.get("event"));
        assertEquals(organization, model.get("organization"));
        assertEquals("http://my-event", model.get("eventPage"));
        assertEquals("me", model.get("assignee"));
        verify(messageSource).getMessage(eq("email-code.subject"), eq(new Object[]{"Event Name"}), eq(Locale.ITALIAN));
    }

    @Test
    public void trimLanguageTag() throws Exception {
        assertTrue(specialPriceManager.sendCodeToAssignee(singletonList(new SendCodeModification("123", "me", "me@domain.com", " it")), "", 0, ""));
        ArgumentCaptor<TextTemplateGenerator> templateCaptor = ArgumentCaptor.forClass(TextTemplateGenerator.class);
        verify(notificationManager).sendSimpleEmail(eq(event), eq("me@domain.com"), anyString(), templateCaptor.capture());
        templateCaptor.getValue().generate();
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(templateManager).renderClassPathResource(endsWith("send-reserved-code-txt.ms"), captor.capture(), eq(Locale.ITALIAN), eq(TemplateManager.TemplateOutput.TEXT));
        Map<String, Object> model = captor.getValue();
        assertEquals("123", model.get("code"));
        assertEquals(event, model.get("event"));
        assertEquals(organization, model.get("organization"));
        assertEquals("http://my-event", model.get("eventPage"));
        assertEquals("me", model.get("assignee"));
        verify(messageSource).getMessage(eq("email-code.subject"), eq(new Object[]{"Event Name"}), eq(Locale.ITALIAN));
    }

    private static void setRestricted(TicketCategory ticketCategory, boolean restricted) {
        when(ticketCategory.isAccessRestricted()).thenReturn(restricted);
    }

    private static void testAssigneeLink(SpecialPriceManager specialPriceManager, List<SendCodeModification> modifications) {
        List<SendCodeModification> sendCodeModifications = specialPriceManager.linkAssigneeToCode(modifications, "test", 0, "username");
        assertFalse(sendCodeModifications.isEmpty());
        assertEquals(2, sendCodeModifications.size());
        sendCodeModifications.forEach(m -> assertEquals("A " + m.getCode(), m.getAssignee()));
    }

    private static final List<SendCodeModification> CODES_REQUESTED = asList(new SendCodeModification("123", "A 123", "123@123", "it"), new SendCodeModification("456", "A 456", "456@456", "en"));
    private static final List<SendCodeModification> CODES_NOT_REQUESTED = asList(new SendCodeModification(null, "A 123", "123@123", "it"), new SendCodeModification(null, "A 456", "456@456", "en"));
    private static final List<SendCodeModification> CODES_PARTIALLY_REQUESTED = asList(new SendCodeModification(null, "A 123", "123@123", "it"), new SendCodeModification("456", "A 456", "456@456", "en"));
}