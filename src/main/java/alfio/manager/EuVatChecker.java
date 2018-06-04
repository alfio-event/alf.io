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
import alfio.model.VatDetail;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import ch.digitalfondue.vatchecker.EUVatCheckResponse;
import ch.digitalfondue.vatchecker.EUVatChecker;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static alfio.model.system.Configuration.getSystemConfiguration;
import static alfio.model.system.ConfigurationKeys.APPLY_VAT_FOREIGN_BUSINESS;

@Component
@Log4j2
public class EuVatChecker {

    private final ConfigurationManager configurationManager;
    private final EUVatChecker client = new EUVatChecker();

    public EuVatChecker(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    public boolean isVatCheckingEnabledFor(int organizationId) {
        return checkingEnabled(configurationManager, organizationId) && StringUtils.isNotEmpty(organizerCountry(configurationManager, organizationId));
    }

    public Optional<VatDetail> checkVat(String vatNr, String countryCode, int organizationId) {
        return performCheck(vatNr, countryCode, organizationId).apply(configurationManager, client);
    }

    static BiFunction<ConfigurationManager, EUVatChecker, Optional<VatDetail>> performCheck(String vatNr, String countryCode, int organizationId) {
        return (configurationManager, client) -> {
            boolean vatNrNotEmpty = StringUtils.isNotEmpty(vatNr);
            boolean validCountryCode = StringUtils.length(StringUtils.trimToNull(countryCode)) == 2;

            if(!vatNrNotEmpty || !validCountryCode) {
                return Optional.empty();
            }

            boolean euCountryCode = configurationManager.getRequiredValue(getSystemConfiguration(ConfigurationKeys.EU_COUNTRIES_LIST)).contains(countryCode);

            if(euCountryCode && checkingEnabled(configurationManager, organizationId)) {
                try {
                    EUVatCheckResponse response = client.check(countryCode.toUpperCase(), vatNr);
                    return Optional.of(getVatDetail(response, vatNr, countryCode, organizerCountry(configurationManager, organizationId)));
                } catch (IllegalStateException e) {
                    log.warn("Error while calling VAT NR check.", e);
                    return Optional.empty();
                }
            } else {
                String organizerCountry = organizerCountry(configurationManager, organizationId);
                Supplier<Boolean> applyVatToForeignBusiness = () -> configurationManager.getBooleanConfigValue(Configuration.from(organizationId, APPLY_VAT_FOREIGN_BUSINESS), true);
                return Optional.of(new VatDetail(vatNr, countryCode, true, "", "", !organizerCountry.equals(countryCode) && !applyVatToForeignBusiness.get()));
            }
        };
    }

    private static VatDetail getVatDetail(EUVatCheckResponse resp, String vatNr, String countryCode, String organizerCountryCode) {
        boolean isValid = resp.isValid();
        return new VatDetail(vatNr, countryCode, isValid, resp.getName(), resp.getAddress(), isValid && !organizerCountryCode.equals(countryCode));
    }

    private static String organizerCountry(ConfigurationManager configurationManager, int organizationId) {
        return configurationManager.getStringConfigValue(Configuration.from(organizationId, ConfigurationKeys.COUNTRY_OF_BUSINESS), null);
    }

    private static boolean checkingEnabled(ConfigurationManager configurationManager, int organizationId) {
        return configurationManager.getBooleanConfigValue(Configuration.from(organizationId, ConfigurationKeys.ENABLE_EU_VAT_DIRECTIVE), false);
    }
}
