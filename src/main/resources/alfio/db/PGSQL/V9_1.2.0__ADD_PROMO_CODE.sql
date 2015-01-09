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

create table promo_code (
  id serial primary key not null,
  promo_code varchar(64) not null,
  event_id_fk integer not null,
  valid_from timestamp with time zone not null,
  valid_to timestamp with time zone not null,
  discount_amount integer not null,
  discount_type varchar(16) not null
);
alter table promo_code add constraint "unique_promo_code_for_event" unique(promo_code, event_id_fk);
alter table promo_code add constraint "check_promo_code_min_length" check (char_length(promo_code) >= 7);
alter table promo_code add constraint "check_discount_type" check (discount_type = 'FIXED_AMOUNT' OR discount_type = 'PERCENTAGE');
alter table promo_code add constraint "check_discount_amount" check (
CASE
	WHEN discount_type = 'FIXED_AMOUNT' THEN discount_amount >= 0
	WHEN discount_type = 'PERCENTAGE' THEN discount_amount >= 0 and discount_amount <= 100
END);
alter table promo_code add foreign key(event_id_fk) references event(id);

CREATE INDEX "promo_code_event_id_fk_idx" ON promo_code(event_id_fk);

--
alter table tickets_reservation add column promo_code_id_fk int;
alter table tickets_reservation add foreign key(promo_code_id_fk) references promo_code(id);
CREATE INDEX "tickets_reservation_promo_code_id_fk_idx" ON tickets_reservation(promo_code_id_fk);