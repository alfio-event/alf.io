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
import alfio.util.Json;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

@Component
public class EuVatChecker {

    private final ConfigurationManager configurationManager;
    private final OkHttpClient client = new OkHttpClient();

    public EuVatChecker(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    public boolean isVatCheckingEnabledFor(int organizationId) {
        return checkingEnabled(configurationManager, organizationId) && StringUtils.isNotEmpty(apiAddress(configurationManager)) && StringUtils.isNotEmpty(organizerCountry(configurationManager, organizationId));
    }

    public Optional<VatDetail> checkVat(String vatNr, String countryCode, int organizationId) {
        return performCheck(vatNr, countryCode, organizationId).apply(configurationManager, client);
    }

    static BiFunction<ConfigurationManager, OkHttpClient, Optional<VatDetail>> performCheck(String vatNr, String countryCode, int organizationId) {
        return (configurationManager, client) -> {
            if(StringUtils.isNotEmpty(vatNr) && StringUtils.length(countryCode) == 2 && checkingEnabled(configurationManager, organizationId)) {
                Request request = new Request.Builder()
                    .url(apiAddress(configurationManager) + "?country="+countryCode.toUpperCase()+"&number="+vatNr)
                    .get()
                    .build();
                try (Response resp = client.newCall(request).execute()) {
                    if(resp.isSuccessful()) {
                        return Optional.of(getVatDetail(resp, vatNr, countryCode, organizerCountry(configurationManager, organizationId)));
                    } else {
                        return Optional.empty();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return Optional.empty();
        };
    }

    private static VatDetail getVatDetail(Response resp, String vatNr, String countryCode, String organizerCountryCode) throws IOException {
        ResponseBody body = resp.body();
        String jsonString = body != null ? body.string() : "{}";
        Map<String, String> json = Json.fromJson(jsonString, new TypeReference<Map<String, String>>() {});
        boolean isValid = Boolean.parseBoolean(json.get("isValid"));
        return new VatDetail(vatNr, countryCode, isValid, json.get("name"), json.get("address"), isValid && !organizerCountryCode.equals(countryCode));
    }

    private static String apiAddress(ConfigurationManager configurationManager) {
        return configurationManager.getStringConfigValue(Configuration.getSystemConfiguration(ConfigurationKeys.EU_VAT_API_ADDRESS), null);
    }

    private static String organizerCountry(ConfigurationManager configurationManager, int organizationId) {
        return configurationManager.getStringConfigValue(Configuration.from(organizationId, ConfigurationKeys.COUNTRY_OF_BUSINESS), null);
    }

    private static boolean checkingEnabled(ConfigurationManager configurationManager, int organizationId) {
        return configurationManager.getBooleanConfigValue(Configuration.from(organizationId, ConfigurationKeys.ENABLE_EU_VAT_DIRECTIVE), false);
    }


}
