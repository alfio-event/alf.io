--
-- This file is part of bagarino.
--
-- bagarino is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- bagarino is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License
-- along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
--

--entities
create table special_price (
  id integer identity not null,
  code varchar(2048) not null,
  price_cts integer not null,
  ticket_category_id integer not null,
  status varchar(255) not null
);

create table organization ( 
	id integer identity not null, 
	name varchar(255) not null,
	description varchar(2048) not null,
	email varchar(2048) not null
);

create table customer (
  id integer identity not null,
  username varchar(255) not null,
  password varchar(255) not null,
  first_name varchar(255) not null,
  last_name varchar(255) not null,
  address varchar(255) not null,
  zip varchar(255) not null,
  city varchar(255) not null,
  state varchar(255) not null,
  country varchar(255) not null,
  email_address varchar(255) not null
);

create table ba_user (
	id integer identity not null, 
	username varchar(255) not null, 
	password varchar(2048) not null,
	first_name varchar(255) not null, 
	last_name varchar(255) not null, 
	email_address varchar(255) not null,
    enabled boolean default true
);

create table authority(
  username varchar(255) not null,
  role varchar(255) not null
);

create table ticket_category (
	id integer identity not null, 
	inception timestamp with time zone not null, 
	expiration timestamp with time zone not null,
	max_tickets integer not null,
	name varchar(255) not null,
  description varchar(1024),
  price_cts integer not null,
  access_restricted boolean not null
);

create table event(
	id integer identity not null,
  short_name varchar(2048) not null,
	description varchar(2048) not null, 
  location varchar(2048) not null,
	latitude varchar(255) not null, 
	longitude varchar(255) not null, 
	start_ts timestamp with time zone not null,
	end_ts timestamp with time zone not null,
  time_zone varchar(255) not null,
  regular_price_cts integer not null,
  currency varchar(3),
  available_seats integer not null,
  vat_included boolean not null,
  vat decimal(5,2) not null,
  allowed_payment_proxies varchar(2048) not null,
  private_key varchar(2048) not null,
  org_id integer not null
);

alter table event add constraint "unique_event_name" unique(short_name);
alter table event add constraint "unique_private_key" unique(private_key);
alter table event add foreign key(org_id) references organization(id);

create table payment_proxy(
	id integer identity not null, 
	key varchar(255) not null, 
	address varchar(1024) not null, 
	name varchar(255) not null
);

create table tickets_reservation(
	id varchar(255) primary key not null,
	validity timestamp with time zone not null,
	status varchar(255) not null,
	full_name varchar(255),
	email_address varchar(255),
	billing_address varchar(2048)
);
-- constraints

create table ticket (
	id integer identity not null,
  uuid varchar(255) not null,
	creation timestamp with time zone not null, 
	category_id integer not null, 
	event_id integer not null, 
	status varchar(255) not null, 
	original_price_cts integer not null,
	paid_price_cts integer not null,
	tickets_reservation_id varchar(255),
	full_name varchar(255),
	email_address varchar(255)
	
);
-- constraints
alter table ticket add constraint "unique_ticket_uuid" unique(uuid);
alter table ticket add foreign key(category_id) references ticket_category(id);
alter table ticket add foreign key(event_id) references event(id);
alter table ticket add foreign key(tickets_reservation_id) references tickets_reservation(id);

create table waiting_queue(
	id integer identity not null, 
	event_id integer not null
);
-- constraints
alter table waiting_queue add foreign key(event_id) references event(id);

--join tables
create table j_user_organization (
	user_id integer not null, 
	org_id integer not null
);
-- constraints
alter table j_user_organization add foreign key(user_id) references ba_user(id);
alter table j_user_organization add foreign key(org_id) references organization(id);


create table j_ticket_category_organization (
	cat_id integer not null, 
	org_id integer not null
);
-- constraints
alter table j_ticket_category_organization add foreign key(cat_id) references ticket_category(id);
alter table j_ticket_category_organization add foreign key(org_id) references organization(id);

create table j_event_ticket_category(
	event_id integer not null, 
	ticket_category_id integer not null
);
-- constraints
alter table j_event_ticket_category add foreign key(event_id) references event(id);
alter table j_event_ticket_category add foreign key(ticket_category_id) references ticket_category(id);

create table j_waiting_queue_customer(
	waiting_queue_id integer not null, 
	customer_id integer not null
);
-- constraints
alter table j_waiting_queue_customer add foreign key(waiting_queue_id) references waiting_queue(id);
alter table j_waiting_queue_customer add foreign key(customer_id) references customer(id);

create table configuration(
  id integer identity not null,
  c_key varchar(255) not null,
  c_value varchar(2048) not null,
  description varchar(2048)
);
alter table configuration add constraint "unique_configuration_c_key" unique(c_key);


insert into configuration (c_key, c_value, description) values
	('MAX_AMOUNT_OF_TICKETS_BY_RESERVATION', '5', 'max amount of tickets'),
	('SPECIAL_PRICE_CODE_LENGTH', '6', 'length of special price code');
