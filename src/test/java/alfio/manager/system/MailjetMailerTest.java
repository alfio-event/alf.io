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

import alfio.manager.testSupport.MaybeConfigurationBuilder;
import alfio.model.Configurable;
import alfio.util.HttpUtils;
import alfio.util.Json;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static alfio.manager.testSupport.MaybeConfigurationBuilder.existing;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.model.system.ConfigurationKeys.MAIL_REPLY_TO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MailjetMailerTest {

    private MailjetMailer mailjetMailer;
    private HttpClient httpClient;
    private Configurable configurable;
    private ArgumentCaptor<HttpRequest> requestCaptor;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        configurable = mock(Configurable.class);
        when(configurable.getConfigurationLevel()).thenReturn(ConfigurationLevel.system());
        var configurationManager = mock(ConfigurationManager.class);
        when(configurationManager.getFor(eq(EnumSet.of(MAILJET_APIKEY_PUBLIC, MAILJET_APIKEY_PRIVATE, MAILJET_FROM, MAIL_REPLY_TO)), any()))
            .thenReturn(Map.of(
                MAILJET_APIKEY_PUBLIC, existing(MAILJET_APIKEY_PUBLIC, "public"),
                MAILJET_APIKEY_PRIVATE, existing(MAILJET_APIKEY_PRIVATE, "private"),
                MAILJET_FROM, existing(MAILJET_FROM, "mail_from"),
                MAIL_REPLY_TO, existing(MAIL_REPLY_TO, "mail_to")
            ));
        mailjetMailer = new MailjetMailer(httpClient, configurationManager);
        requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    }

    @Test
    void send() throws Exception {
        var attachment = new Mailer.Attachment("filename", "test".getBytes(StandardCharsets.UTF_8), "text/plain", Map.of("model", "model"), Mailer.AttachmentIdentifier.CALENDAR_ICS);
        @SuppressWarnings("unchecked")
        var response = (HttpResponse<Object>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(httpClient.send(any(), any())).thenReturn(response);
        mailjetMailer.send(configurable, "from_name", "to", List.of("cc"), "subject", "text", Optional.of("html"), attachment);
        verify(httpClient).send(requestCaptor.capture(), eq(HttpResponse.BodyHandlers.discarding()));

        // verify request
        var request = requestCaptor.getValue();
        assertNotNull(request);
        var body = request.bodyPublisher().orElseThrow();
        var semaphore = new Semaphore(1);
        // acquire lock so that the async processing can complete before exiting the test
        assertTrue(semaphore.tryAcquire());
        body.subscribe(new Flow.Subscriber<>() {

            private final StringBuffer buffer = new StringBuffer();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                buffer.append(new String(item.array(), StandardCharsets.UTF_8));
            }

            @Override
            public void onError(Throwable throwable) {
                fail(throwable);
            }

            @Override
            public void onComplete() {
                assertTrue(buffer.length() > 0);
                var payload = Json.fromJson(buffer.toString(), new TypeReference<Map<String, JsonNode>>() {});
                assertNotNull(payload);
                assertFalse(payload.isEmpty());
                assertEquals("mail_from", getValue(payload.get("FromEmail")));
                assertEquals("from_name", getValue(payload.get("FromName")));
                assertEquals("subject", getValue(payload.get("Subject")));
                assertEquals("text", getValue(payload.get("Text-part")));
                assertEquals("html", getValue(payload.get("Html-part")));
                var recipients = payload.get("Recipients");
                var counter = new AtomicInteger(0);
                var emails = List.of("to", "cc");
                recipients.forEach(node -> {
                    if (emails.contains(node.get("Email").asText())) {
                        counter.incrementAndGet();
                    }
                });
                // we expect to find both addresses
                assertEquals(2, counter.get());
                assertEquals("mail_to", payload.get("Headers").get("Reply-To").asText());
                assertEquals(1, payload.get("Attachments").size());
                var attachment = payload.get("Attachments").get(0);
                assertEquals("filename", attachment.get("Filename").asText());
                assertEquals("text/plain", attachment.get("Content-type").asText());
                assertEquals(Base64.getEncoder().encodeToString("test".getBytes(StandardCharsets.UTF_8)), attachment.get("content").asText());

                var headers = request.headers();
                assertEquals(HttpUtils.APPLICATION_JSON, headers.firstValue(HttpUtils.CONTENT_TYPE).orElseThrow());
                assertEquals(HttpUtils.basicAuth("public", "private"), headers.firstValue(HttpUtils.AUTHORIZATION).orElseThrow());

                semaphore.release();
            }
        });
        assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS));
    }

    private String getValue(JsonNode node) {
        assertNotNull(node);
        return node.asText();
    }
}