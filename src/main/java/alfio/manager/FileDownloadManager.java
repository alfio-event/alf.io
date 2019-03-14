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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Objects;


@Log4j2
public class FileDownloadManager {

    private final OkHttpClient client = new OkHttpClient();

    public DownloadedFile downloadFile(String url) {
        Request request = new Request.Builder().url(url).build();

        try(Response response = client.newCall(request).execute()) {
            if(response.isSuccessful()) {
                String[] parts = url.split("/");
                String name = parts[parts.length - 1];

                byte[] bytes = Objects.requireNonNull(response.body()).bytes();
                if(bytes.length <= FileUploadManager.MAXIMUM_ALLOWED_SIZE) {
                    return new DownloadedFile(
                        bytes,
                        name,
                        response.header("Content-Type", "application/octet-stream")
                    );
                } else {
                    return null;
                }
            } else {
                log.warn("downloading file not successful:" + response);
                return null;
            }
        } catch(IOException e) {
            log.warn("error while downloading file", e);
            return null;
        }
    }

    @Getter
    @AllArgsConstructor
    public class DownloadedFile{
        private byte[] file;
        private String name;
        private String type;

        public UploadBase64FileModification toUploadBase64FileModification() {
            UploadBase64FileModification file = new UploadBase64FileModification();
            file.setFile(this.file);
            file.setName(name);
            file.setType(type);
            return file;
        }
    }

}
