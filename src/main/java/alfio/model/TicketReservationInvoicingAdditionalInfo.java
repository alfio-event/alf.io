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

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TicketReservationInvoicingAdditionalInfo {

    private final ItalianEInvoicing italianEInvoicing;

    public boolean isEmpty() {
        return italianEInvoicing == null || italianEInvoicing.isEmpty();
    }

    public boolean getEmpty() {
        return isEmpty();
    }

    //
    // https://github.com/alfio-event/alf.io/issues/573
    //
    @Getter
    @AllArgsConstructor
    public static class ItalianEInvoicing {

        public enum ReferenceType {
            ADDRESSEE_CODE, /* Codice destinatario */
            PEC, /* (pec = email) */
            NONE
        }

        private final String fiscalCode;
        private final ReferenceType referenceType;
        private final String addresseeCode;
        private final String pec;
        private final boolean splitPayment;

        public String getReferenceTypeAsString() {
            return referenceType == null ? "" : referenceType.toString();
        }

        public String getReference() {
            if(referenceType == ReferenceType.ADDRESSEE_CODE) {
                return addresseeCode;
            } else if(referenceType == ReferenceType.PEC) {
                return pec;
            }
            return null;
        }

        public boolean isEmpty() {
            return fiscalCode == null
                && referenceType == null
                && addresseeCode == null
                && pec == null;
        }
    }
}
