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

import alfio.model.Event;
import alfio.model.SpecialPrice;
import alfio.model.TicketCategory;
import alfio.model.modification.SendCodeModification;
import alfio.model.user.Organization;
import alfio.repository.SpecialPriceRepository;
import alfio.util.TemplateManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Component
public class SpecialPriceManager {

    private static final Predicate<SendCodeModification> IS_CODE_PRESENT = v -> Optional.ofNullable(v.getCode()).isPresent();
    private final EventManager eventManager;
    private final NotificationManager notificationManager;
    private final SpecialPriceRepository specialPriceRepository;
    private final TemplateManager templateManager;
    private final MessageSource messageSource;

    @Autowired
    public SpecialPriceManager(EventManager eventManager,
                               NotificationManager notificationManager,
                               SpecialPriceRepository specialPriceRepository,
                               TemplateManager templateManager,
                               MessageSource messageSource) {
        this.eventManager = eventManager;
        this.notificationManager = notificationManager;
        this.specialPriceRepository = specialPriceRepository;
        this.templateManager = templateManager;
        this.messageSource = messageSource;
    }

    private List<String> checkCodeAssignment(Set<SendCodeModification> input, int categoryId, Event event, String username) {
        eventManager.checkOwnership(event, username, event.getOrganizationId());
        final List<TicketCategory> categories = eventManager.loadTicketCategories(event);
        final TicketCategory category = categories.stream().filter(tc -> tc.getId() == categoryId).findFirst().orElseThrow(IllegalArgumentException::new);
        Validate.isTrue(category.isAccessRestricted(), "Access to the selected category is not restricted.");
        List<String> availableCodes = new ArrayList<>(specialPriceRepository.findActiveByCategoryId(category.getId()).stream().map(SpecialPrice::getCode).collect(toList()));
        Validate.isTrue(input.size() <= availableCodes.size(), "not enough free codes.");
        List<String> requestedCodes = input.stream().filter(IS_CODE_PRESENT).map(SendCodeModification::getCode).collect(toList());
        Validate.isTrue(requestedCodes.stream().distinct().count() == requestedCodes.size(), "Cannot assign the same code twice. Please fix the input file.");
        Validate.isTrue(requestedCodes.stream().allMatch(availableCodes::contains), "some requested codes don't exist.");
        return availableCodes;
    }

    public List<SendCodeModification> linkAssigneeToCode(List<SendCodeModification> input, String eventName, int categoryId, String username) {
        final Event event = eventManager.getSingleEvent(eventName, username);
        Set<SendCodeModification> set = new LinkedHashSet<>(input);
        List<String> availableCodes = checkCodeAssignment(set, categoryId, event, username);
        final Iterator<String> codes = availableCodes.iterator();
        return Stream.concat(set.stream().filter(IS_CODE_PRESENT),
                input.stream().filter(IS_CODE_PRESENT.negate())
                        .map(p -> new SendCodeModification(codes.next(), p.getAssignee(), p.getEmail(), p.getLanguage()))).collect(toList());
    }

    public boolean sendCodeToAssignee(List<SendCodeModification> input, String eventName, int categoryId, String username) {
        final Event event = eventManager.getSingleEvent(eventName, username);
        final Organization organization = eventManager.loadOrganizer(event, username);
        Set<SendCodeModification> set = new LinkedHashSet<>(input);
        checkCodeAssignment(set, categoryId, event, username);
        Validate.isTrue(set.stream().allMatch(IS_CODE_PRESENT), "There are missing codes. Please check input file.");
        set.forEach(m -> {
            Locale locale = Locale.forLanguageTag(StringUtils.defaultString(m.getLanguage(), "en"));
            Map<String, Object> model = new HashMap<>();
            model.put("code", m.getCode());
            model.put("event", event);
            model.put("organization", organization);
            model.put("eventPage", eventManager.getEventUrl(event));
            model.put("assignee", m.getAssignee());

            notificationManager.sendSimpleEmail(event, m.getEmail(), messageSource.getMessage("email-code.subject", new Object[] {event.getDisplayName()}, locale), () -> templateManager.renderClassPathResource("/alfio/templates/send-reserved-code-txt.ms", model, locale, TemplateManager.TemplateOutput.TEXT));
        });
        return true;
    }
}
