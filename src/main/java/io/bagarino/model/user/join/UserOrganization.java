package io.bagarino.model.user.join;

import io.bagarino.datamapper.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class UserOrganization {

    private final int userId;
    private final int organizationId;

    public UserOrganization(@Column("user_id") int userId, @Column("org_id") int organizationId) {
        this.userId = userId;
        this.organizationId = organizationId;
    }
}
