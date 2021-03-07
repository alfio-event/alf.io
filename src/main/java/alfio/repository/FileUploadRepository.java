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
import alfio.model.modification.UploadBase64FileModification;
import alfio.util.Json;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.support.AbstractLobCreatingPreparedStatementCallback;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobCreator;
import org.springframework.jdbc.support.lob.LobHandler;

import java.io.*;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@QueryRepository
public interface FileUploadRepository {

    @Query("select count(id) from file_blob where id = :id")
    Integer isPresent(@Bind("id") String id);

    @Query("select id, name, content_size, content_type, attributes from file_blob where id = :id")
    Optional<FileBlobMetadata> findById(@Bind("id") String id);

    @Query("delete from file_blob where creation_time <= :date and id not in (" +
        "select file_blob_id from event where file_blob_id is not null" +
        " union " +
        "select file_blob_id_fk as file_blob_id from subscription_descriptor where file_blob_id_fk is not null" +
        ")")
    int cleanupUnreferencedBlobFiles(@Bind("date") Date date);

    default void upload(UploadBase64FileModification file, String digest, Map<String, String> attributes) {
        LobHandler lobHandler = new DefaultLobHandler();

        NamedParameterJdbcTemplate jdbc = getNamedParameterJdbcTemplate();

        jdbc.getJdbcOperations().execute("insert into file_blob (id, name, content_size, content, content_type, attributes) values(?, ?, ?, ?, ?, ?)",
            new AbstractLobCreatingPreparedStatementCallback(lobHandler) {
                @Override
                protected void setValues(PreparedStatement ps, LobCreator lobCreator) throws SQLException {
                    ps.setString(1, digest);
                    ps.setString(2, file.getName());
                    ps.setLong(3, file.getFile().length);
                    lobCreator.setBlobAsBytes(ps, 4, file.getFile());
                    ps.setString(5, file.getType());
                    ps.setString(6, Json.GSON.toJson(attributes));
                }
            });
    }

    NamedParameterJdbcTemplate getNamedParameterJdbcTemplate();

    default File file(String id) {
        try {
            File cachedFile = File.createTempFile("fileupload-cache", ".tmp");
            cachedFile.deleteOnExit();
            SqlParameterSource param = new MapSqlParameterSource("id", id);
            getNamedParameterJdbcTemplate().query("select content from file_blob where id = :id", param, rs -> {
                try (InputStream is = rs.getBinaryStream("content"); OutputStream os = new FileOutputStream(cachedFile)) {
                    is.transferTo(os);
                } catch (IOException e) {
                    throw new IllegalStateException("Error while copying data", e);
                }
            });
            return cachedFile;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
