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

create table whitelist (
    id integer identity not null,
    name varchar(255) not null,
    description varchar(2048),
    organization_id_fk integer not null
);

alter table whitelist add constraint "whitelist_org_id_fk" foreign key(organization_id_fk) references organization(id);
alter table whitelist add constraint "whitelist_unique_name_org_id" unique(name, organization_id_fk);

create table whitelist_item (
    id integer identity not null,
    whitelist_id_fk integer not null,
    value varchar(255),
    description varchar(2048)
);

alter table whitelist_item add constraint "whitelisted_attendee_whitelist_id_fk" foreign key(whitelist_id_fk) references whitelist(id);
alter table whitelist_item add constraint "whitelisted_attendee_unique_value" unique(whitelist_id_fk, value);

create table whitelist_configuration (
    id integer identity not null,
    whitelist_id_fk integer not null,
    event_id_fk integer not null,
    ticket_category_id_fk integer,
    type varchar(255),
    match_type varchar(255),
    max_allocation integer,
    active boolean
);

alter table whitelist_configuration add constraint "whitelist_configuration_whitelist_id_fk" foreign key(whitelist_id_fk) references whitelist(id);
alter table whitelist_configuration add constraint "whitelist_configuration_event_id_fk" foreign key(event_id_fk) references event(id);
alter table whitelist_configuration add constraint "whitelist_configuration_ticket_category_id_fk" foreign key(ticket_category_id_fk) references ticket_category(id);

create table whitelisted_ticket (
    whitelist_item_id_fk integer not null,
    whitelist_configuration_id_fk integer not null,
    ticket_id_fk integer not null,
    requires_unique_value boolean
);

alter table whitelisted_ticket add constraint "whitelisted_ticket_whitelist_item_id_fk" foreign key(whitelist_item_id_fk) references whitelist_item(id);
alter table whitelisted_ticket add constraint "whitelisted_ticket_whitelist_configuration_id_fk" foreign key(whitelist_configuration_id_fk) references whitelist_configuration(id);
alter table whitelisted_ticket add constraint "whitelisted_ticket_ticket_id_fk" foreign key(ticket_id_fk) references ticket(id);
alter table whitelisted_ticket add constraint "whitelisted_ticket_unique_item_id" unique(whitelist_item_id_fk, whitelist_configuration_id_fk, requires_unique_value);