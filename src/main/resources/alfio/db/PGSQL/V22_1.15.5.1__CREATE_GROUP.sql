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

create table a_group (
    id serial primary key not null,
    name varchar(255) not null,
    description varchar(2048),
    organization_id_fk integer not null,
    active boolean default true
);

alter table a_group add constraint "a_group_org_id_fk" foreign key(organization_id_fk) references organization(id);
alter table a_group add constraint "a_group_unique_name_org_id" unique(name, organization_id_fk);

create table group_member (
    id serial primary key not null,
    a_group_id_fk integer not null,
    value varchar(255),
    description varchar(2048),
    active boolean default true
);

alter table group_member add constraint "group_member_a_group_id_fk" foreign key(a_group_id_fk) references a_group(id);
alter table group_member add constraint "group_member_unique_value" unique(a_group_id_fk, value);

create table group_link (
    id serial primary key not null,
    a_group_id_fk integer not null,
    event_id_fk integer not null,
    ticket_category_id_fk integer,
    type varchar(255),
    match_type varchar(255),
    max_allocation integer,
    active boolean not null default true
);

alter table group_link add constraint "group_link_a_group_id_fk" foreign key(a_group_id_fk) references a_group(id);
alter table group_link add constraint "group_link_event_id_fk" foreign key(event_id_fk) references event(id);
alter table group_link add constraint "group_link_ticket_category_id_fk" foreign key(ticket_category_id_fk) references ticket_category(id);

create table whitelisted_ticket (
    group_member_id_fk integer not null,
    group_link_id_fk integer not null,
    ticket_id_fk integer not null,
    requires_unique_value boolean
);

alter table whitelisted_ticket add constraint "whitelisted_ticket_group_member_id_fk" foreign key(group_member_id_fk) references group_member(id);
alter table whitelisted_ticket add constraint "whitelisted_ticket_group_link_id_fk" foreign key(group_link_id_fk) references group_link(id);
alter table whitelisted_ticket add constraint "whitelisted_ticket_ticket_id_fk" foreign key(ticket_id_fk) references ticket(id);
alter table whitelisted_ticket add constraint "whitelisted_ticket_unique_item_id" unique(group_member_id_fk, group_link_id_fk, requires_unique_value);

create view group_link_active as select * from group_link where active = true;

create view group_member_active as select * from group_member where active = true;

create view group_active as select * from a_group where active = true;


