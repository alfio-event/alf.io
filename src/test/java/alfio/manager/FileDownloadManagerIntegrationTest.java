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
package alfio.manager;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@WireMockTest
class FileDownloadManagerIntegrationTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void testFileDownloadSuccess(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get("/test.txt")
            .willReturn(ok("Hello World!")
                .withHeader("Content-Type", "text/plain; charset=utf-8")));

        var file = new FileDownloadManager(httpClient).downloadFile(wmRuntimeInfo.getHttpBaseUrl() + "/test.txt");
        Assertions.assertEquals("text/plain; charset=utf-8", file.getType());
        Assertions.assertEquals("test.txt", file.getName());
        Assertions.assertEquals("Hello World!".length(), file.getFile().length);
    }

    @Test
    void testFileDownloadNotFound(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get("/404")
            .willReturn(notFound()));

        var res = new FileDownloadManager(httpClient).downloadFile(wmRuntimeInfo.getHttpBaseUrl() + "/404");
        Assertions.assertNull(res);
    }
}
