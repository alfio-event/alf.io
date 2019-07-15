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

import alfio.model.UploadedResource;
import alfio.model.modification.UploadBase64FileModification;
import alfio.util.Json;
import ch.digitalfondue.npjt.*;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.support.AbstractLobCreatingPreparedStatementCallback;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobCreator;
import org.springframework.jdbc.support.lob.LobHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@QueryRepository
public interface UploadedResourceRepository {

    @Query("select exists(select name from resource_global where name = :name) as res")
    boolean hasResource(@Bind("name") String name);

    @Query("select exists(select name from resource_organizer where name = :name and organization_id_fk = :organizationId) as res")
    boolean hasResource(@Bind("organizationId") int organizationId, @Bind("name") String name);

    @Query("select exists(select name from resource_event where name = :name and organization_id_fk = :organizationId  and event_id_fk = :eventId) as res")
    boolean hasResource(@Bind("organizationId") int organizationId, @Bind("eventId") int eventId, @Bind("name") String name);



    @Query("select name, content_size, content_type, creation_time, attributes, null as organization_id_fk, null as event_id_fk  from resource_global where name = :name")
    UploadedResource get(@Bind("name") String name);

    @Query("select name, content_size, content_type, creation_time, attributes, organization_id_fk, null as event_id_fk from resource_organizer where organization_id_fk = :organizationId  and name = :name")
    UploadedResource get(@Bind("organizationId") int organizationId, @Bind("name") String name);

    @Query("select name, content_size, content_type, creation_time, attributes, organization_id_fk, event_id_fk from resource_event where organization_id_fk = :organizationId  and event_id_fk = :eventId and name = :name")
    UploadedResource get(@Bind("organizationId") int organizationId, @Bind("eventId") int eventId, @Bind("name") String name);




    @Query("select name, content_size, content_type, creation_time, attributes, null as organization_id_fk, null as event_id_fk  from resource_global order by name asc")
    List<UploadedResource> findAll();

    @Query("select name, content_size, content_type, creation_time, attributes, organization_id_fk, null as event_id_fk from resource_organizer where organization_id_fk = :organizationId order by name asc")
    List<UploadedResource> findAll(@Bind("organizationId") int organizationId);

    @Query("select name, content_size, content_type, creation_time, attributes, organization_id_fk, event_id_fk from resource_event where organization_id_fk = :organizationId  and event_id_fk = :eventId order by name asc")
    List<UploadedResource> findAll(@Bind("organizationId") int organizationId, @Bind("eventId") int eventId);



    @Query("delete from resource_global where name = :name")
    int delete(@Bind("name") String name);

    @Query("delete from resource_organizer where name = :name and organization_id_fk = :organizationId")
    int delete(@Bind("organizationId") int organizationId, @Bind("name") String name);

    @Query("delete from resource_event where name = :name and organization_id_fk = :organizationId and event_id_fk = :eventId")
    int delete(@Bind("organizationId") int organizationId, @Bind("eventId") int eventId, @Bind("name") String name);


    Function<OutputStream, RowCallbackHandler> OUTPUT_CONTENT = out -> rs -> {
            try (InputStream is = rs.getBinaryStream("content")) {
                is.transferTo(out);
            } catch (IOException e) {
                throw new IllegalStateException("Error while copying data", e);
            }
        };

    default void fileContent(String name, OutputStream out) {
        SqlParameterSource param = new MapSqlParameterSource("name", name);
        getNamedParameterJdbcTemplate().query("select content from resource_global where name = :name", param, OUTPUT_CONTENT.apply(out));
    }

    default void fileContent(int organizationId, String name, OutputStream out) {
        SqlParameterSource param = new MapSqlParameterSource("name", name).addValue("organizationId", organizationId);
        getNamedParameterJdbcTemplate().query("select content from resource_organizer where name = :name and organization_id_fk = :organizationId", param, OUTPUT_CONTENT.apply(out));
    }

    default void fileContent(int organizationId, int eventId, String name, OutputStream out) {
        SqlParameterSource param = new MapSqlParameterSource("name", name).addValue("organizationId", organizationId).addValue("eventId", eventId);
        getNamedParameterJdbcTemplate().query("select content from resource_event where name = :name and organization_id_fk = :organizationId and event_id_fk = :eventId", param, OUTPUT_CONTENT.apply(out));
    }

    NamedParameterJdbcTemplate getNamedParameterJdbcTemplate();

    default int upload(Integer organizationId, Integer eventId, UploadBase64FileModification file, Map<String, String> attributes) {

        LobHandler lobHandler = new DefaultLobHandler();

        String query = "insert into resource_global (name, content_size, content, content_type, attributes) values(?, ?, ?, ?, ?)";
        if (organizationId != null && eventId != null) {
            query = "insert into resource_event (name, content_size, content, content_type, attributes, organization_id_fk, event_id_fk) values(?, ?, ?, ?, ?, ?, ?)";
        } else if(organizationId != null) {
            query = "insert into resource_organizer (name, content_size, content, content_type, attributes, organization_id_fk) values(?, ?, ?, ?, ?, ?)";
        }

        return getNamedParameterJdbcTemplate().getJdbcOperations().execute(query,
            new AbstractLobCreatingPreparedStatementCallback(lobHandler) {
                @Override
                protected void setValues(PreparedStatement ps, LobCreator lobCreator) throws SQLException {
                    ps.setString(1, file.getName());
                    ps.setLong(2, file.getFile().length);
                    lobCreator.setBlobAsBytes(ps, 3, file.getFile());
                    ps.setString(4, file.getType());
                    ps.setString(5, Json.GSON.toJson(attributes));
                    if (organizationId != null) {
                        ps.setInt(6, organizationId);
                    }
                    if (eventId != null) {
                        ps.setInt(7, eventId);
                    }
                }
            }
        );
    }

}
