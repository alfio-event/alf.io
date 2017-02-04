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

import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

@QueryRepository
public interface InvoiceSequencesRepository {

    @Query("select invoice_sequence from invoice_sequences where organization_id_fk = :orgId for update")
    int lockReservationForUpdate(@Bind("orgId") int orgId);

    @Query("update invoice_sequences set invoice_sequence = invoice_sequence + 1 where organization_id_fk = :orgId")
    int incrementSequenceFor(@Bind("orgId") int orgId);

    @Query("insert into invoice_sequences(organization_id_fk, invoice_sequence) values (:orgId, 1)")
    int initFor(@Bind("orgId") int orgId);
}
