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

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class RefreshableDataSource implements DataSource {

    private final HikariConfig config;
    private final AtomicReference<HikariDataSource> dataSource = new AtomicReference<>();

    public RefreshableDataSource(HikariConfig config) {
        this.config = config;
        this.dataSource.set(new HikariDataSource(config));
    }

    @Override
    public Connection getConnection() throws SQLException {
        return this.dataSource.get().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return this.dataSource.get().getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return this.dataSource.get().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        this.dataSource.get().setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        this.dataSource.get().setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return this.dataSource.get().getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return this.dataSource.get().getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return this.dataSource.get().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return this.dataSource.get().isWrapperFor(iface);
    }

    public void refresh() {
        this.dataSource.getAndSet(new HikariDataSource(config)).close();
    }

    public int getIdleConnections() {
        return this.dataSource.get().getHikariPoolMXBean().getIdleConnections();
    }

    public int getActiveConnections() {
        return this.dataSource.get().getHikariPoolMXBean().getActiveConnections();
    }
}
