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
import alfio.model.Audit;
import alfio.model.EventAndOrganizationId;
import alfio.model.VatDetail;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.AuditingRepository;
import ch.digitalfondue.vatchecker.EUVatCheckResponse;
import ch.digitalfondue.vatchecker.EUVatChecker;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static alfio.model.Audit.EntityType.RESERVATION;
import static alfio.model.Audit.EventType.*;
import static alfio.model.system.Configuration.getSystemConfiguration;
import static alfio.model.system.ConfigurationKeys.APPLY_VAT_FOREIGN_BUSINESS;

@Component
@Log4j2
@AllArgsConstructor
public class EuVatChecker {

    private final ConfigurationManager configurationManager;
    private final AuditingRepository auditingRepository;
    private final EUVatChecker client = new EUVatChecker();
    private final ExtensionManager extensionManager;

    private static final Cache<Pair<String, String>, EUVatCheckResponse> validationCache = Caffeine.newBuilder()
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .build();

    public boolean isReverseChargeEnabledFor(int organizationId) {
        return reverseChargeEnabled(configurationManager, organizationId);
    }

    public Optional<VatDetail> checkVat(String vatNr, String countryCode, EventAndOrganizationId event) {
        Optional<VatDetail> res = performCheck(vatNr, countryCode, event.getOrganizationId()).apply(configurationManager, client);
        res.map(detail -> {
           if(!detail.isValid()) {
               String organizerCountry = organizerCountry(configurationManager, event.getOrganizationId());
               boolean valid = extensionManager.handleTaxIdValidation(event.getId(), vatNr, organizerCountry);
               return new VatDetail(detail.getVatNr(), detail.getCountry(), valid, detail.getName(), detail.getAddress(), VatDetail.Type.FORMAL, false);
           } else {
               return detail;
           }
        });

        return res;
    }

    static BiFunction<ConfigurationManager, EUVatChecker, Optional<VatDetail>> performCheck(String vatNr, String countryCode, int organizationId) {
        return (configurationManager, client) -> {
            boolean vatNrNotEmpty = StringUtils.isNotEmpty(vatNr);
            boolean validCountryCode = StringUtils.length(StringUtils.trimToNull(countryCode)) == 2;

            if(!vatNrNotEmpty || !validCountryCode) {
                return Optional.empty();
            }


            boolean euCountryCode = configurationManager.getRequiredValue(getSystemConfiguration(ConfigurationKeys.EU_COUNTRIES_LIST)).contains(countryCode);

            boolean validationEnabled = validationEnabled(configurationManager, organizationId);
            if(euCountryCode && validationEnabled) {
                EUVatCheckResponse validationResult = validateEUVat(vatNr, countryCode, client);
                return Optional.ofNullable(validationResult)
                    .map(r -> getVatDetail(reverseChargeEnabled(configurationManager, organizationId), r, vatNr, countryCode, organizerCountry(configurationManager, organizationId)));
            }

            String organizerCountry = organizerCountry(configurationManager, organizationId);
            if(StringUtils.isEmpty(organizerCountry(configurationManager, organizationId))) {
                return Optional.empty();
            }

            Supplier<Boolean> applyVatToForeignBusiness = () -> configurationManager.getBooleanConfigValue(Configuration.from(organizationId, APPLY_VAT_FOREIGN_BUSINESS), true);
            boolean vatExempt = !organizerCountry.equals(countryCode) && (euCountryCode || !applyVatToForeignBusiness.get());
            return Optional.of(new VatDetail(vatNr, countryCode, true, "", "", euCountryCode ? VatDetail.Type.SKIPPED : VatDetail.Type.EXTRA_EU, vatExempt));

        };
    }

    public void logSuccessfulValidation(VatDetail detail, String reservationId, int eventId) {
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
        auditingRepository.insert(reservationId, null, eventId, eventType, new Date(), RESERVATION, reservationId, modifications);
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

    static String organizerCountry(ConfigurationManager configurationManager, int organizationId) {
        return configurationManager.getStringConfigValue(Configuration.from(organizationId, ConfigurationKeys.COUNTRY_OF_BUSINESS), null);
    }

    private static boolean reverseChargeEnabled(ConfigurationManager configurationManager, int organizationId) {
        return configurationManager.getBooleanConfigValue(Configuration.from(organizationId, ConfigurationKeys.ENABLE_EU_VAT_DIRECTIVE), false);
    }

    static boolean validationEnabled(ConfigurationManager configurationManager, int organizationId) {
        return configurationManager.getBooleanConfigValue(Configuration.from(organizationId, ConfigurationKeys.ENABLE_VIES_VALIDATION), true);
    }


}
