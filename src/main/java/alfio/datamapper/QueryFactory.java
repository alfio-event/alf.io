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
package alfio.datamapper;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

public class QueryFactory {

	private final String activeDb;
	private final NamedParameterJdbcTemplate jdbc;

	public QueryFactory(String activeDB, NamedParameterJdbcTemplate jdbc) {
		this.activeDb = activeDB;
		this.jdbc = jdbc;
	}

	public static <T> T from(final Class<T> clazz, final String activeDb) {
		return from(clazz, activeDb, null);
	}

	private static class QueryTypeAndQuery {
		private final QueryType type;
		private final String query;

		QueryTypeAndQuery(QueryType type, String query) {
			this.type = type;
			this.query = query;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T from(final Class<T> clazz, final String activeDb, final NamedParameterJdbcTemplate jdbc) {
		return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] { clazz }, (proxy, method, args) -> {
            QueryTypeAndQuery qs = extractQueryAnnotation(clazz, activeDb, method);
            return qs.type.apply(qs.query, jdbc, method, args);
        });
	}

	private static QueryTypeAndQuery extractQueryAnnotation(Class<?> clazz, String activeDb, Method method) {
		Query q = Optional.ofNullable(method.getAnnotation(Query.class)).orElseThrow(() -> new IllegalArgumentException(String.format("missing @Query annotation for method %s in interface %s", method.getName(),	clazz.getSimpleName())));
		QueriesOverride qs = method.getAnnotation(QueriesOverride.class);

		// only one @Query annotation, thus we return the value without checking the database
		if (qs == null) {
			return new QueryTypeAndQuery(q.type(), q.value());
		}

		for (QueryOverride query : qs.value()) {
			if (query.db().equals(activeDb)) {
				return new QueryTypeAndQuery(q.type(), query.value());
			}
		}

		return new QueryTypeAndQuery(q.type(), q.value());
	}

	public <T> T from(final Class<T> clazz) {
		return from(clazz, activeDb, jdbc);
	}

}
