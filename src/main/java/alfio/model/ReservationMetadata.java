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
package alfio.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReservationMetadata {

    private final boolean hideContactData;
    private final boolean readyForConfirmation;
    private final boolean finalized;

    @JsonCreator
    public ReservationMetadata(@JsonProperty("hideContactData") Boolean hideContactData,
                               @JsonProperty("readyForConfirmation") Boolean readyForConfirmation,
                               @JsonProperty("finalized") Boolean finalized) {
        this.hideContactData = Boolean.TRUE.equals(hideContactData);
        this.readyForConfirmation = Boolean.TRUE.equals(readyForConfirmation);
        this.finalized = Boolean.TRUE.equals(finalized);
    }

    public boolean isHideContactData() {
        return hideContactData;
    }

    public boolean isFinalized() {
        return finalized;
    }

    public boolean isReadyForConfirmation() {
        return readyForConfirmation;
    }

    public ReservationMetadata withFinalized(boolean newValue) {
        return new ReservationMetadata(hideContactData, readyForConfirmation, newValue);
    }

    public ReservationMetadata withReadyForConfirmation(boolean newValue) {
        return new ReservationMetadata(hideContactData, newValue, finalized);
    }
}
