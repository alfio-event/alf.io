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
package alfio.model.user;

import alfio.util.RequestUtils;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

@Getter
@JsonSerialize(using = Organization.OrganizationSerializer.class)
public class Organization {
    private final int id;
    private final String name;
    private final String description;
    private final String email;
    private final String externalId;
    private final String slug;

    public Organization(@Column("id") int id,
                        @Column("name") String name,
                        @Column("description") String description,
                        @Column("email") String email,
                        @Column("name_openid") String externalId,
                        @Column("slug") String slug) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.email = email;
        this.externalId = externalId;
        this.slug = slug;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (! (o instanceof Organization)) {
            return false;
        }

        Organization that = (Organization) o;

        return new EqualsBuilder()
            .append(id, that.id)
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(id)
            .toHashCode();
    }

    /**
     * @deprecated use {@link #getExternalId()}
     * @return the external ID, if present
     */
    @Deprecated
    public String getNameOpenId() {
        return externalId;
    }

    @Getter
    public static class OrganizationContact {
        private final String name;
        private final String email;

        public OrganizationContact(@Column("name") String name, @Column("email") String email) {
            this.name = name;
            this.email = email;
        }
    }

    public static class OrganizationSerializer extends JsonSerializer<Organization> {
        @Override
        public void serialize(Organization value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value != null) {
                gen.writeStartObject();
                gen.writeNumberField("id", value.getId());
                gen.writeStringField("name", value.getName());
                gen.writeStringField("email", value.getEmail());
                gen.writeStringField("description", value.getDescription());
                if(RequestUtils.isAdmin(SecurityContextHolder.getContext().getAuthentication())) {
                    gen.writeStringField("externalId", value.getExternalId());
                    gen.writeStringField("slug", value.getSlug());
                } else {
                    gen.writeNullField("externalId");
                    gen.writeNullField("slug");
                }
                gen.writeEndObject();
            } else {
                serializers.defaultSerializeNull(gen);
            }
        }
    }
}
