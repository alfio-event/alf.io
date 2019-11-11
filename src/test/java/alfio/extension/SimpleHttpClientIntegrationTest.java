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

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Map;

public class SimpleHttpClientIntegrationTest {

    private static ClientAndServer mockServer;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final SimpleHttpClient simpleHttpClient = new SimpleHttpClient(httpClient);

    @BeforeClass
    public static void startServer() {
        mockServer = ClientAndServer.startClientAndServer(4243);
    }

    @AfterClass
    public static void stopServer() {
        mockServer.stop();
    }

    @Test
    public void testSimpleGet() throws IOException {

        mockServer
            .when(HttpRequest.request().withMethod("GET").withPath("/simple-get-1"))
            .respond(HttpResponse.response("ok").withStatusCode(200).withHeader("Content-Type", "text/plain"));

        var res = simpleHttpClient.get("http://localhost:4243/simple-get-1");

        Assert.assertTrue(res.isSuccessful());
        Assert.assertEquals("ok", res.getBody());
        Assert.assertEquals(200, res.getCode());

        var notFound = simpleHttpClient.get("http://localhost:4243/simple-get-2-failure");
        Assert.assertFalse(notFound.isSuccessful());
        Assert.assertEquals(404, notFound.getCode());
    }

    @Test
    public void testSimpleGetWithHeaders() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("GET").withPath("/simple-get-header").withHeader("Custom-Header", "Custom-Value"))
            .respond(HttpResponse.response("ok").withStatusCode(200).withHeader("Content-Type", "text/plain"));

        var missingHeader = simpleHttpClient.get("http://localhost:4243/simple-get-header", Map.of());
        Assert.assertFalse(missingHeader.isSuccessful());
        Assert.assertEquals(404, missingHeader.getCode());


        var res = simpleHttpClient.get("http://localhost:4243/simple-get-header", Map.of("Custom-Header", "Custom-Value"));
        Assert.assertTrue(res.isSuccessful());
        Assert.assertEquals(200, res.getCode());
    }

    @Test
    public void testJsonBody() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("GET").withPath("/simple-get-json"))
            .respond(HttpResponse.response("{\"key\": \"value\"}").withStatusCode(200).withHeader("Content-Type", "application/json"));

        var res = simpleHttpClient.get("http://localhost:4243/simple-get-json");

        var body = res.getJsonBody(Map.class);
        Assert.assertTrue(body instanceof Map);
        Assert.assertTrue(body.containsKey("key"));
        Assert.assertEquals("value", body.get("key"));

        var body2 = (Map<String, String>) res.getJsonBody();
        Assert.assertTrue(body2 instanceof Map);
        Assert.assertTrue(body2.containsKey("key"));
        Assert.assertEquals("value", body2.get("key"));
    }

    @Test
    public void testHead() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("HEAD").withPath("/simple-head"))
            .respond(HttpResponse.response().withStatusCode(200));

        var res = simpleHttpClient.head("http://localhost:4243/simple-head");
        Assert.assertTrue(res.isSuccessful());
        Assert.assertEquals(200, res.getCode());
    }

    @Test
    public void testHeadWithHeaders() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("HEAD").withPath("/simple-head-header").withHeader("Custom-Header", "Custom-Value"))
            .respond(HttpResponse.response().withStatusCode(200));

        var noHeader = simpleHttpClient.head("http://localhost:4243/simple-head-header");
        Assert.assertFalse(noHeader.isSuccessful());
        Assert.assertEquals(404, noHeader.getCode());


        var res = simpleHttpClient.head("http://localhost:4243/simple-head-header", Map.of("Custom-Header", "Custom-Value"));
        Assert.assertTrue(res.isSuccessful());
        Assert.assertEquals(200, res.getCode());
    }
}
