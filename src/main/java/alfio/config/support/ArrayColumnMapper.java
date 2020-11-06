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

import alfio.model.support.Array;
import ch.digitalfondue.npjt.mapper.ColumnMapper;
import ch.digitalfondue.npjt.mapper.ColumnMapperFactory;
import ch.digitalfondue.npjt.mapper.ParameterConverter;
import lombok.SneakyThrows;
import org.springframework.jdbc.core.RowMapper;

import java.lang.annotation.Annotation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

public class ArrayColumnMapper extends ColumnMapper {

    private static final int ORDER = Integer.MAX_VALUE - 31;

    private ArrayColumnMapper(String name, Class<?> paramType) {
        super(name, paramType);
    }

    @Override
    public Object getObject(ResultSet rs) throws SQLException {
        var array = rs.getArray(name);
        if(array != null) {
            return Arrays.asList((Object[]) array.getArray());
        }
        return null;
    }

    private static boolean hasAnnotation(Annotation[] annotations) {
        return annotations != null
            && Arrays.stream(annotations).anyMatch(ArrayColumnMapper::annotationFinder);
    }

    private static boolean annotationFinder(Annotation annotation){
        return annotation.annotationType() == Array.class;
    }

    public static class Factory implements ColumnMapperFactory {
        @Override
        public ColumnMapper build(String name, Class<?> paramType) {
            return new ArrayColumnMapper(name, paramType);
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
                var array = resultSet.getArray(1);
                if(array != null) {
                    return Arrays.asList((Object[]) array.getArray());
                }
                return null;
            };
        }
    }

    public static class Converter implements ParameterConverter.AdvancedParameterConverter {
        @Override
        public boolean accept(Class<?> parameterType, Annotation[] annotations) {
            return hasAnnotation(annotations) && List.class.isAssignableFrom(parameterType);
        }

        @SneakyThrows
        @Override
        public void processParameter(ProcessParameterContext processParameterContext) {
            var arg = processParameterContext.getArg();
            var ps = processParameterContext.getParameterSource();
            if(arg == null) {
                ps.addValue(processParameterContext.getParameterName(), null, Types.ARRAY);
            } else {
                Array def = (Array) Arrays.stream(processParameterContext.getParameterAnnotations()).filter(ArrayColumnMapper::annotationFinder).findFirst().orElseThrow();
                var array = processParameterContext.getConnection().createArrayOf(def.type(), ((List<?>) arg).toArray());
                ps.addValue(processParameterContext.getParameterName(), array);
            }
        }

        @Override
        public int order() {
            return ORDER;
        }
    }
}
