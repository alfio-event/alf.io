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

alter table tickets_reservation add column billing_address_company varchar(512);
alter table tickets_reservation add column billing_address_line1 varchar(512);
alter table tickets_reservation add column billing_address_line2 varchar(512);
alter table tickets_reservation add column billing_address_zip varchar(512);
alter table tickets_reservation add column billing_address_city varchar(512);
alter table tickets_reservation add column validated_for_overview boolean;
alter table tickets_reservation add column add_company_billing_details boolean;


drop view if exists reservation_and_ticket_and_tx;
drop view if exists ticket_and_reservation_and_tx;
alter table tickets_reservation alter column billing_address TYPE varchar(4096);
