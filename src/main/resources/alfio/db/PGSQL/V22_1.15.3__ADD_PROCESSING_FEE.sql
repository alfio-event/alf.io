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

create table processing_fee (
  id serial primary key not null,
  event_id_fk integer,
  organization_id_fk integer not null,
  valid_from timestamp with time zone not null,
  valid_to timestamp with time zone not null,
  amount integer not null,
  fee_type varchar(16) not null,
  categories varchar(2048)
);
alter table processing_fee add constraint "check_fee_type" check (fee_type = 'FIXED_AMOUNT' OR fee_type = 'PERCENTAGE');
alter table processing_fee add constraint "check_discount_amount" check (
CASE
	WHEN fee_type = 'FIXED_AMOUNT' THEN amount >= 0
	WHEN fee_type = 'PERCENTAGE' THEN amount >= 0 and amount <= 100
END);
alter table processing_fee add foreign key(event_id_fk) references event(id);
alter table processing_fee add foreign key(organization_id_fk) references organization(id);

CREATE INDEX "processing_fee_event_id_fk_idx" ON processing_fee(event_id_fk);

create table processing_fee_link (
    fee_id_fk integer not null,
    payment_method varchar(255) not null
);

alter table processing_fee_link add foreign key(fee_id_fk) references processing_fee(id);
alter table processing_fee_link add constraint "unique_fee_link" unique(fee_id_fk, payment_method);