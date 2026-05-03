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

import io.roastedroot.quickjs4j.annotations.Builtins;
import io.roastedroot.quickjs4j.annotations.HostFunction;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Builtins("simpleHttpClient")
@SuppressWarnings({"rawtypes", "unchecked"})
class SimpleHttpClientApi {
    private final SimpleHttpClient httpClient;

    SimpleHttpClientApi(SimpleHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @HostFunction
    public SimpleHttpClientResponse get(String url, Map headers) {
        try {
            return httpClient.get(url, safe(headers));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @HostFunction
    public SimpleHttpClientResponse head(String url, Map headers) {
        try {
            return httpClient.head(url, safe(headers));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @HostFunction
    public SimpleHttpClientResponse post(String url, Map headers, Object body) {
        try {
            return httpClient.post(url, safe(headers), body);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @HostFunction
    public SimpleHttpClientResponse postJSON(String url, Map headers, Object body) {
        try {
            return httpClient.postJSON(url, safe(headers), body);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @HostFunction
    public SimpleHttpClientResponse postForm(String url, Map headers, Map params) {
        try {
            return httpClient.postForm(url, safe(headers), safe(params));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @HostFunction
    public SimpleHttpClientCachedResponse postFileAndSaveResponse(String url, Map headers, String file, String filename, String contentType) {
        try {
            return httpClient.postFileAndSaveResponse(url, safe(headers), file, filename, contentType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @HostFunction
    public SimpleHttpClientCachedResponse postBodyAndSaveResponse(String url, Map headers, String content, String contentType) {
        try {
            return httpClient.postBodyAndSaveResponse(url, safe(headers), content, contentType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @HostFunction
    public SimpleHttpClientResponse put(String url, Map headers, Object body) {
        try {
            return httpClient.put(url, safe(headers), body);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @HostFunction
    public SimpleHttpClientResponse patch(String url, Map headers, Object body) {
        try {
            return httpClient.patch(url, safe(headers), body);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @HostFunction
    public SimpleHttpClientResponse delete(String url, Map headers, Object body) {
        try {
            return httpClient.delete(url, safe(headers), body);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @HostFunction
    public SimpleHttpClientResponse method(String method, String url, Map headers, Object body) {
        try {
            return httpClient.method(method, url, safe(headers), body);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @HostFunction
    public String basicCredentials(String username, String password) {
        return httpClient.basicCredentials(username, password);
    }

    private static Map safe(Map map) {
        return map != null ? map : Collections.emptyMap();
    }
}
