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
package alfio.util;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class HttpUtils {
    private HttpUtils() {
        // no-op
    }

    public static final String CONTENT_TYPE = "Content-Type";
    public static final String APPLICATION_JSON = "application/json";
    public static final String AUTHORIZATION = "Authorization";

    public static boolean callSuccessful(HttpResponse<?> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    public static HttpResponse<String> postForm(HttpClient httpClient, String url, Map<String, String> params) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpUtils.ofFormUrlEncodedBody(params))
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static String basicAuth(String publicKey, String privateKey) {
        return "Basic " + java.util.Base64.getEncoder().encodeToString((publicKey + ":" + privateKey).getBytes());
    }

    public static <K, V> HttpRequest.BodyPublisher ofFormUrlEncodedBody(Map<K, V> data) {
        Objects.requireNonNull(data);
        StringBuilder sb = new StringBuilder();
        data.forEach((k,v) -> {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(k).append("=").append(URLEncoder.encode(v.toString(), StandardCharsets.UTF_8));
        });
        return HttpRequest.BodyPublishers.ofString(sb.toString());
    }

    public static HttpRequest.BodyPublisher ofMimeMultipartData(String file, String filename, String contentType) throws IOException {
        Objects.requireNonNull(file);
        var boundary = new BigInteger(256, ThreadLocalRandom.current()).toString();
        Path path = Paths.get(file);
        return buildFromFileContents(path, filename, contentType, boundary);
    }

    private static HttpRequest.BodyPublisher buildFromFileContents(Path path, String filename, String contentType, String boundary) throws IOException {
        var byteArrays = new ArrayList<byte[]>();
        byte[] separator = ("--" + boundary + "\r\nContent-Disposition: form-data; name=").getBytes(StandardCharsets.UTF_8);
        byteArrays.add(separator);
        byteArrays.add(("\"file\"; filename=\"" + filename + "\"\r\n" + CONTENT_TYPE + ": " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        byteArrays.add(Files.readAllBytes(path));
        byteArrays.add("\r\n".getBytes(StandardCharsets.UTF_8));
        byteArrays.add(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
    }
}
