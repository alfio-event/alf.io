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

import alfio.model.modification.UploadBase64FileModification;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.regex.Pattern;


@Log4j2
public class FileDownloadManager {

    private final HttpClient httpClient;

    public FileDownloadManager(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public DownloadedFile downloadFile(String url) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> response = null;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException exception) {
            logWarning(exception);
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logWarning(exception);
            return null;
        }
        if(callSuccessful(response)) {
            String[] parts = Pattern.compile("/").split(url);
            String name = parts[parts.length - 1];
            if(Objects.nonNull(response.body())) {
                return new DownloadedFile(
                        response.body(),
                        name,
                        response.headers().firstValue("Content-Type").orElse("application/octet-stream")
                    );
            } else {
                return null;
            }
        } else {
            log.warn("downloading file not successful:" + response);
            return null;
        }
    }

    private void logWarning(Throwable exception) {
        log.warn("error while downloading file", exception);
    }

    private boolean callSuccessful(HttpResponse<?> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    @Getter
    @AllArgsConstructor
    public static class DownloadedFile {
        private byte[] file;
        private String name;
        private String type;

        public UploadBase64FileModification toUploadBase64FileModification() {
            UploadBase64FileModification uf = new UploadBase64FileModification();
            uf.setFile(file);
            uf.setName(name);
            uf.setType(type);
            return uf;
        }
    }

}
