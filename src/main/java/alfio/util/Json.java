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

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import com.fatboyindustrial.gsonjavatime.Converters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import tools.jackson.core.JacksonException;

public class Json {

    public static final Gson GSON = Converters.registerAll(new GsonBuilder()).create();


    public static final JsonMapper OBJECT_MAPPER;

    static {
        // Jackson 3 handles java.time and constructor parameter names out of the box,
        // so only the two non-default settings are kept here.
        OBJECT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    }

    public String asJsonString(Object o) {
        return toJson(o);
    }

    public <T> T fromJsonString(String value, Class<T> valueType) {
        return fromJson(value, valueType);
    }

    public static String toJson(Object o) {
        try {
            return OBJECT_MAPPER.writeValueAsString(o);
        } catch(JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T fromJson(String value, Class<T> valueType) {
        try {
            return OBJECT_MAPPER.readValue(value, valueType);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T fromJson(String value, TypeReference<T> reference) {
        try {
            return OBJECT_MAPPER.readValue(value, reference);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }
}
