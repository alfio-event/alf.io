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
package alfio.controller.api.v2.model;

import java.util.Objects;

public class EmbeddingConfiguration {
    private final String notificationOrigin;

    public EmbeddingConfiguration(String notificationOrigin) {
        this.notificationOrigin = Objects.requireNonNullElse(notificationOrigin, "");
    }

    public boolean isEnabled() {
        return notificationOrigin != null && !notificationOrigin.isBlank();
    }

    public String getNotificationOrigin() {
        return notificationOrigin;
    }
}
