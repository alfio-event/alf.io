package alfio.extension;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class SimpleHttpClientCachedResponse {
    private final boolean successful;
    private final int code;
    private final String message;
    private final Map<String, List<String>> headers;
    private final String tempFilePath;
}
