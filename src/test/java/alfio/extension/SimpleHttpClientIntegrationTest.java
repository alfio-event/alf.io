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

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@WireMockTest
class SimpleHttpClientIntegrationTest {

    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final SimpleHttpClient simpleHttpClient = new SimpleHttpClient(httpClient);

    @Test
    void testSimpleGet(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        stubFor(get("/simple-get-1")
            .willReturn(ok("ok")
                .withHeader("Content-Type", "text/plain")));

        var res = simpleHttpClient.get(wmRuntimeInfo.getHttpBaseUrl() + "/simple-get-1");

        assertTrue(res.isSuccessful());
        assertEquals("ok", res.getBody());
        assertEquals(200, res.getCode());

        var notFound = simpleHttpClient.get(wmRuntimeInfo.getHttpBaseUrl() + "/simple-get-2-failure");
        assertFalse(notFound.isSuccessful());
        assertEquals(404, notFound.getCode());
    }

    @Test
    void testSimpleGetWithHeaders(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        stubFor(get("/simple-get-header").withHeader("Custom-Header", equalTo("Custom-Value"))
            .willReturn(ok("ok").withHeader("Content-Type", "text/plain")));

        var missingHeader = simpleHttpClient.get(wmRuntimeInfo.getHttpBaseUrl() + "/simple-get-header", Map.of());
        assertFalse(missingHeader.isSuccessful());
        Assertions.assertEquals(404, missingHeader.getCode());

        var res = simpleHttpClient.get(wmRuntimeInfo.getHttpBaseUrl() + "/simple-get-header", Map.of("Custom-Header", "Custom-Value"));
        assertTrue(res.isSuccessful());
        assertEquals(200, res.getCode());
    }

