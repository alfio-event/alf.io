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

drop view if exists reservation_and_ticket_and_tx;
alter table promo_code drop constraint "check_discount_type";
alter table promo_code alter column promo_code set data type varchar(255);
alter table promo_code alter column discount_type set data type varchar(255);
alter table promo_code alter column categories set data type text;
alter table promo_code add constraint "check_discount_type" check (discount_type = 'FIXED_AMOUNT' OR discount_type = 'PERCENTAGE' OR discount_type = 'FIXED_AMOUNT_RESERVATION' or discount_type = 'NONE');