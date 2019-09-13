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
package alfio.model.result;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

class ErrorCodeSerializer extends JsonSerializer<ErrorCode> {
    @Override
    public void serialize(ErrorCode value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("fieldName", value.getLocation());
        gen.writeStringField("code", value.getCode());
        gen.writeStringField("description", value.getDescription());
        if(value.getArguments() != null) {
            gen.writeArrayFieldStart("arguments");
            for (Object arg : value.getArguments()) {
                gen.writeObject(arg);
            }
            gen.writeEndArray();
        }
        gen.writeEndObject();
    }
}
