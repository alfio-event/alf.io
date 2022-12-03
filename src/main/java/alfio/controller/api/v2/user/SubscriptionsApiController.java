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
package alfio.controller.api.v2.user;

import alfio.controller.api.support.CurrencyDescriptor;
import alfio.controller.api.v2.model.*;
import alfio.controller.api.v2.user.support.PurchaseContextInfoBuilder;
import alfio.controller.form.SearchOptions;
import alfio.controller.support.Formatters;
import alfio.manager.SubscriptionManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.i18n.I18nManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.support.response.ValidatedResponse;
import alfio.manager.system.ConfigurationManager;
import alfio.model.result.ValidationResult;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ClockProvider;
import alfio.util.MonetaryUtil;
import lombok.AllArgsConstructor;
import org.joda.money.CurrencyUnit;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.PriceContainer.VatStatus.isVatIncluded;
import static alfio.model.system.ConfigurationKeys.*;
import static java.util.stream.Collectors.toList;

@RestController
@RequestMapping("/api/v2/public/")
@AllArgsConstructor
public class SubscriptionsApiController {

    private static final String DATE_FORMAT_KEY = "common.event.date-format";
    private final SubscriptionManager subscriptionManager;
    private final I18nManager i18nManager;
    private final TicketReservationManager reservationManager;
    private final ConfigurationManager configurationManager;
    private final OrganizationRepository organizationRepository;
    private final MessageSourceManager messageSourceManager;
    private final ClockProvider clockProvider;


    @GetMapping("subscriptions")
    public ResponseEntity<List<BasicSubscriptionDescriptorInfo>> listSubscriptions(SearchOptions searchOptions) {
        var now = ZonedDateTime.now(ClockProvider.clock());
        var activeSubscriptions = subscriptionManager.getActivePublicSubscriptionsDescriptor(now, searchOptions)
            .stream()
            .map(s -> subscriptionDescriptorMapper(s, messageSourceManager.getMessageSourceFor(s)))
            .collect(Collectors.toList());
        return ResponseEntity.ok(activeSubscriptions);
    }

    private static BasicSubscriptionDescriptorInfo subscriptionDescriptorMapper(SubscriptionDescriptor s, MessageSource messageSource) {
        var currencyUnit = CurrencyUnit.of(s.getCurrency());
        var currencyDescriptor = new CurrencyDescriptor(currencyUnit.getCode(), currencyUnit.toCurrency().getDisplayName(), currencyUnit.getSymbol(), currencyUnit.getDecimalPlaces());
        return new BasicSubscriptionDescriptorInfo(s.getId(),
            s.getFileBlobId(),
            s.getTitle(),
            s.getDescription(),
            DatesWithTimeZoneOffset.fromDates(s.getOnSaleFrom(), s.getOnSaleTo()),
            s.getValidityType(),
            s.getUsageType(),
            s.getZoneId(),
            s.getValidityTimeUnit(),
            s.getValidityUnits(),
            s.getMaxEntries(),

            null,
            null,

            MonetaryUtil.formatCents(s.getPrice(), s.getCurrency()),
            s.getCurrency(),
            currencyDescriptor,
            s.getVat(),
            isVatIncluded(s.getVatStatus()),
            Formatters.getFormattedDate(s, s.getOnSaleFrom(), DATE_FORMAT_KEY, messageSource),
            Formatters.getFormattedDate(s, s.getOnSaleTo(), DATE_FORMAT_KEY, messageSource),
            Formatters.getFormattedDate(s, s.getValidityFrom(), DATE_FORMAT_KEY, messageSource),
            Formatters.getFormattedDate(s, s.getValidityTo(), DATE_FORMAT_KEY, messageSource),
            s.getContentLanguages().stream().map(cl -> new Language(cl.getLocale().getLanguage(), cl.getDisplayLanguage())).collect(toList())
        );
    }

    @GetMapping("subscription/{id}")
    public ResponseEntity<SubscriptionDescriptorWithAdditionalInfo> getSubscriptionInfo(@PathVariable("id") String id, HttpSession session) {
        var res = subscriptionManager.getSubscriptionById(UUID.fromString(id));
        return res
            .map(s -> {
                var configurationsValues = PurchaseContextInfoBuilder.configurationsValues(s, configurationManager);
                var invoicingInfo = PurchaseContextInfoBuilder.invoicingInfo(configurationManager, configurationsValues);
                var analyticsConf = AnalyticsConfiguration.build(configurationsValues, session);
                var captchaConf = PurchaseContextInfoBuilder.captchaConfiguration(configurationManager, configurationsValues);
                var bankAccount = configurationsValues.get(BANK_ACCOUNT_NR).getValueOrDefault("");
                var bankAccountOwner = Arrays.asList(configurationsValues.get(BANK_ACCOUNT_OWNER).getValueOrDefault("").split("\n"));
                var orgContact = organizationRepository.getContactById(s.getOrganizationId());
                var messageSource = messageSourceManager.getMessageSourceFor(s);
                int available;
                if(!s.withinSalePeriod(clockProvider.getClock())) {
                    available = 0;
                } else if (s.getMaxAvailable() == -1) {
                    available = Integer.MAX_VALUE;
                } else {
                    available = subscriptionManager.countFree(s.getId());
                }
                return new SubscriptionDescriptorWithAdditionalInfo(s,
                    invoicingInfo,
                    analyticsConf,
                    captchaConf,
                    new EmbeddingConfiguration(configurationsValues.get(EMBED_POST_MESSAGE_ORIGIN).getValueOrNull()),
                    bankAccount,
                    bankAccountOwner,
                    orgContact.getEmail(),
                    orgContact.getName(),
                    DatesWithTimeZoneOffset.fromDates(s.getOnSaleFrom(), s.getOnSaleTo()),
                    Formatters.getFormattedDate(s, s.getOnSaleFrom(), DATE_FORMAT_KEY, messageSource),
                    Formatters.getFormattedDate(s, s.getOnSaleTo(), DATE_FORMAT_KEY, messageSource),
                    s.getZoneId().toString(),
                    Formatters.getFormattedDate(s, s.getValidityFrom(), DATE_FORMAT_KEY, messageSource),
                    Formatters.getFormattedDate(s, s.getValidityTo(), DATE_FORMAT_KEY, messageSource),
                    available);
            })
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("subscription/{id}")
    public ResponseEntity<ValidatedResponse<String>> reserveSubscription(@PathVariable("id") String id, Locale locale, Principal principal) {
        var bindingResult = new MapBindingResult(new HashMap<>(), "request");
        return subscriptionManager.getSubscriptionById(UUID.fromString(id))
            .map(subscriptionDescriptor -> {
                var reservationOptional = reservationManager.createSubscriptionReservation(subscriptionDescriptor, locale, bindingResult, principal);
                if (bindingResult.hasErrors()) {
                    return new ResponseEntity<ValidatedResponse<String>>(ValidatedResponse.toResponse(bindingResult, null), HttpStatus.UNPROCESSABLE_ENTITY);
                } else {
                    return reservationOptional.map(reservationId -> ResponseEntity.ok(new ValidatedResponse<>(ValidationResult.success(), reservationId)))
                        .orElseGet(() -> ResponseEntity.unprocessableEntity().build());
                }
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
