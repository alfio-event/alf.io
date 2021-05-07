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
package alfio.manager.system;

import alfio.model.EventAndOrganizationId;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.ConfigurationPathLevel;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

public class SendGridMailerTest {

    private SendGridMailer sendGridMailer;

    private ConfigurationManager configurationManager;

    private HttpClient client;

    @BeforeEach
    public void setUp() {
        configurationManager = mock(ConfigurationManager.class);
        client = mock(HttpClient.class);
        sendGridMailer = new SendGridMailer(client, configurationManager);
    }

    @Test
    public void shouldSendEmail() throws IOException, InterruptedException {
        //Test data
        final var apiConfig = new ConfigurationManager.MaybeConfiguration(ConfigurationKeys.SENDGRID_API_KEY, new ConfigurationKeyValuePathLevel("key", "value", ConfigurationPathLevel.SYSTEM));
        final var fromConfig = new ConfigurationManager.MaybeConfiguration(ConfigurationKeys.SENDGRID_FROM, new ConfigurationKeyValuePathLevel("key", "value", ConfigurationPathLevel.SYSTEM));
        //Mock
        when(configurationManager.getFor(anySet(), any(ConfigurationLevel.class))).thenReturn(Map.of(ConfigurationKeys.SENDGRID_API_KEY, apiConfig, ConfigurationKeys.SENDGRID_FROM, fromConfig));
        final HttpResponse<Object> httpResponse = createMockHttpResponse();
        when(client.send(any(HttpRequest.class), any())).thenReturn(httpResponse);
        //Service call
        sendGridMailer.send(new EventAndOrganizationId(1, 2), "Test", "TestEmail", List.of(), "TestSubject", "Test", Optional.empty(), ArrayUtils.toArray());
        //Verify
        verify(configurationManager).getFor(anySet(), any(ConfigurationLevel.class));
        verify(client).send(any(HttpRequest.class), any());
        verifyNoMoreInteractions(configurationManager, client);
    }

    private HttpResponse<Object> createMockHttpResponse() {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return 200;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public Optional<HttpResponse<Object>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return null;
            }

            @Override
            public Object body() {
                return null;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return null;
            }

            @Override
            public HttpClient.Version version() {
                return null;
            }
        };
    }
}