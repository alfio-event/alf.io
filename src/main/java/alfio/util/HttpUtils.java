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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

public final class HttpUtils {
    private HttpUtils() {
        // no-op
    }

    public static final String CONTENT_TYPE = "Content-Type";
    public static final String APPLICATION_JSON = "application/json";
    public static final String APPLICATION_JSON_UTF8 = "application/json;charset=UTF-8";
    public static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";
    public static final String MULTIPART_FORM_DATA = "multipart/form-data";
    public static final String AUTHORIZATION = "Authorization";

    public static boolean callSuccessful(HttpResponse<?> response) {
        return statusCodeIsSuccessful(response.statusCode());
    }

    public static boolean statusCodeIsSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    public static HttpResponse<String> postForm(HttpClient httpClient, String url, Map<String, String> params) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpUtils.ofFormUrlEncodedBody(params))
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    public static <K, V> HttpRequest.BodyPublisher ofFormUrlEncodedBody(Map<K, V> data) {
        Objects.requireNonNull(data);
        StringBuilder sb = new StringBuilder();
        data.forEach((k,v) -> {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(URLEncoder.encode(k.toString(), StandardCharsets.UTF_8)).append("=").append(URLEncoder.encode(v.toString(), StandardCharsets.UTF_8));
        });
        return HttpRequest.BodyPublishers.ofString(sb.toString());
    }

    // https://stackoverflow.com/a/54675316
    public static class MultiPartBodyPublisher {
        private final List<PartsSpecification> partsSpecificationList = new ArrayList<>();
        private final String boundary = UUID.randomUUID().toString();

        public HttpRequest.BodyPublisher build() {
            if (partsSpecificationList.size() == 0) {
                throw new IllegalStateException("Must have at least one part to build multipart message.");
            }
            addFinalBoundaryPart();
            return HttpRequest.BodyPublishers.ofByteArrays(PartsIterator::new);
        }

        public String getBoundary() {
            return boundary;
        }

        public MultiPartBodyPublisher addPart(String name, String value) {
            PartsSpecification newPart = new PartsSpecification();
            newPart.type = PartsSpecification.TYPE.STRING;
            newPart.name = name;
            newPart.value = value;
            partsSpecificationList.add(newPart);
            return this;
        }

        public MultiPartBodyPublisher addPart(String name, Supplier<InputStream> value, String filename, String contentType) {
            PartsSpecification newPart = new PartsSpecification();
            newPart.type = PartsSpecification.TYPE.STREAM;
            newPart.name = name;
            newPart.stream = value;
            newPart.filename = filename;
            newPart.contentType = contentType;
            partsSpecificationList.add(newPart);
            return this;
        }

        private void addFinalBoundaryPart() {
            PartsSpecification newPart = new PartsSpecification();
            newPart.type = PartsSpecification.TYPE.FINAL_BOUNDARY;
            newPart.value = "--" + boundary + "--";
            partsSpecificationList.add(newPart);
        }

        static class PartsSpecification {

            public enum TYPE {
                STRING, STREAM, FINAL_BOUNDARY
            }

            PartsSpecification.TYPE type;
            String name;
            String value;
            Supplier<InputStream> stream;
            String filename;
            String contentType;
        }

        class PartsIterator implements Iterator<byte[]> {

            private Iterator<PartsSpecification> iter;
            private InputStream currentFileInput;

            private boolean done;
            private byte[] next;

            PartsIterator() {
                iter = partsSpecificationList.iterator();
            }

            @Override
            public boolean hasNext() {
                if (done) {
                    return false;
                }
                if (next != null) {
                    return true;
                }
                try {
                    next = computeNext();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (next == null) {
                    done = true;
                    return false;
                }
                return true;
            }

            @Override
            public byte[] next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                byte[] res = next;
                next = null;
                return res;
            }

            private byte[] computeNext() throws IOException {
                if (currentFileInput == null) {
                    if (!iter.hasNext()) return null;
                    PartsSpecification nextPart = iter.next();
                    if (PartsSpecification.TYPE.STRING.equals(nextPart.type)) {
                        String part =
                            "--" + boundary + "\r\n" +
                                "Content-Disposition: form-data; name=" + nextPart.name + "\r\n" +
                                "Content-Type: text/plain; charset=UTF-8\r\n\r\n" +
                                nextPart.value + "\r\n";
                        return part.getBytes(StandardCharsets.UTF_8);
                    }
                    if (PartsSpecification.TYPE.FINAL_BOUNDARY.equals(nextPart.type)) {
                        return nextPart.value.getBytes(StandardCharsets.UTF_8);
                    }
                    String filename = nextPart.filename;
                    String contentType = nextPart.contentType;

                    if (contentType == null) {
                        contentType = "application/octet-stream";
                    }
                    currentFileInput = nextPart.stream.get();

                    String partHeader =
                        "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=" + nextPart.name + "; filename=" + filename + "\r\n" +
                            "Content-Type: " + contentType + "\r\n\r\n";
                    return partHeader.getBytes(StandardCharsets.UTF_8);
                } else {
                    byte[] buf = new byte[8192];
                    int r = currentFileInput.read(buf);
                    if (r > 0) {
                        byte[] actualBytes = new byte[r];
                        System.arraycopy(buf, 0, actualBytes, 0, r);
                        return actualBytes;
                    } else {
                        currentFileInput.close();
                        currentFileInput = null;
                        return "\r\n".getBytes(StandardCharsets.UTF_8);
                    }
                }
            }
        }
    }
}
