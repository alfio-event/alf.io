package alfio.model.result;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

class ErrorCodeSerializer extends JsonSerializer<ErrorCode> {
    @Override
    public void serialize(ErrorCode value, JsonGenerator gen, SerializerProvider serializers) throws IOException, JsonProcessingException {
        gen.writeStartObject();
        gen.writeStringField("code", value.getCode());
        gen.writeStringField("description", value.getDescription());
        gen.writeEndObject();
    }
}
