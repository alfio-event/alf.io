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
import alfio.model.system.ConfigurationKeys;
import alfio.util.HttpUtils;
import alfio.util.Json;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Map;

@Component
@AllArgsConstructor
public class RecaptchaService {

    private final HttpClient client;
    private final ConfigurationManager configurationManager;


    public boolean checkRecaptcha(String recaptchaResponse, HttpServletRequest req) {
        return configurationManager.getForSystem(ConfigurationKeys.RECAPTCHA_SECRET).getValue()
            .map(secret -> recaptchaRequest(client, secret, ObjectUtils.firstNonNull(recaptchaResponse, req.getParameter("g-recaptcha-response"))))
            .orElse(true);
    }

    private static boolean recaptchaRequest(HttpClient client, String secret, String response) {
        if(response == null) {
            return false;
        }

        try {
            var params = Map.of("secret", secret, "response", response);
            HttpResponse<String> httpResponse = HttpUtils.postForm(client, "https://www.google.com/recaptcha/api/siteverify", params);
            String body = httpResponse.body();
            return body != null && Json.fromJson(body, RecatpchaResponse.class).success;
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    @Data
    public static class RecatpchaResponse {
        private boolean success;
    }
}
