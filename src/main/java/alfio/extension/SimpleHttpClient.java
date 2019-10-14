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

import alfio.util.HttpUtils;
import alfio.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SimpleHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleHttpClient.class);

    private static final Set<String> NULL_REQUEST_BODY = new HashSet<>(Arrays.asList("GET", "HEAD"));

    private final HttpClient httpClient;

    public SimpleHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public SimpleHttpClientResponse get(String url) throws IOException {
        return get(url, Collections.emptyMap());
    }

    public SimpleHttpClientResponse get(String url, Map<String, String> headers) throws IOException {
        return doRequest(url, headers, "GET", null);
    }


    public SimpleHttpClientResponse head(String url) throws IOException {
        return head(url, Collections.emptyMap());
    }

    public SimpleHttpClientResponse head(String url, Map<String, String> headers) throws IOException {
        return doRequest(url, headers, "HEAD", null);
    }

    public SimpleHttpClientResponse post(String url) throws IOException {
        return post(url, Collections.emptyMap());
    }

    public SimpleHttpClientResponse post(String url, Map<String, String> headers) throws IOException {
        return post(url, headers, null);
    }

    public SimpleHttpClientResponse post(String url, Map<String, String> headers, Object body) throws IOException {
        return postJSON(url, headers, body);
    }

    public SimpleHttpClientResponse postJSON(String url, Map<String, String> headers, Object body) throws IOException {
        return doRequest(url, headers, "POST", body);
    }

    public SimpleHttpClientResponse postForm(String url, Map<String, String> headers, Map<String, String> params) throws IOException {
        HttpRequest request = buildUrlAndHeader(url, headers, null)
            .POST(HttpUtils.ofMimeMultipartData(params, null))
            .build();
        return callRemote(request);
    }

    public SimpleHttpClientCachedResponse postFileAndSaveResponse(String url, Map<String, String> headers, String file, String filename, String contentType) throws IOException {
        HttpRequest request = buildUrlAndHeader(url, headers, null)
            .POST(HttpUtils.ofMimeMultipartData(file, filename, contentType, null))
            .build();
        return callRemoteAndSaveResponse(null);
    }

    public SimpleHttpClientCachedResponse postBodyAndSaveResponse(String url, Map<String, String> headers, String content, String contentType) throws IOException {
        HttpRequest request = buildUrlAndHeader(url, headers, contentType)
            .POST(HttpRequest.BodyPublishers.ofString(content))
            .build();
        return callRemoteAndSaveResponse(request);
    }

    public SimpleHttpClientResponse delete(String url) throws IOException {
        return delete(url, Collections.emptyMap());
    }

    public SimpleHttpClientResponse delete(String url, Map<String, String> headers) throws IOException {
        return delete(url, headers, null);
    }

    public SimpleHttpClientResponse delete(String url, Map<String, String> headers, Object body) throws IOException {
        return doRequest(url, headers, "DELETE", body);
    }


    public SimpleHttpClientResponse put(String url) throws IOException {
        return put(url, Collections.emptyMap());
    }

    public SimpleHttpClientResponse put(String url, Map<String, String> headers) throws IOException {
        return put(url, headers, null);
    }

    public SimpleHttpClientResponse put(String url, Map<String, String> headers, Object body) throws IOException {
        return doRequest(url, headers, "PUT", body);
    }


    public SimpleHttpClientResponse patch(String url) throws IOException {
        return patch(url, Collections.emptyMap());
    }

    public SimpleHttpClientResponse patch(String url, Map<String, String> headers) throws IOException {
        return patch(url, headers, null);
    }

    public SimpleHttpClientResponse patch(String url, Map<String, String> headers, Object body) throws IOException {
        return doRequest(url, headers, "PATCH", body);
    }

    public SimpleHttpClientResponse method(String method, String url, Map<String, String> headers, Object body) throws IOException {
        return doRequest(url, headers, method, body);
    }

    private SimpleHttpClientResponse doRequest(String url, Map<String, String> headers, String method, Object requestBody) throws IOException {
        HttpRequest request = buildUrlAndHeader(url, headers, NULL_REQUEST_BODY.contains(method) ? null : HttpUtils.APPLICATION_JSON)
            .method(method, buildRequestBody(requestBody))
            .build();
        return callRemote(request);
    }

    private SimpleHttpClientResponse callRemote(HttpRequest request) throws IOException {
        HttpResponse<String> response = null;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charset.defaultCharset()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logInterruption(exception);
        }
        return new SimpleHttpClientResponse(
            HttpUtils.callSuccessful(response),
            response.statusCode(),
            response.headers().map(),
            response.body());
    }

    private void logInterruption(InterruptedException exception) {
        LOGGER.warn("HTTP request interrupted", exception);
    }

    private SimpleHttpClientCachedResponse callRemoteAndSaveResponse(HttpRequest request) throws IOException {
        HttpResponse<InputStream> response = null;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logInterruption(exception);
        }
        Path tempFile = null;
        if (HttpUtils.callSuccessful(response)) {
            InputStream body = response.body();
            if (body != null) {
                tempFile = Files.createTempFile("extension-out", ".tmp");
                try (FileOutputStream out = new FileOutputStream(tempFile.toFile())) {
                    body.transferTo(out);
                }
            }
        }
        return new SimpleHttpClientCachedResponse(
            HttpUtils.callSuccessful(response),
            response.statusCode(),
            response.headers().map(),
            tempFile != null ? tempFile.toAbsolutePath().toString() : null);
    }

    // Thanks to: https://stackoverflow.com/questions/54208945/java-11-httpclient-not-sending-basic-authentication
    public void addBasicAuthorization(HttpRequest.Builder requestBuilder, String username, String password) {
        requestBuilder.header(HttpUtils.AUTHORIZATION, HttpUtils.basicAuth(username, password));
    }

    private static HttpRequest.Builder buildUrlAndHeader(String url, Map<String, String> headers, String contentType) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        requestBuilder.uri(URI.create(url));
        if (headers != null) {
            headers.forEach(requestBuilder::header);
        }
        if (contentType != null) {
            requestBuilder.header(HttpUtils.CONTENT_TYPE, contentType);
        }
        return requestBuilder;
    }

    private static HttpRequest.BodyPublisher buildRequestBody(Object body) {
        return body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(Json.GSON.toJson(body));
    }

}
