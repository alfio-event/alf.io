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
import alfio.controller.api.v2.model.EventWithAdditionalInfo;
import alfio.controller.support.Formatters;
import alfio.manager.EuVatChecker;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.Validate;
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

    public Optional<EventWithAdditionalInfo> loadEventInfo(String eventName, HttpSession session) {
        return eventRepository.findOptionalByShortName(eventName).filter(e -> e.getStatus() != Event.Status.DISABLED)//
            .map(event -> {
                //
                var messageSourceAndOverride = messageSourceManager.getMessageSourceForEventAndOverride(event);
                var messageSource = messageSourceAndOverride.getLeft();
                var i18nOverride = messageSourceAndOverride.getRight();

                var descriptions = Formatters.applyCommonMark(eventDescriptionRepository.findDescriptionByEventIdAsMap(event.getId()));

                var organization = organizationRepository.getContactById(event.getOrganizationId());

                var configurationsValues = configurationManager.getFor(List.of(
                    MAPS_PROVIDER,
                    MAPS_CLIENT_API_KEY,
                    MAPS_HERE_API_KEY,
                    RECAPTCHA_API_KEY,
                    BANK_ACCOUNT_NR,
                    BANK_ACCOUNT_OWNER,
                    ENABLE_CUSTOMER_REFERENCE,
                    ENABLE_ITALY_E_INVOICING,
                    VAT_NUMBER_IS_REQUIRED,
                    FORCE_TICKET_OWNER_ASSIGNMENT_AT_RESERVATION,
                    ENABLE_ATTENDEE_AUTOCOMPLETE,
                    ENABLE_TICKET_TRANSFER,
                    DISPLAY_DISCOUNT_CODE_BOX,
                    USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL,
                    GOOGLE_ANALYTICS_KEY,
                    GOOGLE_ANALYTICS_ANONYMOUS_MODE,
                    ENABLE_TAGS_IN_PROMO_CODES,
                    // captcha
                    ENABLE_CAPTCHA_FOR_TICKET_SELECTION,
                    RECAPTCHA_API_KEY,
                    ENABLE_CAPTCHA_FOR_OFFLINE_PAYMENTS,
                    //
                    GENERATE_ONLY_INVOICE,
                    //
                    INVOICE_ADDRESS,
                    VAT_NR,
                    // required by EuVatChecker.reverseChargeEnabled
                    ENABLE_EU_VAT_DIRECTIVE,
                    COUNTRY_OF_BUSINESS,

                    DISPLAY_TICKETS_LEFT_INDICATOR,
                    EVENT_CUSTOM_CSS
                ), ConfigurationLevel.event(event));

                var locationDescriptor = LocationDescriptor.fromGeoData(event.getFormat(), event.getLatLong(), TimeZone.getTimeZone(event.getTimeZone()), configurationsValues);

                //
                boolean captchaForTicketSelection = isRecaptchaForTicketSelectionEnabled(configurationsValues);
                String recaptchaApiKey = null;
                if (captchaForTicketSelection) {
                    recaptchaApiKey = configurationsValues.get(RECAPTCHA_API_KEY).getValueOrNull();
                }
                //
                boolean captchaForOfflinePaymentAndFreeEnabled = configurationManager.isRecaptchaForOfflinePaymentAndFreeEnabled(configurationsValues);
                var captchaConf = new EventWithAdditionalInfo.CaptchaConfiguration(captchaForTicketSelection, captchaForOfflinePaymentAndFreeEnabled, recaptchaApiKey);


                //
                String bankAccount = configurationsValues.get(BANK_ACCOUNT_NR).getValueOrDefault("");
                List<String> bankAccountOwner = Arrays.asList(configurationsValues.get(BANK_ACCOUNT_OWNER).getValueOrDefault("").split("\n"));
                //

                var formattedDates = Formatters.getFormattedDates(event, messageSource, event.getContentLanguages());

                //invoicing information
                boolean canGenerateReceiptOrInvoiceToCustomer = configurationManager.canGenerateReceiptOrInvoiceToCustomer(configurationsValues);
                boolean euVatCheckingEnabled = EuVatChecker.reverseChargeEnabled(configurationsValues);
                boolean invoiceAllowed = configurationManager.hasAllConfigurationsForInvoice(configurationsValues);
                boolean onlyInvoice = invoiceAllowed && configurationManager.isInvoiceOnly(configurationsValues);
                boolean customerReferenceEnabled = configurationsValues.get(ENABLE_CUSTOMER_REFERENCE).getValueAsBooleanOrDefault();
                boolean enabledItalyEInvoicing = configurationsValues.get(ENABLE_ITALY_E_INVOICING).getValueAsBooleanOrDefault();
                boolean vatNumberStrictlyRequired = configurationsValues.get(VAT_NUMBER_IS_REQUIRED).getValueAsBooleanOrDefault();

                var invoicingConf = new EventWithAdditionalInfo.InvoicingConfiguration(canGenerateReceiptOrInvoiceToCustomer,
                    euVatCheckingEnabled, invoiceAllowed, onlyInvoice,
                    customerReferenceEnabled, enabledItalyEInvoicing, vatNumberStrictlyRequired);
                //

                //
                boolean forceAssignment = configurationsValues.get(FORCE_TICKET_OWNER_ASSIGNMENT_AT_RESERVATION).getValueAsBooleanOrDefault();
                boolean enableAttendeeAutocomplete = configurationsValues.get(ENABLE_ATTENDEE_AUTOCOMPLETE).getValueAsBooleanOrDefault();
                boolean enableTicketTransfer = configurationsValues.get(ENABLE_TICKET_TRANSFER).getValueAsBooleanOrDefault();
                var assignmentConf = new EventWithAdditionalInfo.AssignmentConfiguration(forceAssignment, enableAttendeeAutocomplete, enableTicketTransfer);
                //

//<<<<<<< HEAD
                //promoCodes
                boolean hasAccessPromotions = false;
                if (configurationsValues.get(ENABLE_TAGS_IN_PROMO_CODES).getValueAsBooleanOrDefault()) {
                    //new code for carnet management
                    hasAccessPromotions = configurationsValues.get(DISPLAY_DISCOUNT_CODE_BOX).getValueAsBooleanOrDefault() &&
                        (ticketCategoryRepository.countAccessRestrictedRepositoryByEventId(event.getId()) > 0 );
                    if (!hasAccessPromotions){
                        //let's try witth event and organizations (checking tags)
                        var promoDiscounts = promoCodeRepository.getByEventTagsAndOrganizationId(event.getId(), event.getOrganizationId());

                        if (promoDiscounts!= null && promoDiscounts.size() > 0) {
                            var eventMetadata = eventRepository.getMetadataForEvent(event.getId());

                            if (eventMetadata.getTags() != null
                                && eventMetadata.getTags().size() > 0) {
                                for (var promoDiscount : promoDiscounts) {
                                    if (promoDiscount.getAlfioMetadata().getTags() == null || promoDiscount.getAlfioMetadata().getTags().size() == 0) {
                                        //effective discount found, no more check needed
                                        hasAccessPromotions = true;
                                        break;
                                    } else if ((eventMetadata.getAttributes() == null
                                                || eventMetadata.getAttributes().size() == 0)
                                                || !eventMetadata.getAttributes().containsKey(Event.EventOccurrence.CARNET.toString())) {
                                        //I have to check if any event tag match the promo tags
                                        //If event is a carnet cannot be bought with another carnet
                                        for (var tag : eventMetadata.getTags()){
                                            if (promoDiscount.getAlfioMetadata().getTags().contains(tag)){
                                                hasAccessPromotions = true;
                                                break;
                                            }
                                        }
                                        if (hasAccessPromotions) {
                                            break; //force exit, one code found is enought
                                        }
                                    }
                                }
                            }
                        }
                    }

//                            promoCodeRepository.countByEventTagsAndOrganizationId(event.getId(), event.getOrganizationId()) > 0);
                } else {
                    //old way for promo codes (the classic one)
                    hasAccessPromotions = configurationsValues.get(DISPLAY_DISCOUNT_CODE_BOX).getValueAsBooleanOrDefault() &&
                        (ticketCategoryRepository.countAccessRestrictedRepositoryByEventId(event.getId()) > 0 ||
                            promoCodeRepository.countByEventAndOrganizationId(event.getId(), event.getOrganizationId()) > 0);
                }
                boolean usePartnerCode = configurationsValues.get(USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL).getValueAsBooleanOrDefault();
//=======
//
//                //promotion codes
//                boolean hasAccessPromotions = configurationsValues.get(DISPLAY_DISCOUNT_CODE_BOX).getValueAsBooleanOrDefault() &&
//                    (ticketCategoryRepository.countAccessRestrictedRepositoryByEventId(event.getId()) > 0 ||
//                        promoCodeRepository.countByEventAndOrganizationId(event.getId(), event.getOrganizationId()) > 0);
//                boolean usePartnerCode = configurationsValues.get(USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL).getValueAsBooleanOrDefault();
//>>>>>>> 7e28b7decf8894d981715b92272f4124a4461e09
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

                return new EventWithAdditionalInfo(event, locationDescriptor.getMapUrl(), organization, descriptions,
                    bankAccount, bankAccountOwner,
                    formattedDates.beginDate, formattedDates.beginTime,
                    formattedDates.endDate, formattedDates.endTime,
                    invoicingConf, captchaConf, assignmentConf, promoConf, analyticsConf,
                    MessageSourceManager.convertPlaceholdersForEachLanguage(i18nOverride), availableTicketsCount, customCss);
            });
    }

    public boolean isRecaptchaForTicketSelectionEnabled(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configurationValues) {
        Validate.isTrue(configurationValues.containsKey(ENABLE_CAPTCHA_FOR_TICKET_SELECTION) && configurationValues.containsKey(RECAPTCHA_API_KEY));
        return configurationValues.get(ENABLE_CAPTCHA_FOR_TICKET_SELECTION).getValueAsBooleanOrDefault() &&
            configurationValues.get(RECAPTCHA_API_KEY).getValueOrNull() != null;
    }
}
