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
package alfio.extension;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.*;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleHttpClientIntegrationTest {

    private static ClientAndServer mockServer;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final SimpleHttpClient simpleHttpClient = new SimpleHttpClient(httpClient);

    @BeforeAll
    public static void startServer() {
        mockServer = ClientAndServer.startClientAndServer(4243);
    }

    @AfterAll
    public static void stopServer() {
        mockServer.stop();
    }

    @Test
    public void testSimpleGet() throws IOException {

        mockServer
            .when(HttpRequest.request().withMethod("GET").withPath("/simple-get-1"))
            .respond(HttpResponse.response("ok").withStatusCode(200).withHeader("Content-Type", "text/plain"));

        var res = simpleHttpClient.get("http://localhost:4243/simple-get-1");

        assertTrue(res.isSuccessful());
        assertEquals("ok", res.getBody());
        assertEquals(200, res.getCode());

        var notFound = simpleHttpClient.get("http://localhost:4243/simple-get-2-failure");
        assertFalse(notFound.isSuccessful());
        assertEquals(404, notFound.getCode());
    }

    @Test
    public void testSimpleGetWithHeaders() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("GET").withPath("/simple-get-header").withHeader("Custom-Header", "Custom-Value"))
            .respond(HttpResponse.response("ok").withStatusCode(200).withHeader("Content-Type", "text/plain"));

        var missingHeader = simpleHttpClient.get("http://localhost:4243/simple-get-header", Map.of());
        assertFalse(missingHeader.isSuccessful());
        Assertions.assertEquals(404, missingHeader.getCode());


        var res = simpleHttpClient.get("http://localhost:4243/simple-get-header", Map.of("Custom-Header", "Custom-Value"));
        assertTrue(res.isSuccessful());
        assertEquals(200, res.getCode());
    }

    @Test
    public void testJsonBody() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("GET").withPath("/simple-get-json"))
            .respond(HttpResponse.response("{\"key\": \"value\"}").withStatusCode(200).withHeader("Content-Type", "application/json"));

        var res = simpleHttpClient.get("http://localhost:4243/simple-get-json");

        var body = res.getJsonBody(Map.class);
        assertNotNull(body);
        assertTrue(body.containsKey("key"));
        Assertions.assertEquals("value", body.get("key"));

        @SuppressWarnings("unchecked")
        var body2 = (Map<String, String>) res.getJsonBody();
        assertNotNull(body2);
        assertTrue(body2.containsKey("key"));
        Assertions.assertEquals("value", body2.get("key"));
    }

    @Test
    public void testHead() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("HEAD").withPath("/simple-head"))
            .respond(HttpResponse.response().withStatusCode(200));

        var res = simpleHttpClient.head("http://localhost:4243/simple-head");
        assertTrue(res.isSuccessful());
        Assertions.assertEquals(200, res.getCode());
    }

    @Test
    public void testHeadWithHeaders() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("HEAD").withPath("/simple-head-header").withHeader("Custom-Header", "Custom-Value"))
            .respond(HttpResponse.response().withStatusCode(200));

        var noHeader = simpleHttpClient.head("http://localhost:4243/simple-head-header");
        assertFalse(noHeader.isSuccessful());
        Assertions.assertEquals(404, noHeader.getCode());


        var res = simpleHttpClient.head("http://localhost:4243/simple-head-header", Map.of("Custom-Header", "Custom-Value"));
        assertTrue(res.isSuccessful());
        Assertions.assertEquals(200, res.getCode());
    }

    @Test
    public void testPost() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("POST").withPath("/simple-post"))
            .respond(HttpResponse.response().withStatusCode(200).withBody("Hello World!").withHeader("Content-Type", "text/plain"));

        var res = simpleHttpClient.post("http://localhost:4243/simple-post");
        assertTrue(res.isSuccessful());
        Assertions.assertEquals(200, res.getCode());
        Assertions.assertEquals("Hello World!", res.getBody());
        assertTrue(res.getHeaders().containsKey("Content-Type"));
        Assertions.assertEquals("text/plain", res.getHeaders().get("Content-Type").get(0));
        Assertions.assertEquals("text/plain", res.getHeader("Content-Type"));
    }

    @Test
    public void testPostWithHeaders() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("POST").withPath("/simple-post-header").withHeader("Custom-Header", "Custom-Value"))
            .respond(HttpResponse.response().withStatusCode(200).withBody("Hello World!").withHeader("Content-Type", "text/plain"));

        assertFalse(simpleHttpClient.post("http://localhost:4243/simple-post-header").isSuccessful());

        assertTrue(simpleHttpClient.post("http://localhost:4243/simple-post-header", Map.of("Custom-Header", "Custom-Value")).isSuccessful());
    }

    @Test
    public void testPostJson() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("POST").withPath("/simple-post-json").withBody(JsonBody.json("{\"key\": \"value\"}")))
            .respond(HttpResponse.response().withStatusCode(200).withBody("Hello World!").withHeader("Content-Type", "text/plain"));

        var resNok = simpleHttpClient.post("http://localhost:4243/simple-post-json", Map.of(), Map.of("not", "correct"));
        assertFalse(resNok.isSuccessful());
        Assertions.assertEquals(404, resNok.getCode());

        var res = simpleHttpClient.post("http://localhost:4243/simple-post-json", Map.of(), Map.of("key", "value"));
        assertTrue(res.isSuccessful());
        Assertions.assertEquals(200, res.getCode());


        var res2 = simpleHttpClient.postJSON("http://localhost:4243/simple-post-json", Map.of(), Map.of("key", "value"));
        assertTrue(res2.isSuccessful());
        Assertions.assertEquals(200, res2.getCode());
    }

    @Test
    public void testPostForm() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("POST").withPath("/simple-post-form").withBody(
                ParameterBody.params(
                    Parameter.param("k1", "v1"),
                    Parameter.param("k2", "v2")
                )))
            .respond(HttpResponse.response().withStatusCode(200).withBody("Hello World!").withHeader("Content-Type", "text/plain"));

        var res = simpleHttpClient.postForm("http://localhost:4243/simple-post-form", Map.of(), Map.of("k1", "v1", "k2", "v2"));
        assertTrue(res.isSuccessful());
        Assertions.assertEquals(200, res.getCode());
    }

    @Test
    public void testPostFile() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("POST").withPath("/simple-post-file").withBody(StringBody.subString("content", StandardCharsets.UTF_8)))
            .respond(HttpResponse.response().withStatusCode(200).withBody("Hello World!").withHeader("Content-Type", "text/plain"));

        File tmp = File.createTempFile("test", "test");
        FileUtils.write(tmp, "content", StandardCharsets.UTF_8.toString());

        var res = simpleHttpClient.postFileAndSaveResponse("http://localhost:4243/simple-post-file", Map.of(), tmp.getAbsolutePath(), tmp.getName(), "text/plain");
        assertTrue(res.isSuccessful());
        Assertions.assertEquals(200, res.getCode());
        assertNotNull(res.getTempFilePath());
        var saved = new File(res.getTempFilePath());
        Assertions.assertEquals("Hello World!", FileUtils.readFileToString(saved, StandardCharsets.UTF_8.toString()));

        tmp.delete();
        saved.delete();

    }

    @Test
    public void testPostBody() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("POST").withPath("/simple-post-body").withHeader("Content-Type", "text/plain").withBody(StringBody.exact("content", StandardCharsets.UTF_8)))
            .respond(HttpResponse.response().withStatusCode(200).withBody("Hello World!").withHeader("Content-Type", "text/plain"));

        var res = simpleHttpClient.postBodyAndSaveResponse("http://localhost:4243/simple-post-body", Map.of(), "content", "text/plain");
        assertTrue(res.isSuccessful());
        Assertions.assertEquals(200, res.getCode());
        assertNotNull(res.getTempFilePath());
        var saved = new File(res.getTempFilePath());
        Assertions.assertEquals("Hello World!", FileUtils.readFileToString(saved, StandardCharsets.UTF_8.toString()));
        saved.delete();
    }
}
