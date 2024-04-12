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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicReference;

public class RefreshableDataSource extends DelegatingDataSource {

    private final HikariConfig config;
    private final AtomicReference<HikariDataSource> dataSource = new AtomicReference<>();

    public RefreshableDataSource(HikariConfig config) {
        this.config = config;
        this.dataSource.set(new HikariDataSource(config));
    }

    @Override
    public DataSource getTargetDataSource() {
        return dataSource.get();
    }

    public void refresh() {
        this.dataSource.getAndSet(new HikariDataSource(config)).close();
    }

    public boolean isWrapperFor(Class<?> iface) throws java.sql.SQLException {
        // TODO Auto-generated method stub
        return iface != null && iface.isAssignableFrom(this.getClass());
    }

    public <T> T unwrap(Class<T> iface) throws java.sql.SQLException {
        // TODO Auto-generated method stub
        try {
            if(iface != null && iface.isAssignableFrom(this.getClass())) {
                return (T) this;
            }
            throw new java.sql.SQLException("Auto-generated unwrap failed; Revisit implementation");
        } catch (Exception e) {
            throw new java.sql.SQLException(e);
        }
    }

    public java.util.logging.Logger getParentLogger() {
        // TODO Auto-generated method stub
        return null;
    }

}
