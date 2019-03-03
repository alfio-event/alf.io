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
package alfio.config.support;

import alfio.model.support.JSONData;
import alfio.util.Json;
import ch.digitalfondue.npjt.mapper.ColumnMapper;
import ch.digitalfondue.npjt.mapper.ColumnMapperFactory;
import ch.digitalfondue.npjt.mapper.ParameterConverter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.lang.annotation.Annotation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

public class JSONColumnMapper extends ColumnMapper {

    private static final int ORDER = Integer.MAX_VALUE - 30;

    private JSONColumnMapper(String name, Class<?> paramType) {
        super(name, paramType);
    }

    @Override
    public Object getObject(ResultSet rs) throws SQLException {
        var jsonAsString = rs.getString(name);
        if(jsonAsString != null) {
            return Json.fromJson(jsonAsString, paramType);
        }
        return null;
    }

    private static boolean hasAnnotation(Annotation[] annotations) {
        return annotations != null
            && Arrays.stream(annotations).anyMatch(annotation -> annotation.annotationType() == JSONData.class);
    }

    public static class Factory implements ColumnMapperFactory {
        @Override
        public ColumnMapper build(String name, Class<?> paramType) {
            return new JSONColumnMapper(name, paramType);
        }

        @Override
        public int order() {
            return ORDER;
        }

        @Override
        public boolean accept(Class<?> paramType, Annotation[] annotations) {
            return hasAnnotation(annotations);
        }

        @Override
        public RowMapper<Object> getSingleColumnRowMapper(Class<Object> clazz) {
            return (resultSet, rowNum) -> {
                var jsonAsString = resultSet.getString(1);
                if(jsonAsString != null) {
                    return Json.fromJson(jsonAsString, clazz);
                }
                return null;
            };
        }
    }

    public static class Converter implements ParameterConverter {
        @Override
        public boolean accept(Class<?> parameterType, Annotation[] annotations) {
            return hasAnnotation(annotations);
        }

        @Override
        public void processParameter(String parameterName, Object arg, Class<?> parameterType, MapSqlParameterSource ps) {
            ps.addValue(parameterName, Json.toJson(arg));
        }

        @Override
        public int order() {
            return ORDER;
        }
    }
}
