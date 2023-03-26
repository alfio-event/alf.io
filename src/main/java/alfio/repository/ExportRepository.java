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
        "    select tr_id, jsonb_agg(jsonb_build_object(" +
        "            'id', t_uuid," +
        "            'firstName', t_first_name," +
        "            'lastName', t_last_name," +
        "            'type', tc_name," +
        "            'status', t_status," +
        "            'srcPriceCts', t_src_price_cts," +
        "            'taxCts', t_vat_cts," +
        "            'taxStatus', t_vat_status," +
        "            'finalPriceCts', t_final_price_cts" +
        "        )) items" +
        "    from checkin_ticket_event_and_category_info" +
        "    group by 1)," +
        "     reservations as (" +
        "         select distinct event_id_fk, id, invoice_number, first_name," +
        "                         billing_address_company, vat_nr, vat_country," +
        "                         last_name, email_address, payment_method, currency_code, invoicing_additional_information#>>'{italianEInvoicing,fiscalCode}' as tax_code," +
        "                         confirmation_ts, final_price_cts, src_price_cts, vat_cts, vat_status from tickets_reservation" +
        "         where status in ('OFFLINE_PAYMENT', 'DEFERRED_OFFLINE_PAYMENT', 'COMPLETE', 'CANCELLED')" +
        "         and confirmation_ts between :startTs and :endTs" +
        "         and event_id_fk is not null" +
        "     )," +
        "     reservations_event as (" +
        "         select tr.event_id_fk e_id, jsonb_agg(jsonb_build_object(" +
        "                 'id', tr.id," +
        "                 'invoiceNumber', tr.invoice_number," +
        "                 'firstName', tr.first_name," +
        "                 'lastName', tr.last_name," +
        "                 'email', tr.email_address," +
        "                 'paymentType', tr.payment_method," +
        "                 'finalPriceCts', tr.final_price_cts," +
        "                 'currency', tr.currency_code," +
        "                 'taxId', tr.vat_nr," +
        "                 'taxCountry', tr.vat_country," +
        "                 'companyName', tr.billing_address_company," +
        "                 'srcPriceCts', tr.src_price_cts," +
        "                 'taxCts', tr.vat_cts," +
        "                 'taxStatus', tr.vat_status," +
        "                 'taxCode', tr.tax_code," +
        "                 'confirmationTimestamp', to_char(tr.confirmation_ts at time zone 'UTC', 'YYYY-MM-DD') || 'T' || to_char(tr.confirmation_ts at time zone 'UTC', 'HH24:MI:SS.MSZ')," +
        "                 'tickets', t.items" +
        "             )) as reservations" +
        "         from reservations tr" +
        "                  join tickets t on t.tr_id = tr.id" +
        "         group by 1" +
        "     )" +
        " select e.id as event_id, e.short_name as event_short_name, e.display_name as event_display_name, tr.reservations" +
        " from event e" +
        "   join reservations_event tr on e.id = tr.e_id" +
        " where e.org_id in (:orgIds)" +
        " order by 1")
    List<ReservationsByEvent> allReservationsForInterval(@Bind("startTs") ZonedDateTime from,
                                                         @Bind("endTs") ZonedDateTime to,
                                                         @Bind("orgIds") List<Integer> orgIds);
}
