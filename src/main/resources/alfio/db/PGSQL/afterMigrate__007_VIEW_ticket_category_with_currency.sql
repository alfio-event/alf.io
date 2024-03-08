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

drop view if exists ticket_category_with_currency;
create view ticket_category_with_currency as (
    select tc.id id,
        tc.inception inception,
        tc.expiration expiration,
        tc.max_tickets max_tickets,
        tc.name "name",
        tc.price_cts price_cts,
        tc.access_restricted access_restricted,
        tc.tc_status tc_status,
        tc.event_id  event_id,
        tc.bounded bounded,
        tc.src_price_cts src_price_cts,
        tc.category_code category_code,
        tc.valid_checkin_from valid_checkin_from,
        tc.valid_checkin_to valid_checkin_to,
        tc.ticket_validity_start ticket_validity_start,
        tc.ticket_validity_end ticket_validity_end,
        tc.organization_id_fk organization_id_fk,
        tc.ordinal ordinal,
        tc.ticket_checkin_strategy ticket_checkin_strategy,
        tc.ticket_access_type ticket_access_type,
        e.currency currency_code
    from ticket_category tc, event e
    where tc.event_id = e.id
)