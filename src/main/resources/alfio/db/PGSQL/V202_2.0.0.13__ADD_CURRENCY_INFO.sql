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

alter table ticket add column currency_code varchar(10);
update ticket set currency_code = (select currency from event where id = event_id) where ticket.currency_code is null;

alter table additional_service_item add column currency_code varchar(10);
update additional_service_item set currency_code = (select currency from event where id = event_id_fk) where additional_service_item.currency_code is null;