    @Test
    void testJsonBody(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        stubFor(get("/simple-get-json")
            .willReturn(ok("{\"key\": \"value\"}").withHeader("Content-Type", "application/json")));

        var res = simpleHttpClient.get(wmRuntimeInfo.getHttpBaseUrl() + "/simple-get-json");

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
    void testHead(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        stubFor(head(urlEqualTo("/simple-head")).willReturn(ok()));

        var res = simpleHttpClient.head(wmRuntimeInfo.getHttpBaseUrl() + "/simple-head");
        assertTrue(res.isSuccessful());
        Assertions.assertEquals(200, res.getCode());
    }

    @Test
    void testHeadWithHeaders(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        stubFor(head(urlEqualTo("/simple-head-header")).withHeader("Custom-Header", equalTo("Custom-Value"))
            .willReturn(ok()));

        var noHeader = simpleHttpClient.head(wmRuntimeInfo.getHttpBaseUrl() + "/simple-head-header");
        assertFalse(noHeader.isSuccessful());
        Assertions.assertEquals(404, noHeader.getCode());

        var res = simpleHttpClient.head(wmRuntimeInfo.getHttpBaseUrl() + "/simple-head-header", Map.of("Custom-Header", "Custom-Value"));
        assertTrue(res.isSuccessful());
        Assertions.assertEquals(200, res.getCode());
    }

    @Test
    void testPost(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        stubFor(post("/simple-post").willReturn(ok("Hello World!")
                .withHeader("Content-Type", "text/plain")));

        var res = simpleHttpClient.post(wmRuntimeInfo.getHttpBaseUrl() + "/simple-post");
        assertTrue(res.isSuccessful());
        Assertions.assertEquals(200, res.getCode());
        Assertions.assertEquals("Hello World!", res.getBody());
        assertTrue(res.getHeaders().containsKey("Content-Type"));
        Assertions.assertEquals("text/plain", res.getHeaders().get("Content-Type").get(0));
        Assertions.assertEquals("text/plain", res.getHeader("Content-Type"));
    }

    @Test
    void testPostWithHeaders(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        stubFor(post("/simple-post-header").withHeader("Custom-Header", equalTo("Custom-Value"))
            .willReturn(ok("Hello World!").withHeader("Content-Type", "text/plain")));

        assertFalse(simpleHttpClient.post(wmRuntimeInfo.getHttpBaseUrl() + "/simple-post-header").isSuccessful());

        assertTrue(simpleHttpClient.post(wmRuntimeInfo.getHttpBaseUrl() + "/simple-post-header", Map.of("Custom-Header", "Custom-Value")).isSuccessful());
    }

    @Test
    void testPostJson(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        stubFor(post("/simple-post-json").withRequestBody(equalToJson("{\"key\": \"value\"}"))
            .willReturn(ok("Hello World!").withHeader("Content-Type", "text/plain")));

        var resNok = simpleHttpClient.post(wmRuntimeInfo.getHttpBaseUrl() + "/simple-post-json", Map.of(), Map.of("not", "correct"));
        assertFalse(resNok.isSuccessful());
        Assertions.assertEquals(404, resNok.getCode());

        var res = simpleHttpClient.post(wmRuntimeInfo.getHttpBaseUrl() + "/simple-post-json", Map.of(), Map.of("key", "value"));
        assertTrue(res.isSuccessful());
        Assertions.assertEquals(200, res.getCode());

        var res2 = simpleHttpClient.postJSON(wmRuntimeInfo.getHttpBaseUrl() + "/simple-post-json", Map.of(), Map.of("key", "value"));
        assertTrue(res2.isSuccessful());
        Assertions.assertEquals(200, res2.getCode());
    }

    @Test
    void testPostForm(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        stubFor(post("/simple-post-form").withRequestBody(containing("k1=v1")).withRequestBody(containing("k2=v2"))
            .willReturn(ok("Hello World!").withHeader("Content-Type", "text/plain")));

        var res = simpleHttpClient.postForm(wmRuntimeInfo.getHttpBaseUrl() + "/simple-post-form", Map.of(), Map.of("k1", "v1", "k2", "v2"));
        assertTrue(res.isSuccessful());
        Assertions.assertEquals(200, res.getCode());
    }

    @Test
    void testPostFile(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        stubFor(post("/simple-post-file").withRequestBody(containing("content"))
            .willReturn(ok("Hello World!").withHeader("Content-Type", "text/plain")));

        File tmp = File.createTempFile("test", "test");
        FileUtils.write(tmp, "content", StandardCharsets.UTF_8.toString());

        var res = simpleHttpClient.postFileAndSaveResponse(wmRuntimeInfo.getHttpBaseUrl() + "/simple-post-file", Map.of(), tmp.getAbsolutePath(), tmp.getName(), "text/plain");
        assertTrue(res.isSuccessful());
        Assertions.assertEquals(200, res.getCode());
        assertNotNull(res.getTempFilePath());
        var saved = new File(res.getTempFilePath());
        Assertions.assertEquals("Hello World!", FileUtils.readFileToString(saved, StandardCharsets.UTF_8.toString()));

        assertTrue(tmp.delete());
        assertTrue(saved.delete());
    }

    @Test
    void testPostBody(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        stubFor(post("/simple-post-body")
            .withHeader("Content-Type", containing("text/plain"))
            .withRequestBody(equalTo("content"))
            .willReturn(ok("Hello World!").withHeader("Content-Type", "text/plain")));

        var res = simpleHttpClient.postBodyAndSaveResponse(wmRuntimeInfo.getHttpBaseUrl() + "/simple-post-body", Map.of(), "content", "text/plain");
        assertTrue(res.isSuccessful());
        Assertions.assertEquals(200, res.getCode());
        assertNotNull(res.getTempFilePath());
        var saved = new File(res.getTempFilePath());
        Assertions.assertEquals("Hello World!", FileUtils.readFileToString(saved, StandardCharsets.UTF_8.toString()));
        assertTrue(saved.delete());
    }
}
