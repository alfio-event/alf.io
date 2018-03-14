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
import alfio.util.Json;
import lombok.AllArgsConstructor;
import lombok.Data;
import okhttp3.*;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component
@AllArgsConstructor
public class RecaptchaService {

    private final OkHttpClient client = new OkHttpClient();

    private final ConfigurationManager configurationManager;


    public boolean checkRecaptcha(HttpServletRequest req) {
        return configurationManager.getStringConfigValue(alfio.model.system.Configuration.getSystemConfiguration(ConfigurationKeys.RECAPTCHA_SECRET))
            .map((secret) -> recaptchaRequest(client, secret, req.getParameter("g-recaptcha-response")))
            .orElse(true);
    }

    private static boolean recaptchaRequest(OkHttpClient client, String secret, String response) {
        if(response == null) {
            return false;
        }

        try {
            RequestBody reqBody = new FormBody.Builder().add("secret", secret).add("response", response).build();
            Request request = new Request.Builder().url("https://www.google.com/recaptcha/api/siteverify").post(reqBody).build();

            try(Response resp = client.newCall(request).execute()) {
                ResponseBody body = resp.body();
                return body != null && Json.fromJson(body.string(), RecatpchaResponse.class).success;
            }
        } catch (IOException e) {
            return false;
        }
    }

    @Data
    public static class RecatpchaResponse {
        private boolean success;
    }
}
