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
create table discount (
	id integer identity not null, 
	amount decimal not null
);

create table organization ( 
	id integer identity not null, 
	description varchar(2048) not null
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

create table user (
	id integer identity not null, 
	username varchar(255) not null, 
	password varchar(255) not null, 
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
	inception timestamp not null, 
	"end" timestamp not null, 
	discount_id integer, 
	max_tickets integer not null
);
-- constraints
alter table ticket_category add foreign key(discount_id) references discount(id); 

create table event(
	id integer identity not null, 
	description varchar(2048) not null, 
	owner integer not null, 
	latitude varchar(255) not null, 
	longitude varchar(255) not null, 
	"begin" timestamp not null, 
	"end" timestamp not null
);

create table payment_proxy(
	id integer identity not null, 
	key varchar(255) not null, 
	address varchar(1024) not null, 
	name varchar(255) not null
);

create table transaction(
	id integer identity not null, 
	payment_proxy_id integer not null, 
	"timestamp" timestamp not null, 
	source_ip varchar(255) not null, 
	customer_id integer not null
);
-- constraints
alter table transaction add foreign key(customer_id) references customer(id);

create table ticket (
	id integer identity not null, 
	creation timestamp not null, 
	category_id integer not null, 
	event_id integer not null, 
	status varchar(255) not null, 
	original_price decimal not null, 
	paid_price decimal not null, 
	transaction_id integer not null
);
-- constraints
alter table ticket add foreign key(category_id) references ticket_category(id);
alter table ticket add foreign key(event_id) references event(id);
alter table ticket add foreign key(transaction_id) references transaction(id);

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
alter table j_user_organization add foreign key(user_id) references user(id);
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