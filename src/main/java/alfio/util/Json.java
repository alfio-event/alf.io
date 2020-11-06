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
package alfio.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fatboyindustrial.gsonjavatime.Converters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;

public class Json {

    public static final Gson GSON = Converters.registerAll(new GsonBuilder()).create();


    private static final ObjectMapper mapper;

    static {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        m.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper = m;
    }

    public String asJsonString(Object o) {
        return toJson(o);
    }

    public <T> T fromJsonString(String value, Class<T> valueType) {
        return fromJson(value, valueType);
    }

    public static String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch(JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T fromJson(String value, Class<T> valueType) {
        try {
            return mapper.readValue(value, valueType);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T fromJson(String value, TypeReference<T> reference) {
        try {
            return mapper.readValue(value, reference);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
