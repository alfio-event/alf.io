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

import alfio.model.FileBlobMetadata;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import ch.digitalfondue.npjt.QueryType;

import java.util.Date;
import java.util.Optional;

@QueryRepository
public interface FileUploadRepository {

    @Query("select count(id) from file_blob where id = :id")
    Integer isPresent(@Bind("id") String id);

    @Query("select id, name, content_size, content_type, attributes from file_blob where id = :id")
    Optional<FileBlobMetadata> findById(@Bind("id") String id);

    @Query("delete from file_blob where creation_time <= :date and id not in (select file_blob_id from event where file_blob_id is not null)")
    int cleanupUnreferencedBlobFiles(@Bind("date") Date date);

    @Query(type = QueryType.TEMPLATE, value = "insert into file_blob (id, name, content_size, content, content_type, attributes) " +
            "values(?, ?, ?, ?, ?, ?)")
    String uploadTemplate();

    @Query(type = QueryType.TEMPLATE, value = "select content from file_blob where id = :id")
    String fileContent(@Bind("id") String id);

}
