package alfio.manager;

import alfio.model.modification.UploadBase64FileModification;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;


@Log4j2
@Component
public class FileDownloadManager {

    private final OkHttpClient client = new OkHttpClient();

    public DownloadedFile downloadFile(String url) {
        Request request = new Request.Builder().url(url).build();

        try(Response response = client.newCall(request).execute()) {
            if(response.isSuccessful()) {
                String[] parts = url.split("/");
                String name = parts[parts.length - 1];

                return new DownloadedFile(
                    response.body().bytes(),
                    name,
                    response.header("Content-Type", "application/octet-stream")
                );
            } else {
                log.warn("downloading file not successful:" + response);
                return null;
            }
        } catch(IOException e) {
            log.warn("error while downloading file");
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
