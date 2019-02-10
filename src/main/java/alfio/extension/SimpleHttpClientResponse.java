package alfio.extension;

import alfio.util.Json;
import com.google.gson.JsonSyntaxException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class SimpleHttpClientResponse {
    private final boolean successful;
    private final int code;
    private final String message;
    private final Map<String, List<String>> headers;
    private final String body;


    public Object getJsonBody() {
        return tryParse(body, Object.class);
    }

    private static Object tryParse(String body, Class<?> clazz) {
        try {
            return Json.GSON.fromJson(body, clazz);
        } catch (JsonSyntaxException jse) {
            return null;
        }
    }
}
