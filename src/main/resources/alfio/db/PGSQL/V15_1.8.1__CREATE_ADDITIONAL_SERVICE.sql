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

create table additional_service (
    id serial PRIMARY KEY NOT NULL,
    event_id_fk integer not null,
    price_cts integer,
    fix_price boolean not null,
    ordinal integer not null DEFAULT 0,
    available_qty integer not null DEFAULT -1,
    max_qty_per_order integer not null DEFAULT -1,
    inception_ts timestamp with time zone,
    expiration_ts timestamp with time zone,
    vat DECIMAL(5,2),
    vat_type varchar(50) not null
);

alter table additional_service add FOREIGN KEY (event_id_fk) REFERENCES event(id);

create table additional_service_description (
    additional_service_id_fk integer not null,
    locale varchar(8) not null,
    type varchar(16) not null,
    value varchar(2048) not null
);

alter table additional_service_description add PRIMARY KEY (additional_service_id_fk, locale, type);
alter table additional_service_description add FOREIGN KEY (additional_service_id_fk) REFERENCES additional_service(id);

create table additional_service_item (
    id serial PRIMARY KEY NOT NULL,
    additional_service_id_fk integer NOT NULL,
    original_price_cts integer,
    paid_price_cts integer
);
alter table additional_service_item add FOREIGN KEY (additional_service_id_fk) REFERENCES additional_service(id);

alter table ticket_field_configuration add COLUMN context varchar(50) not NULL DEFAULT 'ATTENDEE';
