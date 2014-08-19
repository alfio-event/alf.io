package io.bagarino.repository.user.join;

import io.bagarino.datamapper.Bind;
import io.bagarino.datamapper.Query;
import io.bagarino.datamapper.QueryRepository;
import io.bagarino.model.user.join.UserOrganization;

import java.util.List;

@QueryRepository
public interface UserOrganizationRepository {

    @Query("select * from j_user_organization where user_id = :userId")
    List<UserOrganization> findByUserId(@Bind("userId") int userId);

    @Query("select * from j_user_organization where org_id = :organizationId")
    List<UserOrganization> findByOrganizationId(@Bind("organizationId") int organizationId);

    @Query("insert into j_user_organization (user_id, org_id) values(:userId, :organizationId)")
    int create(@Bind("userId") int userId, @Bind("organizationId") int organizationId);

}
