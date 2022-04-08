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
import lombok.Getter;

@Getter
public class TicketReservationInvoicingAdditionalInfo {

    private final ItalianEInvoicing italianEInvoicing;

    @JsonCreator
    public TicketReservationInvoicingAdditionalInfo(@JsonProperty("italianEInvoicing") ItalianEInvoicing italianEInvoicing) {
        this.italianEInvoicing = italianEInvoicing;
    }

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

        @JsonCreator
        public ItalianEInvoicing(@JsonProperty("fiscalCode") String fiscalCode,
                                 @JsonProperty("referenceType") ReferenceType referenceType,
                                 @JsonProperty("addresseeCode") String addresseeCode,
                                 @JsonProperty("pec") String pec,
                                 @JsonProperty("splitPayment") boolean splitPayment) {
            this.fiscalCode = fiscalCode;
            this.referenceType = referenceType;
            this.addresseeCode = addresseeCode;
            this.pec = pec;
            this.splitPayment = splitPayment;
        }

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
