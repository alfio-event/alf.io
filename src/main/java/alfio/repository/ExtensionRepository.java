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

import alfio.model.ExtensionSupport;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@QueryRepository
public interface ExtensionRepository {

    @Query("insert into extension_support(path, name, hash, enabled, async, script) values " +
        " (:path, :name, :hash, :enabled, :async, :script)")
    int insert(@Bind("path") String path,
               @Bind("name") String name,
               @Bind("hash") String hash,
               @Bind("enabled") boolean enabled,
               @Bind("async") boolean async,
               @Bind("script") String script);

    @Query("update extension_support set hash = :hash, enabled = :enabled, async = :async, script = :script where path = :path and name = :name")
    int update(@Bind("path") String path,
               @Bind("name") String name,
               @Bind("hash") String hash,
               @Bind("enabled") boolean enabled,
               @Bind("async") boolean async,
               @Bind("script") String script);

    @Query("update extension_support set enabled = :enabled where path = :path and name = :name")
    int toggle(@Bind("path") String path, @Bind("name") String name, @Bind("enabled") boolean enabled);

    @Query("insert into extension_event(path_fk, name_fk, event) values " +
        " (:path, :name, :event)")
    int insertEvent(@Bind("path") String path, @Bind("name") String name, @Bind("event") String event);

    @Query("select script from extension_support where path = :path and name = :name")
    String getScript(@Bind("path") String path, @Bind("name") String name);

    @Query("select * from extension_support where path = :path and name = :name")
    Optional<ExtensionSupport> getSingle(@Bind("path") String path, @Bind("name") String name);

    @Query("delete from extension_event where path_fk = :path and name_fk = :name")
    int deleteEventsForPath(@Bind("path") String path, @Bind("name") String name);

    @Query("delete from extension_support where path = :path and name = :name")
    int deleteScriptForPath(@Bind("path") String path, @Bind("name") String name);

    @Query("select * from extension_support order by name, path")
    List<ExtensionSupport> listAll();

    @Query("select a3.path, a3.name, a3.hash from " +
        " (select a1.* from " +
        " (select path, name, hash from extension_support where enabled = true and async = :async and (path in (:possiblePaths))) a1 " +
        " left outer join (select path, name from extension_support where enabled = true and async = :async and (path in (:possiblePaths))) a2 on " +
        " (a1.name = a2.name) and length(a1.path) < length(a2.path) where a2.path is null) a3 " +
        " " +
        " inner join extension_event on path_fk = a3.path and name_fk = a3.name where event = :event order by a3.name, a3.path")
    List<ExtensionSupport.ScriptPathNameHash> findActive(@Bind("possiblePaths") Set<String> possiblePaths,
                                                         @Bind("async") boolean async,
                                                         @Bind("event") String event);


}
