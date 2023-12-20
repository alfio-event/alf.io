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
package alfio.model.api.v1.admin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public class CreateApiKeyRequest {
    public static final String DEFAULT_DESCRIPTION = "Auto-generated API Key";
    private final String apiKeyType;
    private final String description;

    @JsonCreator
    public CreateApiKeyRequest(@JsonProperty("type") String apiKeyType,
                               @JsonProperty("description") String description) {
        this.apiKeyType = apiKeyType;
        this.description = description;
    }

    public String apiKeyType() {
        return apiKeyType;
    }

    public String description() {
        return requireNonNullElse(trimToNull(description), DEFAULT_DESCRIPTION);
    }
}
