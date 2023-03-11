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

}
