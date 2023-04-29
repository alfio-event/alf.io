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

public class ReservationConfiguration {
    private final boolean hideContactData;
    private final boolean hideConfirmationButtons;
    private final boolean lockEmailEdit;

    @JsonCreator
    public ReservationConfiguration(@JsonProperty("hideContactData") Boolean hideContactData,
                                    @JsonProperty("hideConfirmationButtons") Boolean hideConfirmationButtons,
                                    @JsonProperty("lockEmailEdit") Boolean lockEmailEdit) {
        this.hideContactData = Boolean.TRUE.equals(hideContactData);
        this.hideConfirmationButtons = Boolean.TRUE.equals(hideConfirmationButtons);
        this.lockEmailEdit = Boolean.TRUE.equals(lockEmailEdit);
    }

    public boolean isHideContactData() {
        return hideContactData;
    }

    public boolean isHideConfirmationButtons() {
        return hideConfirmationButtons;
    }

    public boolean isLockEmailEdit() {
        return lockEmailEdit;
    }
}
