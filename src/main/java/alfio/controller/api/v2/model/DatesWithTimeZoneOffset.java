package alfio.controller.api.v2.model;

import alfio.model.Event;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;

import java.io.IOException;
import java.time.ZonedDateTime;

@Data
public class DatesWithTimeZoneOffset {
    @JsonSerialize(using = DateSerializer.class)
    private final ZonedDateTime startDateTime;
    private final int startTimeZoneOffset;
    @JsonSerialize(using = DateSerializer.class)
    private final ZonedDateTime endDateTime;
    private final int endTimeZoneOffset;

    public static DatesWithTimeZoneOffset fromEvent(Event event) {
        return new DatesWithTimeZoneOffset(event.getBegin(), event.getBeginTimeZoneOffset(), event.getEnd(), event.getEndTimeZoneOffset());
    }

    private static class DateSerializer extends JsonSerializer<ZonedDateTime> {
        @Override
        public void serialize(ZonedDateTime zonedDateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeNumber(zonedDateTime.toInstant().toEpochMilli());
        }
    }
}

