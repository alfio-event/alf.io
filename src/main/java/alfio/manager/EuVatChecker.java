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

import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.AuditingRepository;
import alfio.util.ItalianTaxIdValidator;
import ch.digitalfondue.vatchecker.EUVatCheckResponse;
import ch.digitalfondue.vatchecker.EUVatChecker;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static alfio.model.Audit.EntityType.RESERVATION;
import static alfio.model.Audit.EventType.*;
import static alfio.model.system.ConfigurationKeys.*;

@Component
@Log4j2
@AllArgsConstructor
public class EuVatChecker {

    private final ConfigurationManager configurationManager;
    private final AuditingRepository auditingRepository;
    private final EUVatChecker client = new EUVatChecker();
    private final ExtensionManager extensionManager;

    private static final Cache<Pair<String, String>, EUVatCheckResponse> validationCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(15))
        .build();

    public boolean isReverseChargeEnabledFor(PurchaseContext configurable) {
        return reverseChargeEnabled(configurationManager, configurable);
    }

    public Optional<VatDetail> checkVat(String vatNr, String countryCode, PurchaseContext purchaseContext) {
        Optional<VatDetail> res = performCheck(vatNr, countryCode, purchaseContext).apply(configurationManager, client);
        return res.map(detail -> {
           if(!detail.isValid()) {
               String organizerCountry = organizerCountry(configurationManager, purchaseContext);
               boolean valid = ("IT".equals(organizerCountry) && "IT".equals(countryCode) && ItalianTaxIdValidator.validateVatId(vatNr))
                   || extensionManager.handleTaxIdValidation(purchaseContext, vatNr, organizerCountry);
               return new VatDetail(detail.getVatNr(), detail.getCountry(), valid, detail.getName(), detail.getAddress(), VatDetail.Type.FORMAL, false);
           } else {
               return detail;
           }
        });
    }

    static BiFunction<ConfigurationManager, EUVatChecker, Optional<VatDetail>> performCheck(String vatNr,
                                                                                            String countryCode,
                                                                                            Configurable configurable) {
        return (configurationManager, client) -> {
            boolean vatNrNotEmpty = StringUtils.isNotEmpty(vatNr);
            boolean validCountryCode = StringUtils.length(StringUtils.trimToNull(countryCode)) == 2;

            if(!vatNrNotEmpty || !validCountryCode) {
                return Optional.empty();
            }


            boolean euCountryCode = configurationManager.getForSystem(ConfigurationKeys.EU_COUNTRIES_LIST).getRequiredValue().contains(countryCode);

            boolean validationEnabled = validationEnabled(configurationManager, configurable);
            if(euCountryCode && validationEnabled) {
                EUVatCheckResponse validationResult = validateEUVat(vatNr, countryCode, client);
                return Optional.ofNullable(validationResult)
                    .map(r -> getVatDetail(reverseChargeEnabled(configurationManager, configurable), r, vatNr, countryCode, organizerCountry(configurationManager, configurable)));
            }

            String organizerCountry = organizerCountry(configurationManager, configurable);
            if(StringUtils.isEmpty(organizerCountry(configurationManager, configurable))) {
                return Optional.empty();
            }

            BooleanSupplier applyVatToForeignBusiness = () -> configurationManager.getFor(APPLY_VAT_FOREIGN_BUSINESS, configurable.getConfigurationLevel()).getValueAsBooleanOrDefault();
            boolean vatExempt = !organizerCountry.equals(countryCode) && (euCountryCode || !applyVatToForeignBusiness.getAsBoolean());
            return Optional.of(new VatDetail(vatNr, countryCode, true, "", "", euCountryCode ? VatDetail.Type.SKIPPED : VatDetail.Type.EXTRA_EU, vatExempt));

        };
    }

    public void logSuccessfulValidation(VatDetail detail, String reservationId, PurchaseContext purchaseContext) {
        List<Map<String, Object>> modifications = List.of(
            Map.of("vatNumber", detail.getVatNr(), "country", detail.getCountry(), "validationType", detail.getType())
        );
        Audit.EventType eventType = null;
        switch(detail.getType()) {
            case VIES:
            case EXTRA_EU:
                eventType = VAT_VALIDATION_SUCCESSFUL;
            break;
            case SKIPPED:
                eventType = VAT_VALIDATION_SKIPPED;
            break;
            case FORMAL:
                eventType = VAT_FORMAL_VALIDATION_SUCCESSFUL;
            break;
        }
        auditingRepository.insert(reservationId, null, purchaseContext, eventType, new Date(), RESERVATION, reservationId, modifications);
    }


    static EUVatCheckResponse validateEUVat(String vat, String countryCode, EUVatChecker client) {

        if(StringUtils.isEmpty(vat) || StringUtils.length(countryCode) != 2) {
            return null;
        }

        return validationCache.get(Pair.of(vat, countryCode), k -> client.check(countryCode.toUpperCase(), vat));
    }

    private static VatDetail getVatDetail(boolean reverseChargeEnabled, EUVatCheckResponse response, String vatNr, String countryCode, String organizerCountryCode) {
        boolean isValid = response.isValid();
        return new VatDetail(vatNr, countryCode, isValid, response.getName(), response.getAddress(), VatDetail.Type.VIES, isValid && reverseChargeEnabled && !organizerCountryCode.equals(countryCode));
    }

    static String organizerCountry(ConfigurationManager configurationManager, Configurable configurable) {
        return configurationManager.getFor(COUNTRY_OF_BUSINESS, configurable.getConfigurationLevel()).getValueOrNull();
    }

    private static boolean reverseChargeEnabled(ConfigurationManager configurationManager, Configurable configurable) {
        return reverseChargeEnabled(loadConfigurationForReverseChargeCheck(configurationManager, configurable));
    }

    public static Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> loadConfigurationForReverseChargeCheck(ConfigurationManager configurationManager, Configurable configurable) {
        return configurationManager.getFor(Set.of(ENABLE_EU_VAT_DIRECTIVE,
            COUNTRY_OF_BUSINESS,
            ENABLE_REVERSE_CHARGE_IN_PERSON,
            ENABLE_REVERSE_CHARGE_ONLINE), configurable.getConfigurationLevel());
    }

    /**
     * In order for Reverse Charge to be enabled, the global flag must be active (ENABLE_EU_VAT_DIRECTIVE) plus one of ENABLE_REVERSE_CHARGE_IN_PERSON, ENABLE_REVERSE_CHARGE_ONLINE
     * which are active by default.
     * @param res require the keys ENABLE_EU_VAT_DIRECTIVE, COUNTRY_OF_BUSINESS, ENABLE_REVERSE_CHARGE_IN_PERSON, ENABLE_REVERSE_CHARGE_ONLINE
     * @return true if Reverse Charge is enabled
     */
    public static boolean reverseChargeEnabled(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> res) {
        Validate.isTrue(res.containsKey(ENABLE_EU_VAT_DIRECTIVE)
            && res.containsKey(COUNTRY_OF_BUSINESS)
            && res.containsKey(ENABLE_REVERSE_CHARGE_IN_PERSON)
            && res.containsKey(ENABLE_REVERSE_CHARGE_ONLINE));
        return (
            res.get(ENABLE_EU_VAT_DIRECTIVE).getValueAsBooleanOrDefault()
                && (res.get(ENABLE_REVERSE_CHARGE_IN_PERSON).getValueAsBooleanOrDefault() || res.get(ENABLE_REVERSE_CHARGE_ONLINE).getValueAsBooleanOrDefault())
        ) && res.get(COUNTRY_OF_BUSINESS).isPresent();
    }

    static boolean validationEnabled(ConfigurationManager configurationManager, Configurable configurable) {
        return configurationManager.getFor(ConfigurationKeys.ENABLE_VIES_VALIDATION, configurable.getConfigurationLevel()).getValueAsBooleanOrDefault();
    }

}
