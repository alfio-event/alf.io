package io.bagarino.repository.user;

import io.bagarino.datamapper.Bind;
import io.bagarino.datamapper.Query;
import io.bagarino.datamapper.QueryRepository;
import io.bagarino.model.user.Organization;

import java.util.List;

@QueryRepository
public interface OrganizationRepository {

    @Query("SELECT * FROM organization")
    List<Organization> findAll();

    @Query("SELECT * FROM organization where id = :id")
    Organization findById(@Bind("id") int id);

    @Query("INSERT INTO organization(name, description) VALUES (:name, :description)")
    int create(@Bind("name") String name, @Bind("description") String description);
}
