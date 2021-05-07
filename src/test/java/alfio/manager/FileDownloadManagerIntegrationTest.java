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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.http.HttpClient;

public class FileDownloadManagerIntegrationTest {

    private static ClientAndServer mockServer;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    public static void startServer() {
        mockServer = ClientAndServer.startClientAndServer(4242);

        mockServer
            .when(HttpRequest.request().withMethod("GET").withPath("/test.txt"))
            .respond(HttpResponse.response()
                .withStatusCode(200)
                .withHeader("Content-Type", "text/plain; charset=utf-8")
                .withBody("Hello World!"));

        mockServer
            .when(HttpRequest.request().withMethod("GET").withPath("/404"))
            .respond(HttpResponse.response().withStatusCode(404));
    }

    @AfterAll
    public static void stopServer() {
        mockServer.stop();
    }

    @Test
    public void testFileDownloadSuccess() {
        var file = new FileDownloadManager(httpClient).downloadFile("http://localhost:4242/test.txt");
        Assertions.assertEquals("text/plain; charset=utf-8", file.getType());
        Assertions.assertEquals("test.txt", file.getName());
        Assertions.assertEquals("Hello World!".length(), file.getFile().length);
    }

    @Test
    public void testFileDownloadNotFound() {
        var res = new FileDownloadManager(httpClient).downloadFile("http://localhost:4242/404");
        Assertions.assertNull(res);
    }
}
