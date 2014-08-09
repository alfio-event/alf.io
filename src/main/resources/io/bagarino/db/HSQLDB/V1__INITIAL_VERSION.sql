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
create table discount (id integer identity, amount decimal);
create table organization ( id integer identity, description varchar(2048));
create table user (id integer identity, username varchar(255), password varchar(255), first_name varchar(255), last_name varchar(255), address varchar(255), zip varchar(255), city varchar(255), state varchar(255), country varchar(255), email_address varchar(255));
create table ticket_category (id integer identity, inception timestamp, "end" timestamp, discount_id integer, max_tickets integer);
create table event(id integer identity, description varchar(2048), owner integer, latitude varchar(255), longitude varchar(255), "begin" timestamp, "end" timestamp);
create table payment_proxy(id integer identity, key varchar(255), address varchar(1024), name varchar(255));
create table transaction(id integer identity, payment_proxy_id integer, "timestamp" timestamp, source_ip varchar(255), user_id integer);
create table ticket (id integer identity, creation timestamp, category_id integer, event_id integer, status varchar(255), original_price decimal, paid_price decimal, transaction_id integer);
create table waiting_queue(id integer identity, event_id integer);
--join tables
create table j_user_organization (user_id integer not null, org_id integer not null);
create table j_ticket_category_organization (cat_id integer not null, org_id integer not null);
create table j_event_ticket_category(event_id integer not null, ticket_category_id integer not null);
create table j_waiting_queue_user(waiting_queue_id integer not null, user_id integer not null);