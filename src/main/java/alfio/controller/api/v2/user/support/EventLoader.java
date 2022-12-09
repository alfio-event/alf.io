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
package alfio.controller.api.v2.user.support;

import alfio.controller.api.v2.model.AnalyticsConfiguration;
import alfio.controller.api.v2.model.EmbeddingConfiguration;
import alfio.controller.api.v2.model.EventWithAdditionalInfo;
import alfio.controller.api.v2.model.OfflinePaymentConfiguration;
import alfio.controller.support.Formatters;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpSession;
import java.util.*;

import static alfio.model.system.ConfigurationKeys.*;

@Component
@AllArgsConstructor
public class EventLoader {

    private final EventRepository eventRepository;
    private final MessageSourceManager messageSourceManager;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final OrganizationRepository organizationRepository;
    private final ConfigurationManager configurationManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final PromoCodeDiscountRepository promoCodeRepository;
    private final SubscriptionRepository subscriptionRepository;

    public Optional<EventWithAdditionalInfo> loadEventInfo(String eventName, HttpSession session) {
        return eventRepository.findOptionalByShortName(eventName).filter(e -> e.getStatus() != Event.Status.DISABLED)//
            .map(event -> {
                //
                var messageSourceAndOverride = messageSourceManager.getMessageSourceForPurchaseContextAndOverride(event);
                var messageSource = messageSourceAndOverride.getLeft();
                var i18nOverride = messageSourceAndOverride.getRight();

                var descriptions = Formatters.applyCommonMark(eventDescriptionRepository.findDescriptionByEventIdAsMap(event.getId()), messageSource);

                var organization = organizationRepository.getContactById(event.getOrganizationId());

                var configurationsValues = PurchaseContextInfoBuilder.configurationsValues(event, configurationManager);

                var locationDescriptor = LocationDescriptor.fromGeoData(event.getFormat(), event.getLatLong(), TimeZone.getTimeZone(event.getTimeZone()), configurationsValues);

                //
                var captchaConf = PurchaseContextInfoBuilder.captchaConfiguration(configurationManager, configurationsValues);


                //
                String bankAccount = configurationsValues.get(BANK_ACCOUNT_NR).getValueOrDefault("");
                List<String> bankAccountOwner = Arrays.asList(configurationsValues.get(BANK_ACCOUNT_OWNER).getValueOrDefault("").split("\n"));
                //

                var formattedDates = Formatters.getFormattedDates(event, messageSource, event.getContentLanguages());

                //invoicing information
                var invoicingConf = PurchaseContextInfoBuilder.invoicingInfo(configurationManager, configurationsValues);
                //

                //
                boolean forceAssignment = configurationsValues.get(FORCE_TICKET_OWNER_ASSIGNMENT_AT_RESERVATION).getValueAsBooleanOrDefault();
                boolean enableAttendeeAutocomplete = configurationsValues.get(ENABLE_ATTENDEE_AUTOCOMPLETE).getValueAsBooleanOrDefault();
                boolean enableTicketTransfer = configurationsValues.get(ENABLE_TICKET_TRANSFER).getValueAsBooleanOrDefault();
                var assignmentConf = new EventWithAdditionalInfo.AssignmentConfiguration(forceAssignment, enableAttendeeAutocomplete, enableTicketTransfer);
                //


                //promotion codes
                boolean hasAccessPromotions = configurationsValues.get(DISPLAY_DISCOUNT_CODE_BOX).getValueAsBooleanOrDefault() &&
                    (ticketCategoryRepository.countAccessRestrictedRepositoryByEventId(event.getId()) > 0 ||
                        promoCodeRepository.countByEventAndOrganizationId(event.getId(), event.getOrganizationId()) > 0);
                boolean usePartnerCode = configurationsValues.get(USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL).getValueAsBooleanOrDefault();
                var promoConf = new EventWithAdditionalInfo.PromotionsConfiguration(hasAccessPromotions, usePartnerCode);
                //

                //analytics configuration
                var analyticsConf = AnalyticsConfiguration.build(configurationsValues, session);
                //

                Integer availableTicketsCount = null;
                if (configurationsValues.get(DISPLAY_TICKETS_LEFT_INDICATOR).getValueAsBooleanOrDefault()) {
                    availableTicketsCount = ticketRepository.countFreeTicketsForPublicStatistics(event.getId());
                }

                var customCss = configurationsValues.get(EVENT_CUSTOM_CSS).getValueOrNull();

                var hasLinkedSubscription = subscriptionRepository.hasLinkedSubscription(event.getId());

                var offlinePaymentConfiguration = new OfflinePaymentConfiguration(configurationsValues.get(SHOW_ONLY_BASIC_INSTRUCTIONS).getValueAsBooleanOrDefault());

                return new EventWithAdditionalInfo(event, locationDescriptor.getMapUrl(), organization, descriptions,
                    bankAccount, bankAccountOwner,
                    formattedDates.beginDate, formattedDates.beginTime,
                    formattedDates.endDate, formattedDates.endTime,
                    invoicingConf, captchaConf, assignmentConf, promoConf, analyticsConf, offlinePaymentConfiguration,
                    new EmbeddingConfiguration(configurationsValues.get(EMBED_POST_MESSAGE_ORIGIN).getValueOrNull()),
                    MessageSourceManager.convertPlaceholdersForEachLanguage(i18nOverride), availableTicketsCount, customCss, hasLinkedSubscription);
            });
    }

    public boolean isRecaptchaForTicketSelectionEnabled(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configurationValues) {
        return PurchaseContextInfoBuilder.isRecaptchaForTicketSelectionEnabled(configurationValues);
    }
}
