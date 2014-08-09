/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.datamapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.StringUtils;

/**
 * Query Type:
 * 
 * <ul>
 * <li>TEMPLATE : we receive the string defined in @Query/@QueryOverride annotation.
 * <li>EXECUTE : the query will be executed. If it's a select, the result will be mapped with a
 * ConstructorAnnotationRowMapper if it has the correct form.
 * </ul>
 * 
 */
public enum QueryType {
	/**
	 * Receive the string defined in @Query/@QueryOverride annotation.
	 */
	TEMPLATE {
		@Override
		Object apply(String template, NamedParameterJdbcTemplate jdbc, Method method, Object[] args) {
			return template;
		}
	},

	/**
	 */
	EXECUTE {

		/**
		 * Keep a mapping between a given class and a possible RowMapper.
		 * 
		 * If the Class has the correct form, a ConstructorAnnotationRowMapper will be built and the boolean set to true
		 * in the pair. If the class has not the correct form, the boolean will be false and the class will be used as
		 * it is in the jdbc template.
		 */
		private final Map<Class<Object>, HasRowmapper> cachedClassToMapper = new ConcurrentHashMap<Class<Object>, HasRowmapper>();

		@Override
		Object apply(String template, NamedParameterJdbcTemplate jdbc, Method method, Object[] args) {
			JdbcAction action = actionFromTemplate(template);
			Map<String, Object> parameters = extractParameters(method, args);
			if (action == JdbcAction.QUERY) {
				return doQuery(template, jdbc, method, parameters);
			} else {
				return jdbc.update(template, parameters);
			}
		}

		@SuppressWarnings("unchecked")
		private Object doQuery(String template, NamedParameterJdbcTemplate jdbc, Method method,
				Map<String, Object> parameters) {
			if (method.getReturnType().isAssignableFrom(List.class)) {
				Class<Object> c = (Class<Object>) ((ParameterizedType) method.getGenericReturnType())
						.getActualTypeArguments()[0];
				HasRowmapper r = ensurePresence(c);
				if (r.present) {
					return jdbc.query(template, parameters, r.rowMapper);
				} else {
					return jdbc.queryForList(template, parameters, c);
				}
			} else {
				Class<Object> c = (Class<Object>) method.getReturnType();
				HasRowmapper r = ensurePresence(c);
				if (r.present) {
					return jdbc.queryForObject(template, parameters, r.rowMapper);
				} else {
					return jdbc.queryForObject(template, parameters, c);
				}
			}
		}

		private HasRowmapper ensurePresence(Class<Object> c) {
			if (!cachedClassToMapper.containsKey(c)) {
				cachedClassToMapper.put(c, handleClass(c));
			}
			return cachedClassToMapper.get(c);
		}
	};

	private static JdbcAction actionFromTemplate(String template) {
		String tmpl = StringUtils.deleteAny(template.toLowerCase(Locale.ENGLISH), "() ").trim();
		return tmpl.indexOf("select") == 0 ? JdbcAction.QUERY : JdbcAction.UPDATE;
	}

	private enum JdbcAction {
		QUERY, UPDATE
	}

	abstract Object apply(String template, NamedParameterJdbcTemplate jdbc, Method method, Object[] args);

	private static HasRowmapper handleClass(Class<Object> c) {
		if (ConstructorAnnotationRowMapper.hasConstructorInTheCorrectForm(c)) {
			return new HasRowmapper(true, new ConstructorAnnotationRowMapper<Object>(c));
		} else {
			return new HasRowmapper(false, null);
		}
	}

	private static Map<String, Object> extractParameters(Method m, Object[] args) {

		Annotation[][] parameterAnnotations = m.getParameterAnnotations();
		if (parameterAnnotations == null || parameterAnnotations.length == 0) {
			return Collections.emptyMap();
		}

		Map<String, Object> r = new HashMap<>();
		for (int i = 0; i < args.length; i++) {
			String name = parameterName(parameterAnnotations[i]);
			if (name != null) {
				r.put(name, args[i]);
			}
		}

		return r;
	}

	private static String parameterName(Annotation[] annotation) {

		if (annotation == null) {
			return null;
		}

		for (Annotation a : annotation) {
			if (a instanceof Bind) {
				return ((Bind) a).value();
			}
		}
		return null;
	}

	private static class HasRowmapper {
		private final boolean present;
		private final RowMapper<Object> rowMapper;

		HasRowmapper(boolean present, RowMapper<Object> rowMapper) {
			this.present = present;
			this.rowMapper = rowMapper;
		}
	}
}
