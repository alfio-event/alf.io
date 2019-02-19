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

import alfio.util.Json;
import okhttp3.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SimpleHttpClient {


    private static final RequestBody EMPTY_REQUEST = RequestBody.create(null, new byte[0]);
    private static final Set<String> NULL_REQUEST_BODY = new HashSet<>(Arrays.asList("GET", "HEAD"));

    private final OkHttpClient okHttpClient;

    public SimpleHttpClient(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
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
        FormBody.Builder form = new FormBody.Builder();
        params.forEach(form::add);
        return callRemote(buildUrlAndHeader(url, headers).method("POST", form.build()).build());
    }

    public SimpleHttpClientCachedResponse postFileAndSaveResponse(String url, Map<String, String> headers, String file, String filename, String contentType) throws IOException {
        MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, RequestBody.create(MediaType.parse(contentType), file))
            .build();
        return callRemoteAndSaveResponse(buildUrlAndHeader(url, headers).post(body).build());
    }

    public SimpleHttpClientCachedResponse postBodyAndSaveResponse(String url, Map<String, String> headers, String file, String contentType) throws IOException {
        return callRemoteAndSaveResponse(buildUrlAndHeader(url, headers).post(RequestBody.create(MediaType.parse(contentType), file)).build());
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
        Request.Builder requestBuilder = buildUrlAndHeader(url, headers);
        Request req = requestBuilder.method(method,  NULL_REQUEST_BODY.contains(method)? null : buildRequestBody(requestBody)).build();
        return callRemote(req);
    }

    private SimpleHttpClientResponse callRemote(Request req) throws IOException {
        try (Response response = okHttpClient.newCall(req).execute()) {
            ResponseBody body = response.body();
            return new SimpleHttpClientResponse(
                response.isSuccessful(),
                response.code(),
                response.message(),
                response.headers().toMultimap(),
                body == null ? null : body.string());
        }
    }

    private SimpleHttpClientCachedResponse callRemoteAndSaveResponse(Request req) throws IOException {
        try (Response response = okHttpClient.newCall(req).execute()) {
            Path tempFile = null;
            if(response.isSuccessful()) {
                ResponseBody body = response.body();

                if(body != null) {
                    //saving the response to a temporary file
                    tempFile = Files.createTempFile("extension-out", ".tmp");
                    try (FileOutputStream out = new FileOutputStream(tempFile.toFile())) {
                        body.byteStream().transferTo(out);
                    }
                }
            }
            return new SimpleHttpClientCachedResponse(
                response.isSuccessful(),
                response.code(),
                response.message(),
                response.headers().toMultimap(),
                tempFile != null ? tempFile.toAbsolutePath().toString() : null);
        }
    }


    public String basicCredentials(String username, String password) {
        return Credentials.basic(username, password);
    }

    private static Request.Builder buildUrlAndHeader(String url, Map<String, String> headers) {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (headers != null) {
            headers.forEach(requestBuilder::header);
        }
        return requestBuilder;
    }

    private static RequestBody buildRequestBody(Object body) {
        return body == null ? EMPTY_REQUEST : RequestBody.create(MediaType.parse("application/json"), Json.GSON.toJson(body));
    }


}
