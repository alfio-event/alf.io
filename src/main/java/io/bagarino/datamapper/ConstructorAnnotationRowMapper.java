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
package io.bagarino.datamapper;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.Assert;

import java.lang.annotation.*;
import java.lang.reflect.Constructor;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class ConstructorAnnotationRowMapper<T> implements RowMapper<T> {

	private final Constructor<T> con;
	private final List<ColumnMapper> mappedColumn;

	/**
	 * Check if the given class has the correct form.
	 * 
	 * <ul>
	 * <li>must have exactly one public constructor.</li>
	 * <li>must at least have one parameter.</li>
	 * <li>all the parameters must be annotated with @Column annotation.</li>
	 * </ul>
	 * 
	 * @param clazz
	 * @return
	 */
	public static boolean hasConstructorInTheCorrectForm(Class<?> clazz) {

		if (clazz.getConstructors().length != 1) {
			return false;
		}

		Constructor<?> con = clazz.getConstructors()[0];

		if (con.getParameterTypes().length == 0) {
			return false;
		}

		Annotation[][] parameterAnnotations = con.getParameterAnnotations();
		for (Annotation[] as : parameterAnnotations) {
			if (!hasColumnAnnotation(as)) {
				return false;
			}
		}

		return true;
	}

	private static boolean hasColumnAnnotation(Annotation[] as) {
		if (as == null || as.length == 0) {
			return false;
		}
		for (Annotation a : as) {
			if (a.annotationType().isAssignableFrom(Column.class)) {
				return true;
			}
		}

		return false;
	}

	@SuppressWarnings("unchecked")
	public ConstructorAnnotationRowMapper(Class<T> clazz) {
		int constructorCount = clazz.getConstructors().length;
		Assert.isTrue(constructorCount == 1, "The class " + clazz.getName()
				+ " must have exactly one public constructor, " + constructorCount + " are present");

		con = (Constructor<T>) clazz.getConstructors()[0];
		mappedColumn = from(clazz, con.getParameterAnnotations(), con.getParameterTypes());
	}

	@Override
	public T mapRow(ResultSet rs, int rowNum) throws SQLException {
		List<Object> vals = new ArrayList<>(mappedColumn.size());

		for (ColumnMapper colMapper : mappedColumn) {
			vals.add(colMapper.getObject(rs));
		}

		try {
			return con.newInstance(vals.toArray(new Object[vals.size()]));
		} catch (ReflectiveOperationException e) {
			throw new SQLException(e);
		} catch (IllegalArgumentException e) {
			throw new SQLException("type mismatch between the expected one from the construct and the one passed,"
					+ " check 1: some values are null and passed to primitive types 2: incompatible numeric types", e);
		}
	}

	private static List<ColumnMapper> from(Class<?> clazz, Annotation[][] annotations, Class<?>[] paramTypes) {
		List<ColumnMapper> res = new ArrayList<>();
		for (int i = 0; i < annotations.length; i++) {
			res.add(findColumnAnnotationValue(clazz, i, annotations[i], paramTypes[i]));
		}
		return res;
	}

	private static ColumnMapper findColumnAnnotationValue(Class<?> clazz, int position, Annotation[] annotations,
			Class<?> paramType) {

		for (Annotation a : annotations) {
			if (Column.class.isAssignableFrom(a.annotationType())) {

				String name = ((Column) a).value();

                if (paramType.isAssignableFrom(ZonedDateTime.class)) {
                    return new ZonedDateTimeColumnMapper(name);
                } else if (paramType.isEnum()) {
					return new EnumColumnMapper(name, paramType);
				} else if (boolean.class == paramType || Boolean.class == paramType) {
					return new BooleanColumnMapper(name);
				} else {
					return new ColumnMapper(name);
				}
			}
		}

		throw new IllegalStateException("No annotation @Column found for class: " + clazz.getName()
				+ " in constructor at position " + position);
	}

	static class ColumnMapper {
		protected final String name;

		ColumnMapper(String name) {
			this.name = name;
		}

		public Object getObject(ResultSet rs) throws SQLException {
			Object res = rs.getObject(name);
			if (res != null && Clob.class.isAssignableFrom(res.getClass())) {
				try (ClobAutoCloseable clob = new ClobAutoCloseable((Clob) res)) {
					return clob.clob.getSubString(1, (int) clob.clob.length());
				}
			} else {
				return res;
			}
		}
	}

	private static class ClobAutoCloseable implements AutoCloseable {

		private final Clob clob;

		public ClobAutoCloseable(Clob clob) {
			this.clob = clob;
		}

		@Override
		public void close() throws SQLException {
			clob.free();
		}
	}

	static class BooleanColumnMapper extends ColumnMapper {

		BooleanColumnMapper(String name) {
			super(name);
		}

		public Object getObject(ResultSet rs) throws SQLException {
			Object res = rs.getObject(name);
			Class<?> resClass = res == null ? null : res.getClass();
			if (res == null || Boolean.class.isAssignableFrom(resClass)) {
				return res;
			} else if (Number.class.isAssignableFrom(resClass)) {
				return 1 == ((Number) res).intValue();
			} else if (String.class.isAssignableFrom(resClass)) {
				return "true".equalsIgnoreCase(res.toString());
			} else {
				throw new IllegalArgumentException("was not able to extract a boolean value");
			}
		}
	}

    static class ZonedDateTimeColumnMapper extends ColumnMapper {

        ZonedDateTimeColumnMapper(String name) {
            super(name);
        }

        public Object getObject(ResultSet rs) throws SQLException {
            Timestamp timestamp = rs.getTimestamp(name, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
            if(timestamp == null) {
                return null;
            }
            return ZonedDateTime.ofInstant(timestamp.toInstant(), ZoneId.of("UTC"));
        }
    }

	static class EnumColumnMapper extends ColumnMapper {

		@SuppressWarnings("rawtypes")
		private final Class<? extends Enum> enumType;

		@SuppressWarnings("unchecked")
		EnumColumnMapper(String name, Class<?> enumType) {
			super(name);
			this.enumType = (Class<? extends Enum<?>>) enumType;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Object getObject(ResultSet rs) throws SQLException {
			String res = rs.getString(name);
			return res == null ? null : Enum.valueOf(enumType, res);
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	public @interface Column {
		/**
		 * Column name
		 * 
		 * @return
		 */
		String value();
	}

}
