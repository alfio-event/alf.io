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
package alfio.repository;

import alfio.model.BillingDocument;
import alfio.model.support.EnumTypeAsString;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

@QueryRepository
public interface InvoiceSequencesRepository {

    @Query("select invoice_sequence from invoice_sequences where organization_id_fk = :orgId and document_type = :documentType::BILLING_DOCUMENT_TYPE for update")
    int lockSequenceForUpdate(@Bind("orgId") int orgId, @Bind("documentType") @EnumTypeAsString BillingDocument.Type billingDocumentType);

    default int lockSequenceForUpdate(@Bind("orgId") int orgId) {
        return lockSequenceForUpdate(orgId, BillingDocument.Type.INVOICE);
    }

    @Query("update invoice_sequences set invoice_sequence = invoice_sequence + 1 where organization_id_fk = :orgId and document_type = :documentType::BILLING_DOCUMENT_TYPE")
    int incrementSequenceFor(@Bind("orgId") int orgId, @Bind("documentType") @EnumTypeAsString BillingDocument.Type billingDocumentType);


    default int incrementSequenceFor(@Bind("orgId") int orgId) {
        return incrementSequenceFor(orgId, BillingDocument.Type.INVOICE);
    }



    @Query("insert into invoice_sequences(organization_id_fk, invoice_sequence, document_type) values" +
        " (:orgId, 1, 'INVOICE')," +
        " (:orgId, 1, 'CREDIT_NOTE')")
    int initFor(@Bind("orgId") int orgId);
}
