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
import alfio.manager.system.ConfigurationManager;
import alfio.model.ContentLanguage;
import alfio.model.Event;
import alfio.model.SpecialPrice;
import alfio.model.TicketCategory;
import alfio.model.modification.SendCodeModification;
import alfio.model.system.Configuration;
import alfio.model.user.Organization;
import alfio.repository.SpecialPriceRepository;
import alfio.util.TemplateManager;
import alfio.util.TemplateResource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;

import java.time.ZoneId;
import java.util.*;

import static alfio.model.system.ConfigurationKeys.USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class SpecialPriceManagerTest {

    private EventManager eventManager;
    private Event event;
    private Organization organization;
    private TicketCategory ticketCategory;
    private NotificationManager notificationManager;
    private SpecialPriceRepository specialPriceRepository;
    private TemplateManager templateManager;
    private MessageSource messageSource;
    private I18nManager i18nManager;
    private SpecialPriceManager specialPriceManager;
    private ConfigurationManager configurationManager;

    @BeforeEach
    public void init() {
        eventManager = mock(EventManager.class);
        event = mock(Event.class);
        organization = mock(Organization.class);
        ticketCategory = mock(TicketCategory.class);
        notificationManager = mock(NotificationManager.class);
        specialPriceRepository = mock(SpecialPriceRepository.class);
        templateManager = mock(TemplateManager.class);
        messageSource = mock(MessageSource.class);
        i18nManager = mock(I18nManager.class);
        configurationManager = mock(ConfigurationManager.class);

        List<SpecialPrice> specialPrices = asList(new SpecialPrice(0, "123", 0, 0, "FREE", null, null, null, null, null), new SpecialPrice(0, "456", 0, 0, "FREE", null, null, null, null, null));
        when(i18nManager.getEventLanguages(anyInt())).thenReturn(Collections.singletonList(ContentLanguage.ITALIAN));
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("text");
        when(eventManager.getSingleEvent(anyString(), anyString())).thenReturn(event);
        when(eventManager.getEventAndOrganizationId(anyString(), anyString())).thenReturn(event);
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
        specialPriceManager = new SpecialPriceManager(eventManager, notificationManager, specialPriceRepository, templateManager, messageSource, i18nManager, configurationManager);
    }

    @Test
    public void linkAssigneeToCode() throws Exception {
        testAssigneeLink(specialPriceManager, CODES_NOT_REQUESTED);
        testAssigneeLink(specialPriceManager, CODES_PARTIALLY_REQUESTED);
        testAssigneeLink(specialPriceManager, CODES_REQUESTED);
    }

    @Test
    public void validationErrorCategoryNotRestricted() throws Exception {
        setRestricted(ticketCategory, false);
        Assertions.assertThrows(IllegalArgumentException.class, () -> specialPriceManager.linkAssigneeToCode(Collections.emptyList(), "test", 0, "username"));
    }

    @Test
    public void validationErrorTooManyCodesRequested() throws Exception {
        List<SendCodeModification> oneMore = new ArrayList<>(CODES_REQUESTED);
        oneMore.add(new SendCodeModification("123", "", "", ""));
        Assertions.assertThrows(IllegalArgumentException.class, () -> specialPriceManager.linkAssigneeToCode(oneMore, "test", 0, "username"));
    }

    @Test
    public void validationErrorRequestedCodeIsNotAvailable() throws Exception {
        List<SendCodeModification> notExistingCode = asList(new SendCodeModification("AAA", "A 123", "123@123", "it"), new SendCodeModification("456", "A 456", "456@456", "en"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> specialPriceManager.linkAssigneeToCode(notExistingCode, "test", 0, "username"));
    }

    @Test
    public void validationErrorCodeRequestedTwice() throws Exception {
        List<SendCodeModification> duplicatedCodes = asList(new SendCodeModification("123", "A 123", "123@123", "it"), new SendCodeModification("123", "A 456", "456@456", "en"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> specialPriceManager.linkAssigneeToCode(duplicatedCodes, "test", 0, "username"));
    }

    @Test
    public void sendAllCodes() throws Exception {
        assertTrue(specialPriceManager.sendCodeToAssignee(CODES_REQUESTED, "", 0, ""));
        verify(notificationManager, times(CODES_REQUESTED.size())).sendSimpleEmail(eq(event), isNull(), anyString(), anyString(), any());
    }

    @Test
    public void sendSuccessfulComplete() throws Exception {
        sendMessage(null);
    }

    @Test
    public void trimLanguageTag() throws Exception {
        assertTrue(specialPriceManager.sendCodeToAssignee(singletonList(new SendCodeModification("123", "me", "me@domain.com", " it")), "", 0, ""));
        ArgumentCaptor<TextTemplateGenerator> templateCaptor = ArgumentCaptor.forClass(TextTemplateGenerator.class);
        verify(notificationManager).sendSimpleEmail(eq(event), isNull(), eq("me@domain.com"), anyString(), templateCaptor.capture());
        templateCaptor.getValue().generate();
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(templateManager).renderTemplate(any(Event.class), eq(TemplateResource.SEND_RESERVED_CODE), captor.capture(), eq(Locale.ITALIAN));
        Map<String, Object> model = captor.getValue();
        assertEquals("123", model.get("code"));
        assertEquals(event, model.get("event"));
        assertEquals(organization, model.get("organization"));
        assertEquals("http://my-event", model.get("eventPage"));
        assertEquals("me", model.get("assignee"));
        verify(messageSource).getMessage(eq("email-code.subject"), eq(new Object[]{"Event Name", null}), eq(Locale.ITALIAN));
    }

    @Test
    void usePartnerCode() {
        when(messageSource.getMessage(eq("show-event.promo-code-type.partner"), isNull(), isNull(), any())).thenReturn("Partner");
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event, USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL)), eq(false))).thenReturn(true);
        sendMessage("Partner");
    }

    private void sendMessage(String promoCodeDescription) {
        assertTrue(specialPriceManager.sendCodeToAssignee(singletonList(new SendCodeModification("123", "me", "me@domain.com", "it")), "", 0, ""));
        ArgumentCaptor<TextTemplateGenerator> templateCaptor = ArgumentCaptor.forClass(TextTemplateGenerator.class);
        verify(notificationManager).sendSimpleEmail(eq(event), isNull(), eq("me@domain.com"), anyString(), templateCaptor.capture());
        templateCaptor.getValue().generate();
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(templateManager).renderTemplate(any(Event.class), eq(TemplateResource.SEND_RESERVED_CODE), captor.capture(), eq(Locale.ITALIAN));
        Map<String, Object> model = captor.getValue();
        assertEquals("123", model.get("code"));
        assertEquals(event, model.get("event"));
        assertEquals(organization, model.get("organization"));
        assertEquals("http://my-event", model.get("eventPage"));
        assertEquals("me", model.get("assignee"));
        verify(messageSource).getMessage(eq("email-code.subject"), eq(new Object[]{"Event Name", promoCodeDescription}), eq(Locale.ITALIAN));
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