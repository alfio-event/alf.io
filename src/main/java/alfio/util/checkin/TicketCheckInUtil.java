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
package alfio.util.checkin;

import alfio.manager.ExtensionManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import lombok.experimental.UtilityClass;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Supplier;

@UtilityClass
public class TicketCheckInUtil {

    public static final String CUSTOM_CHECK_IN_URL = "customCheckInUrl";
    public static final String ONLINE_CHECK_IN_URL = "onlineCheckInUrl";
    public static final String CUSTOM_CHECK_IN_URL_TEXT = "customCheckInUrlText";
    public static final String CUSTOM_CHECK_IN_URL_DESCRIPTION = "customCheckInUrlDescription";

    public static String ticketOnlineCheckInUrl(Event event, Ticket ticket, String baseUrl) {
        var ticketCode = DigestUtils.sha256Hex(ticket.ticketCode(event.getPrivateKey(), event.supportsQRCodeCaseInsensitive()));
        return StringUtils.removeEnd(baseUrl, "/")
            + "/event/" + event.getShortName() + "/ticket/" + ticket.getUuid() + "/check-in/"+ticketCode;
    }

    public static Map<String, String> getOnlineCheckInInfo(ExtensionManager extensionManager,
                                                           EventRepository eventRepository,
                                                           TicketCategoryRepository ticketCategoryRepository,
                                                           ConfigurationManager configurationManager,
                                                           Event event,
                                                           Locale ticketLanguage,
                                                           Ticket ticket,
                                                           TicketCategory ticketCategory,
                                                           Map<String, List<String>> ticketAdditionalInfo) {
        var result = new HashMap<String, String>();
        var customMetadataOptional = extensionManager.handleCustomOnlineJoinUrl(event, ticket, ticketAdditionalInfo);
        result.put(CUSTOM_CHECK_IN_URL, Boolean.toString(customMetadataOptional.isPresent()));
        if(customMetadataOptional.isPresent()) {
            var ticketMetadata = customMetadataOptional.get();
            var joinLink = ticketMetadata.getJoinLink();
            result.put(ONLINE_CHECK_IN_URL, joinLink.getLink());
            if(joinLink.hasLinkText()) {
                result.put(CUSTOM_CHECK_IN_URL_TEXT, joinLink.getLocalizedText(ticketLanguage.getLanguage(), event));
            }
            var linkDescription = ticketMetadata.getLocalizedDescription(ticketLanguage.getLanguage(), event);
            result.put(CUSTOM_CHECK_IN_URL_DESCRIPTION, linkDescription);
            result.put("prerequisites", "");
        } else {
            Supplier<Optional<String>> eventMetadata = () -> Optional.ofNullable(eventRepository.getMetadataForEvent(event.getId()).getRequirementsDescriptions()).flatMap(m -> Optional.ofNullable(m.get(ticketLanguage.getLanguage())));
            var categoryMetadata = Optional.ofNullable(ticketCategoryRepository.getMetadata(event.getId(), ticketCategory.getId()).getRequirementsDescriptions()).flatMap(m -> Optional.ofNullable(m.get(ticketLanguage.getLanguage())));
            result.put(ONLINE_CHECK_IN_URL, ticketOnlineCheckInUrl(event, ticket, configurationManager.baseUrl(event)));
            result.put("prerequisites", categoryMetadata.or(eventMetadata).orElse(""));
        }
        return result;
    }
}
