create table configuration_organization(
  id integer identity not null,
  organization_id_fk integer not null,
  c_key varchar(255) not null,
  c_value varchar(2048) not null,
  description varchar(2048)
);
alter table configuration_organization add constraint "unique_configuration_organization" unique(organization_id_fk, c_key);
alter table configuration_organization add foreign key(organization_id_fk) references organization(id);

create table configuration_event(
  id integer identity not null,
  organization_id_fk integer not null,
  event_id_fk integer not null,
  c_key varchar(255) not null,
  c_value varchar(2048) not null,
  description varchar(2048)
);
alter table configuration_event add constraint "unique_configuration_event" unique(organization_id_fk, event_id_fk, c_key);
alter table configuration_event add foreign key(organization_id_fk) references organization(id);
alter table configuration_event add foreign key(event_id_fk) references event(id);

create table configuration_ticket_category(
  id integer identity not null,
  organization_id_fk integer not null,
  event_id_fk integer not null,
  ticket_category_id_fk integer not null,
  c_key varchar(255) not null,
  c_value varchar(2048) not null,
  description varchar(2048)
);
alter table configuration_ticket_category add constraint "unique_configuration_ticket_category" unique(organization_id_fk, event_id_fk, ticket_category_id_fk, c_key);
alter table configuration_ticket_category add foreign key(organization_id_fk) references organization(id);
alter table configuration_ticket_category add foreign key(event_id_fk) references event(id);
alter table configuration_ticket_category add foreign key(ticket_category_id_fk) references ticket_category(id);