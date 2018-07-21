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

create table attendee_list (
    id integer identity not null,
    name varchar(255) not null,
    description varchar(2048),
    organization_id_fk integer not null
);

alter table attendee_list add constraint "attendee_list_org_id_fk" foreign key(organization_id_fk) references organization(id);
alter table attendee_list add constraint "attendee_list_unique_name_org_id" unique(name, organization_id_fk);

create table attendee_list_item (
    id integer identity not null,
    attendee_list_id_fk integer not null,
    value varchar(255),
    description varchar(2048)
);

alter table attendee_list_item add constraint "attendee_list_item_attendee_list_id_fk" foreign key(attendee_list_id_fk) references attendee_list(id);
alter table attendee_list_item add constraint "attendee_list_item_unique_value" unique(attendee_list_id_fk, value);

create table attendee_list_configuration (
    id integer identity not null,
    attendee_list_id_fk integer not null,
    event_id_fk integer not null,
    ticket_category_id_fk integer,
    type varchar(255),
    match_type varchar(255),
    max_allocation integer,
    active boolean default true
);

alter table attendee_list_configuration add constraint "attendee_list_configuration_attendee_list_id_fk" foreign key(attendee_list_id_fk) references attendee_list(id);
alter table attendee_list_configuration add constraint "attendee_list_configuration_event_id_fk" foreign key(event_id_fk) references event(id);
alter table attendee_list_configuration add constraint "attendee_list_configuration_ticket_category_id_fk" foreign key(ticket_category_id_fk) references ticket_category(id);

create table whitelisted_ticket (
    attendee_list_item_id_fk integer not null,
    attendee_list_configuration_id_fk integer not null,
    ticket_id_fk integer not null,
    requires_unique_value boolean
);

alter table whitelisted_ticket add constraint "whitelisted_ticket_attendee_list_item_id_fk" foreign key(attendee_list_item_id_fk) references attendee_list_item(id);
alter table whitelisted_ticket add constraint "whitelisted_ticket_attendee_list_configuration_id_fk" foreign key(attendee_list_configuration_id_fk) references attendee_list_configuration(id);
alter table whitelisted_ticket add constraint "whitelisted_ticket_ticket_id_fk" foreign key(ticket_id_fk) references ticket(id);
alter table whitelisted_ticket add constraint "whitelisted_ticket_unique_item_id" unique(attendee_list_item_id_fk, attendee_list_configuration_id_fk, requires_unique_value);

create view attendee_list_configuration_active as select * from attendee_list_configuration where active = true;