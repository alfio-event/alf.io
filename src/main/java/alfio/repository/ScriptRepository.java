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

import alfio.model.ScriptSupport;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.List;

@QueryRepository
public interface ScriptRepository {

    @Query("insert into script_support(path, name, hash, enabled, async, script, configuration) values " +
        " (:path, :name, :hash, :enabled, :async, :script, :configuration)")
    int insert(@Bind("path") String path,
               @Bind("name") String name,
               @Bind("hash") String hash,
               @Bind("enabled") boolean enabled,
               @Bind("async") boolean async,
               @Bind("script") String script,
               @Bind("configuration") String configuration);

    @Query("update script_support set enabled = :enabled where path = :path")
    int toggle(@Bind("path") String path, @Bind("enabled") boolean enabled);

    @Query("insert into script_event(path, event) values " +
        " (:path, :event)")
    int insert(@Bind("path") String path, @Bind("event ") String event);

    @Query("select count(*) from script_support where path = :path")
    int hasPath(@Bind("path") String path);

    @Query("select script from script_support where path = :path")
    String getScript(@Bind("path") String path);

    @Query("delete from script_event where path = :path")
    int deleteEventsForPath(@Bind("path") String path);

    @Query("delete from script_support where path = :path")
    int deleteScriptForPath(@Bind("path") String path);

    @Query("select * from script_support order by path")
    List<ScriptSupport> listAll();
}
