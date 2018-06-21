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
import ch.digitalfondue.npjt.*;

import java.util.List;

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



    @Query(type = QueryType.TEMPLATE, value = "select content from resource_global where name = :name")
    String fileContentTemplate(String name);

    @Query(type = QueryType.TEMPLATE, value = "select content from resource_organizer where name = :name and organization_id_fk = :organizationId")
    String fileContentTemplate(int organizationId, String name);

    @Query(type = QueryType.TEMPLATE, value = "select content from resource_event where name = :name and organization_id_fk = :organizationId and event_id_fk = :eventId")
    String fileContentTemplate(int organizationId, int eventId, String name);




    @Query(type = QueryType.TEMPLATE, value = "insert into resource_global (name, content_size, content, content_type, attributes) " +
        "values(?, ?, ?, ?, ?)")
    String uploadTemplate(String name);

    @Query(type = QueryType.TEMPLATE, value = "insert into resource_organizer (name, organization_id_fk, content_size, content, content_type, attributes) " +
        "values(?, ?, ?, ?, ?, ?)")
    String uploadTemplate(int organizationId, String name);

    @Query(type = QueryType.TEMPLATE, value = "insert into resource_event (name, organization_id_fk, event_id_fk, content_size, content, content_type, attributes) " +
        "values(?, ?, ?, ?, ?, ?, ?)")
    String uploadTemplate(int organizationId, int eventId, String name);

}
