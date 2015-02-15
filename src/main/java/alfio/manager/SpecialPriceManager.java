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

    public List<SendCodeModification> linkAssigneeToCode(Set<SendCodeModification> input, String eventName, int categoryId, String username) {
        final Event event = eventManager.getSingleEvent(eventName, username);
        List<String> availableCodes = checkCodeAssignment(input, categoryId, event, username);
        final Iterator<String> codes = availableCodes.iterator();
        return Stream.concat(input.stream().filter(IS_CODE_PRESENT),
                input.stream().filter(IS_CODE_PRESENT.negate())
                        .map(p -> new SendCodeModification(codes.next(), p.getAssignee(), p.getEmail(), p.getLanguage()))).collect(toList());
    }

    public boolean sendCodeToAssignee(Set<SendCodeModification> input, String eventName, int categoryId, String username) {
        final Event event = eventManager.getSingleEvent(eventName, username);
        final Organization organization = eventManager.loadOrganizer(event, username);
        checkCodeAssignment(input, categoryId, event, username);
        Validate.isTrue(input.stream().allMatch(IS_CODE_PRESENT), "There are missing codes. Please check input file.");
        input.forEach(m -> {
            Locale locale = Locale.forLanguageTag(StringUtils.defaultString(m.getLanguage(), "en"));
            Map<String, Object> model = new HashMap<>();
            model.put("code", m.getCode());
            model.put("event", event);
            model.put("organization", organization);
            model.put("eventPage", eventManager.getEventUrl(event));
            model.put("assignee", m.getAssignee());

            notificationManager.sendSimpleEmail(event, m.getEmail(), messageSource.getMessage("email-code.subject", new Object[] {event.getShortName()}, locale), () -> templateManager.renderClassPathResource("/alfio/templates/send-reserved-code-txt.ms", model, locale));
        });
        return true;
    }
}
