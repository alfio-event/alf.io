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

create table v2_event (
    id bigserial not null primary key,
    short_name varchar(128) not null,
    start_time timestamp,
    end_time timestamp,
    time_zone varchar(255)
);

alter table v2_event add constraint "v2_event_unique_short_name" unique(short_name);

create table v2_resource (
    id bigserial not null primary key,
    name varchar(256),
    resource_id_fk bigint
);
alter table v2_resource add constraint "v2_resource_self_fk" foreign key(resource_id_fk) references v2_resource(id);

create table v2_event_resource (
    id bigserial not null primary key,
    event_id_fk bigint not null,
    resource_id_fk bigint not null,
    start_time timestamp,
    end_time timestamp
);
alter table v2_event_resource add constraint "v2_event_resource_event_id_fk" foreign key(event_id_fk) references v2_event(id);
alter table v2_event_resource add constraint "v2_event_resource_resource_id_fk" foreign key(resource_id_fk) references v2_resource(id);

create type slot_creation_strategy_t as enum ('EAGER', 'LAZY');
create type resource_type_t as enum ('TICKET', 'MERCH', 'DONATION');

create table v2_resource_category (
    id bigserial not null primary key,
    event_resource_id_fk bigint not null,
    start_time timestamp,
    end_time timestamp,
    max_tickets int,
    name varchar(256) not null,
    src_price_cts int,
    slot_creation_strategy slot_creation_strategy_t,
    resource_type resource_type_t
);
alter table v2_resource_category add constraint "v2_resource_category_event_resource_id_fk" foreign key(event_resource_id_fk) references v2_event_resource(id);

create table v2_slot (
    id bigserial not null primary key,
    resource_category_id_fk bigint,
    start_time timestamp,
    end_time timestamp,
    ticket_id_fk bigint
);
alter table v2_slot add constraint "v2_slot_resource_category_id_fk" foreign key(resource_category_id_fk) references v2_resource_category(id);

create table v2_resource_category_event_resource (
    event_resource_id_fk bigint not null,
    resource_category_id_fk bigint not null
);
alter table v2_resource_category_event_resource add constraint "v2_resource_category_event_resource_event_resource_id_fk" foreign key(event_resource_id_fk) references v2_event_resource(id);
alter table v2_resource_category_event_resource add constraint "v2_resource_category_event_resource_resource_category_id_fk" foreign key(resource_category_id_fk) references v2_resource_category(id);


create table v2_reservation (
    id bigserial not null primary key,
    uuid uuid,
    event_id_fk bigint
);
alter table v2_reservation add constraint "v2_reservation_event_id_fk" foreign key(event_id_fk) references v2_event(id);

create table v2_ticket (
    id bigserial not null primary key,
    uuid uuid,
    creation_time timestamp,
    resource_category_id_fk bigint,
    reservation_id_fk bigint
);
alter table v2_ticket add constraint "v2_ticket_resource_category_id_fk" foreign key(resource_category_id_fk) references v2_resource_category(id);
alter table v2_ticket add constraint "v2_ticket_reservation_id_fk" foreign key(reservation_id_fk) references v2_reservation(id);

create table v2_reservation_request (
    id bigserial not null primary key,
    event_id_fk bigint
);
alter table v2_reservation_request add constraint "v2_reservation_request_event_id_fk" foreign key(event_id_fk) references v2_event(id);


create type reservation_request_item_status_t as enum ('FULFILLED', 'FAILED', 'WAITING');

create table v2_reservation_request_item (
    id bigserial not null primary key,
    reservation_request_id_fk bigint not null,
    resource_category_id_fk bigint not null,
    status reservation_request_item_status_t not null
);
alter table v2_reservation_request_item add constraint "v2_reservation_request_item_reservation_request_id_fk" foreign key(reservation_request_id_fk) references v2_reservation_request(id);
alter table v2_reservation_request_item add constraint "v2_reservation_request_item_resource_category_id_fk" foreign key(resource_category_id_fk) references v2_resource_category(id);