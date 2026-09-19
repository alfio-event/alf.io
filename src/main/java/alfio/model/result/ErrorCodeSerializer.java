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

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

class ErrorCodeSerializer extends ValueSerializer<ErrorCode> {
    @Override
    public void serialize(ErrorCode value, JsonGenerator gen, SerializationContext serializers) {
        gen.writeStartObject();
        gen.writeStringProperty("fieldName", value.getLocation());
        gen.writeStringProperty("code", value.getCode());
        gen.writeStringProperty("description", value.getDescription());
        if(value.getArguments() != null) {
            gen.writeArrayPropertyStart("arguments");
            for (Object arg : value.getArguments()) {
                gen.writePOJO(arg);
            }
            gen.writeEndArray();
        }
        gen.writeEndObject();
    }
}
