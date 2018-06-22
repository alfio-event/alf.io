--
-- This file is part of alf.io.
--
-- alf.io is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- alf.io is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License
-- along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
--

CREATE VIEW ticket_and_reservation_and_tx AS
 SELECT ticket.id AS t_id,
    ticket.uuid AS t_uuid,
    ticket.creation AS t_creation,
    ticket.category_id AS t_category_id,
    ticket.status AS t_status,
    ticket.event_id AS t_event_id,
    ticket.tickets_reservation_id AS t_tickets_reservation_id,
    ticket.full_name AS t_full_name,
    ticket.first_name AS t_first_name,
    ticket.last_name AS t_last_name,
    ticket.email_address AS t_email_address,
    ticket.locked_assignment AS t_locked_assignment,
    ticket.user_language AS t_user_language,
    ticket.src_price_cts AS t_src_price_cts,
    ticket.final_price_cts AS t_final_price_cts,
    ticket.vat_cts AS t_vat_cts,
    ticket.discount_cts AS t_discount_cts,
    ticket.ext_reference AS t_ext_reference,
    tickets_reservation.id AS tr_id,
    tickets_reservation.validity AS tr_validity,
    tickets_reservation.status AS tr_status,
    tickets_reservation.full_name AS tr_full_name,
    tickets_reservation.first_name AS tr_first_name,
    tickets_reservation.last_name AS tr_last_name,
    tickets_reservation.email_address AS tr_email_address,
    tickets_reservation.billing_address AS tr_billing_address,
    tickets_reservation.confirmation_ts AS tr_confirmation_ts,
    tickets_reservation.latest_reminder_ts AS tr_latest_reminder_ts,
    tickets_reservation.payment_method AS tr_payment_method,
    tickets_reservation.offline_payment_reminder_sent AS tr_offline_payment_reminder_sent,
    tickets_reservation.promo_code_id_fk AS tr_promo_code_id_fk,
    tickets_reservation.automatic AS tr_automatic,
    tickets_reservation.user_language AS tr_user_language,
    tickets_reservation.direct_assignment AS tr_direct_assignment,
    tickets_reservation.invoice_number AS tr_invoice_number,
    tickets_reservation.invoice_model AS tr_invoice_model,
    tickets_reservation.vat_status AS tr_vat_status,
    tickets_reservation.vat_nr AS tr_vat_nr,
    tickets_reservation.vat_country AS tr_vat_country,
    tickets_reservation.invoice_requested AS tr_invoice_requested,
    tickets_reservation.used_vat_percent AS tr_used_vat_percent,
    tickets_reservation.vat_included AS tr_vat_included,
    b_transaction.id AS bt_id,
    b_transaction.gtw_tx_id AS bt_gtw_tx_id,
    b_transaction.gtw_payment_id AS bt_gtw_payment_id,
    b_transaction.reservation_id AS bt_reservation_id,
    b_transaction.t_timestamp AS bt_t_timestamp,
    b_transaction.price_cts AS bt_price_cts,
    b_transaction.currency AS bt_currency,
    b_transaction.description AS bt_description,
    b_transaction.payment_proxy AS bt_payment_proxy,
    b_transaction.gtw_fee AS bt_gtw_fee,
    b_transaction.plat_fee AS bt_plat_fee
   FROM ticket
     JOIN tickets_reservation ON ticket.tickets_reservation_id = tickets_reservation.id
     LEFT JOIN b_transaction ON ticket.tickets_reservation_id = b_transaction.reservation_id;