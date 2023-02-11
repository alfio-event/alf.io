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

import alfio.model.ReservationsByEvent;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import ch.digitalfondue.npjt.QueryType;

import java.time.ZonedDateTime;
import java.util.List;

@QueryRepository
public interface ExportRepository {
    @Query(type = QueryType.SELECT, value = "with tickets as (" +
        "    select tr_id, json_agg(jsonb_build_object(" +
        "            'id', t_uuid," +
        "            'firstName', t_first_name," +
        "            'lastName', t_last_name," +
        "            'type', tc_name," +
        "            'status', t_status" +
        "        )) items" +
        "    from checkin_ticket_event_and_category_info" +
        "    group by 1" +
        ")," +
        "reservations as (" +
        "     select e_id," +
        "            jsonb_agg(jsonb_build_object(" +
        "                    'id', tr.tr_id," +
        "                    'invoiceNumber', tr.tr_invoice_number," +
        "                    'firstName', tr.tr_first_name," +
        "                    'lastName', tr.tr_last_name," +
        "                    'email', tr.tr_email_address," +
        "                    'paymentType', tr.tr_payment_method," +
        "                    'finalPriceCts', tr.tr_final_price_cts," +
        "                    'currency', tr.tr_currency_code," +
        "                    'confirmationTimestamp', to_char(tr.tr_confirmation_ts at time zone 'UTC', 'YYYY-MM-DD') || 'T' || to_char(tr.tr_confirmation_ts at time zone 'UTC', 'HH24:MI:SS.MSZ')," +
        "                    'tickets', t.items" +
        "                )) as agg" +
        "     from checkin_ticket_event_and_category_info tr" +
        "     join tickets t on tr.tr_id = t.tr_id" +
        "     where tr_status in ('OFFLINE_PAYMENT', 'DEFERRED_OFFLINE_PAYMENT', 'COMPLETE', 'CANCELLED')" +
        "     group by 1" +
        " )" +
        " select distinct c.e_id as event_id, c.e_short_name event_short_name, c.e_display_name event_display_name, r.agg reservations" +
        " from checkin_ticket_event_and_category_info c join reservations r on r.e_id = c.e_id" +
        " where tr_confirmation_ts between :startTs and :endTs" +
        " order by 1")
    List<ReservationsByEvent> allReservationsForInterval(@Bind("startTs") ZonedDateTime from, @Bind("endTs") ZonedDateTime to);
}
