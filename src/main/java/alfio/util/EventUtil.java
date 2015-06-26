package alfio.util;

import alfio.controller.decorator.SaleableTicketCategory;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import lombok.experimental.UtilityClass;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@UtilityClass
public class EventUtil {

    public static boolean displaySoldOutWarning(Event event, List<SaleableTicketCategory> categories, ConfigurationManager configurationManager) {
        if(!configurationManager.getBooleanConfigValue(Configuration.event(event), ConfigurationKeys.ENABLE_WAITING_QUEUE, false)) {
            return false;
        }
        Predicate<List<SaleableTicketCategory>> isEmpty = List::isEmpty;
        Optional<SaleableTicketCategory> lastCategory = Optional.ofNullable(categories).filter(isEmpty.negate()).map(l -> l.get(l.size() - 1));
        ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
        return lastCategory.map(category -> now.isBefore(category.getZonedExpiration()) && categories.stream().noneMatch(c -> c.getAvailableTickets() > 0)).orElse(false);
    }
}
