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

    @Query("select invoice_sequences.sequence from invoice_sequences where event_id_fk = :eventId for update")
    int lockReservationForUpdate(@Bind("eventId") int eventId);

    @Query("update invoice_sequences set invoice_sequences.sequence = invoice_sequences.sequence + 1 where event_id_fk = :eventId")
    int incrementSequenceFor(@Bind("eventId") int eventId);

    @Query("insert into invoice_sequences(event_id_fk, sequence) values (:eventId, 0)")
    int initFor(@Bind("eventId") int eventId);
}
