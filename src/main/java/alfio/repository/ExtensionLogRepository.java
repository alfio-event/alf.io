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

package alfio.repository;

import alfio.model.ExtensionLog;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.List;

@QueryRepository
public interface ExtensionLogRepository {

    @Query("insert into extension_log(effective_path, path, name, description, type) values (:effectivePath, :path, :name, :description, :type)")
    int insert(@Bind("effectivePath") String effectivePath,
               @Bind("path") String path,
               @Bind("name") String name,
               @Bind("description") String description,
               @Bind("type") ExtensionLog.Type type);

    String FIND_EXTENSION_LOG = "select * from extension_log where ((:path is null or path = :path) and (:name is null or name = :name)) and (:type is null or type = :type) order by event_ts desc";

    @Query("select count(*) from (" + FIND_EXTENSION_LOG + ") as el_tbl")
    int countPages(@Bind("path") String path,
                   @Bind("name") String name,
                   @Bind("type") String type);

    @Query("select * from (" + FIND_EXTENSION_LOG + " limit :pageSize offset :offset) as el_tbl")
    List<ExtensionLog> getPage(@Bind("path") String path,
                               @Bind("name") String name,
                               @Bind("type") String type,
                               @Bind("pageSize") int pageSize,
                               @Bind("offset") int offset);
}
