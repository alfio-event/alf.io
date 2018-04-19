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

-- new model, wip

-- for the range constraint
create extension btree_gist;

create table v2_event (
    id bigint not null primary key,
    short_name varchar(128) not null,
    start_time timestamp,
    end_time timestamp
);


create table v2_event_resource (
    id bigint not null primary key,
    event_id_fk bigint not null,
    resource_id_fk bigint not null,
    start_time timestamp,
    end_time timestamp
);

create type slot_creation_strategy_t as enum ('EAGER', 'LAZY');

create table v2_ticket_category (
    id bigint not null primary key,
    event_resource_id_fk bigint not null,
    start_time timestamp,
    end_time timestamp,
    max_tickets int,
    name varchar(128) not null,
    src_price_cts int,
    slot_creation_strategy slot_creation_strategy_t
);

create table v2_slot (
    id bigint not null primary key,
    event_resource_id_fk bigint,
    exclusive_use boolean,
    start_time timestamp,
    end_time timestamp,
    ticket_id_fk bigint
);

-- https://stackoverflow.com/questions/26735955/postgres-constraint-for-unique-datetime-range
-- TODO: check that setting exclusive use to null OR start_time/end_time to null will deactivate this constraint :D
alter table v2_slot add constraint unique_overlapping_time exclude using gist (
    event_resource_id_fk WITH =,
    (exclusive_use::int) WITH =, -- <- boolean is not supported
    tsrange(start_time, end_time) WITH &&
);

create table v2_ticket (
    id bigint not null primary key,
    uuid uuid,
    creation_time timestamp,
    category_id_fk bigint
);

create table v2_reservation (
    id bigint not null primary key,
    uuid uuid
);