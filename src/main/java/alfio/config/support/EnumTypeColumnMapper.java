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

import alfio.model.support.EnumTypeAsString;
import ch.digitalfondue.npjt.mapper.ColumnMapper;
import ch.digitalfondue.npjt.mapper.ColumnMapperFactory;
import ch.digitalfondue.npjt.mapper.ParameterConverter;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;

@Log4j2
public class EnumTypeColumnMapper extends ColumnMapper {

    private static final int ORDER = Integer.MAX_VALUE - 32;

    private EnumTypeColumnMapper(String name, Class<?> paramType) {
        super(name, paramType);
    }

    @Override
    public Object getObject(ResultSet rs) throws SQLException {
        var enumAsString = rs.getString(name);
        if (enumAsString != null) {
            return parseValue(paramType, enumAsString);
        }
        return null;
    }

    private static boolean isSupported(Class<?> paramType, Annotation[] annotations) {
        return annotations != null
            && Arrays.stream(annotations).anyMatch(annotation -> annotation.annotationType() == EnumTypeAsString.class)
            && Enum.class.isAssignableFrom(paramType);
    }

    public static class Factory implements ColumnMapperFactory {

        @Override
        public ColumnMapper build(String name, Class<?> paramType) {
            return new EnumTypeColumnMapper(name, paramType);
        }

        @Override
        public int order() {
            return ORDER;
        }

        @Override
        public boolean accept(Class<?> paramType, Annotation[] annotations) {
            return isSupported(paramType, annotations);
        }

        @Override
        public RowMapper<Object> getSingleColumnRowMapper(Class<Object> clazz) {
            return (resultSet, rowNum) -> {
                var enumAsString = resultSet.getString(1);
                if(enumAsString != null) {
                    return parseValue(clazz, enumAsString);
                }
                return null;
            };
        }
    }

    public static class Converter implements ParameterConverter {

        @Override
        public boolean accept(Class<?> parameterType, Annotation[] annotations) {
            return isSupported(parameterType, annotations);
        }

        @Override
        public void processParameter(String parameterName, Object arg, Class<?> parameterType, MapSqlParameterSource ps) {
            String value = arg != null ? arg.toString() : null;
            ps.addValue(parameterName, value);
        }

        @Override
        public int order() {
            return ORDER;
        }
    }

    private static Object parseValue(Class<?> clazz, String enumAsString) {
        try {
            var method = clazz.getMethod("valueOf", String.class);
            return method.invoke(null, Objects.requireNonNull(enumAsString));
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new IllegalStateException("unexpected exception while deserializing Enum value", e);
        }
    }
}
