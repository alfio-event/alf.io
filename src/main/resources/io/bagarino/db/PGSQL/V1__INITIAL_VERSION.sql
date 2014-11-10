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

-- here be tables for pgsql

--entities
create table special_price (
  id serial primary key not null,
  code varchar(64) not null,
  price_cts integer not null,
  ticket_category_id integer not null,
  status varchar(255) not null
);
alter table special_price add constraint "unique_code" unique(code);

create table organization ( 
	id serial primary key not null, 
	name varchar(255) not null,
	description varchar(2048) not null,
	email varchar(2048) not null
);

create table ba_user (
	id serial primary key not null, 
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


create table event(
	id serial primary key not null,
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

create table ticket_category (
	id serial primary key not null, 
	inception timestamp with time zone not null, 
	expiration timestamp with time zone not null,
	max_tickets integer not null,
	name varchar(255) not null,
    description varchar(1024),
	price_cts integer not null,
  	access_restricted boolean not null,
  	tc_status varchar(255),
  	event_id integer not null
);
alter table ticket_category add foreign key(event_id) references event(id);


create table tickets_reservation(
	id character(36) primary key not null, --uuid type could be used...
	validity timestamp with time zone not null,
	status varchar(255) not null,
	full_name varchar(255),
	email_address varchar(255),
	billing_address varchar(450)
);
-- constraints

create table ticket (
	id serial primary key not null,
  	uuid character(36) not null,
	creation timestamp with time zone not null, 
	category_id integer not null, 
	event_id integer not null, 
	status varchar(255) not null, 
	original_price_cts integer not null,
	paid_price_cts integer not null,
	tickets_reservation_id character(36),
	full_name varchar(255),
	email_address varchar(255),
	special_price_id_fk integer
);
-- constraints
alter table ticket add constraint "unique_ticket_uuid" unique(uuid);
alter table ticket add foreign key(category_id) references ticket_category(id);
alter table ticket add foreign key(event_id) references event(id);
alter table ticket add foreign key(tickets_reservation_id) references tickets_reservation(id);
alter table ticket add foreign key(special_price_id_fk) references special_price(id);
alter table ticket add constraint "unique_special_price" unique(special_price_id_fk);

--join tables
create table j_user_organization (
	user_id integer not null, 
	org_id integer not null
);
-- constraints
alter table j_user_organization add foreign key(user_id) references ba_user(id);
alter table j_user_organization add foreign key(org_id) references organization(id);


create table configuration(
  id serial primary key not null,
  c_key varchar(255) not null,
  c_value varchar(2048) not null,
  description varchar(2048)
);
alter table configuration add constraint "unique_configuration_c_key" unique(c_key);

create table b_transaction (
  id serial primary key not null,
  gtw_tx_id varchar(2048) not null,
  reservation_id character(36) not null,
  t_timestamp timestamp with time zone not null,
  price_cts integer not null,
  currency varchar(255) not null,
  description varchar(2048) not null,
  payment_proxy varchar(2048) not null
);
alter table b_transaction add foreign key(reservation_id) references tickets_reservation(id);

insert into configuration (c_key, c_value, description) values
	('MAX_AMOUNT_OF_TICKETS_BY_RESERVATION', '5', 'Max amount of tickets'),
	('SPECIAL_PRICE_CODE_LENGTH', '6', 'Length of special price code');