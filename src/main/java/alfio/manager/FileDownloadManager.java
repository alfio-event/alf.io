package alfio.manager;

import alfio.model.modification.UploadBase64FileModification;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class FileDownloadManager {

    private final OkHttpClient client = new OkHttpClient();

    public DownloadedFile downloadFile(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();

        InputStream is = response.body().byteStream();

        BufferedInputStream input = new BufferedInputStream(is);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        byte[] data = new byte[1024];

        long total = 0;

        int count = input.read(data);
        while (count != -1) {
            total += count;
            output.write(data, 0, count);
            count = input.read(data);
        }

        output.flush();
        output.close();
        input.close();

        String[] parts = url.split("/");
        String name = parts[parts.length -1];

        return new DownloadedFile(
            output.toByteArray(),
            name,
            response.header("Content-Type","application/octet-stream")
        );
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
