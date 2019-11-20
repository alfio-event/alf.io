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
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.*;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
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
        Assert.assertNotNull(body);
        Assert.assertTrue(body.containsKey("key"));
        Assert.assertEquals("value", body.get("key"));

        @SuppressWarnings("unchecked")
        var body2 = (Map<String, String>) res.getJsonBody();
        Assert.assertNotNull(body2);
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

    @Test
    public void testPost() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("POST").withPath("/simple-post"))
            .respond(HttpResponse.response().withStatusCode(200).withBody("Hello World!").withHeader("Content-Type", "text/plain"));

        var res = simpleHttpClient.post("http://localhost:4243/simple-post");
        Assert.assertTrue(res.isSuccessful());
        Assert.assertEquals(200, res.getCode());
        Assert.assertEquals("Hello World!", res.getBody());
        Assert.assertTrue(res.getHeaders().containsKey("Content-Type"));
        Assert.assertEquals("text/plain", res.getHeaders().get("Content-Type").get(0));
        Assert.assertEquals("text/plain", res.getHeader("Content-Type"));
    }

    @Test
    public void testPostWithHeaders() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("POST").withPath("/simple-post-header").withHeader("Custom-Header", "Custom-Value"))
            .respond(HttpResponse.response().withStatusCode(200).withBody("Hello World!").withHeader("Content-Type", "text/plain"));

        Assert.assertFalse(simpleHttpClient.post("http://localhost:4243/simple-post-header").isSuccessful());

        Assert.assertTrue(simpleHttpClient.post("http://localhost:4243/simple-post-header", Map.of("Custom-Header", "Custom-Value")).isSuccessful());
    }

    @Test
    public void testPostJson() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("POST").withPath("/simple-post-json").withBody(JsonBody.json("{\"key\": \"value\"}")))
            .respond(HttpResponse.response().withStatusCode(200).withBody("Hello World!").withHeader("Content-Type", "text/plain"));

        var resNok = simpleHttpClient.post("http://localhost:4243/simple-post-json", Map.of(), Map.of("not", "correct"));
        Assert.assertFalse(resNok.isSuccessful());
        Assert.assertEquals(404, resNok.getCode());

        var res = simpleHttpClient.post("http://localhost:4243/simple-post-json", Map.of(), Map.of("key", "value"));
        Assert.assertTrue(res.isSuccessful());
        Assert.assertEquals(200, res.getCode());


        var res2 = simpleHttpClient.postJSON("http://localhost:4243/simple-post-json", Map.of(), Map.of("key", "value"));
        Assert.assertTrue(res2.isSuccessful());
        Assert.assertEquals(200, res2.getCode());
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
        Assert.assertTrue(res.isSuccessful());
        Assert.assertEquals(200, res.getCode());
    }

    @Test
    public void testPostFile() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("POST").withPath("/simple-post-file").withBody(StringBody.subString("content", StandardCharsets.UTF_8)))
            .respond(HttpResponse.response().withStatusCode(200).withBody("Hello World!").withHeader("Content-Type", "text/plain"));

        File tmp = File.createTempFile("test", "test");
        FileUtils.write(tmp, "content", StandardCharsets.UTF_8);

        var res = simpleHttpClient.postFileAndSaveResponse("http://localhost:4243/simple-post-file", Map.of(), tmp.getAbsolutePath(), tmp.getName(), "text/plain");
        Assert.assertTrue(res.isSuccessful());
        Assert.assertEquals(200, res.getCode());
        Assert.assertNotNull(res.getTempFilePath());
        var saved = new File(res.getTempFilePath());
        Assert.assertEquals("Hello World!", FileUtils.readFileToString(saved, StandardCharsets.UTF_8));

        tmp.delete();
        saved.delete();

    }

    @Test
    public void testPostBody() throws IOException {
        mockServer
            .when(HttpRequest.request().withMethod("POST").withPath("/simple-post-body").withHeader("Content-Type", "text/plain").withBody(StringBody.exact("content", StandardCharsets.UTF_8)))
            .respond(HttpResponse.response().withStatusCode(200).withBody("Hello World!").withHeader("Content-Type", "text/plain"));

        var res = simpleHttpClient.postBodyAndSaveResponse("http://localhost:4243/simple-post-body", Map.of(), "content", "text/plain");
        Assert.assertTrue(res.isSuccessful());
        Assert.assertEquals(200, res.getCode());
        Assert.assertNotNull(res.getTempFilePath());
        var saved = new File(res.getTempFilePath());
        Assert.assertEquals("Hello World!", FileUtils.readFileToString(saved, StandardCharsets.UTF_8));
        saved.delete();
    }
}
