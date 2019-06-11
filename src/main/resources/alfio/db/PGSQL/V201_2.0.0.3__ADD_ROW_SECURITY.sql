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

-- this function return true if the paramenter $1 (which is the organization id)
-- is present in the alfio.currentUserOrgs array OR simply
-- alfio.checkRowAccess is not set
create or replace function alfio_check_row_access(integer)
returns boolean
as
$$
    select (not coalesce(current_setting('alfio.checkRowAccess', true), 'false') = 'true') or

    (current_setting('alfio.checkRowAccess', true) = 'true' and ($1)::text = any(string_to_array(coalesce(current_setting('alfio.currentUserOrgs', true), ''),',')))
$$ language sql;


-- enable row level security and create policy
alter table organization enable row level security;
alter table organization force row level security;
create policy organization_access_policy on organization to public
    using (alfio_check_row_access(id))
    with check (alfio_check_row_access(id));


alter table j_user_organization enable row level security;
alter table j_user_organization force row level security;
create policy j_user_organization_access_policy on j_user_organization to public
    using (alfio_check_row_access(org_id))
    with check (alfio_check_row_access(org_id));

--

alter table event enable row level security;
alter table event force row level security;
create policy event_access_policy on event to public
    using (alfio_check_row_access(org_id))
    with check (alfio_check_row_access(org_id));


-- resources tables
alter table resource_organizer enable row level security;
alter table resource_organizer force row level security;
create policy resource_organizer_access_policy on resource_organizer to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));


alter table resource_event enable row level security;
alter table resource_event force row level security;
create policy resource_event_access_policy on resource_event to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));
--

-- configuration tables
alter table configuration_organization enable row level security;
alter table configuration_organization force row level security;
create policy configuration_organization_access_policy on configuration_organization to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));


alter table configuration_event enable row level security;
alter table configuration_event force row level security;
create policy configuration_event_access_policy on configuration_event to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));


alter table configuration_ticket_category enable row level security;
alter table configuration_ticket_category force row level security;
create policy configuration_ticket_category_access_policy on configuration_ticket_category to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));
--

--
alter table invoice_sequences enable row level security;
alter table invoice_sequences force row level security;
create policy invoice_sequences_access_policy on invoice_sequences to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));
--
alter table a_group enable row level security;
alter table a_group force row level security;
create policy a_group_access_policy on a_group to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));
--

alter table promo_code drop constraint "unique_promo_code_for_org";
alter table promo_code drop constraint "unique_promo_code_for_event";


create unique index "unique_promo_code_for_org" on promo_code(promo_code, organization_id_fk) where event_id_fk is null;
create unique index "unique_promo_code_for_event" on promo_code(promo_code, event_id_fk) where event_id_fk is not null;
--

update promo_code set organization_id_fk = (select org_id from event where event.id = event_id_fk) where organization_id_fk is null;
alter table promo_code alter column organization_id_fk set not null;

alter table promo_code enable row level security;
alter table promo_code force row level security;
create policy promo_code_access_policy on promo_code to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));
--


--

alter table billing_document enable row level security;
alter table billing_document force row level security;
create policy billing_document_access_policy on billing_document to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));

-- tables where event_id_fk is present


-- trigger for tables where the event fk is named event_id
create or replace function set_organization_id_fk_from_event_id() returns trigger
as $$ begin
    if new.organization_id_fk is null then
      new.organization_id_fk = (select org_id from event where event.id = new.event_id);
    end if;
    return new;
end;
$$ language plpgsql;

-- trigger for tables where the event fk is named event_id_fk
create or replace function set_organization_id_fk_from_event_id_fk() returns trigger
as $$ begin
    if new.organization_id_fk is null then
      new.organization_id_fk = (select org_id from event where event.id = new.event_id_fk);
    end if;
    return new;
end;
$$ language plpgsql;


-- ticket table
alter table ticket add column organization_id_fk integer;
alter table ticket add foreign key(organization_id_fk) references organization(id);
update ticket set organization_id_fk = (select org_id from event where event.id = event_id);
alter table ticket alter column organization_id_fk set not null;

create trigger ticket_insert_org_id_fk_trigger
    before insert on ticket
    for each row execute procedure set_organization_id_fk_from_event_id();

alter table ticket enable row level security;
alter table ticket force row level security;
create policy ticket_access_policy on ticket to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select org_id from event where event.id = event_id)));
--

-- tickets_reservation table
alter table tickets_reservation add column organization_id_fk integer;
alter table tickets_reservation add foreign key(organization_id_fk) references organization(id);
update tickets_reservation set organization_id_fk = (select org_id from event where event.id = event_id_fk);
alter table tickets_reservation alter column organization_id_fk set not null;

create trigger tickets_reservation_insert_org_id_fk_trigger
    before insert on tickets_reservation
    for each row execute procedure set_organization_id_fk_from_event_id_fk();

alter table tickets_reservation enable row level security;
alter table tickets_reservation force row level security;
create policy tickets_reservation_access_policy on tickets_reservation to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select org_id from event where event.id = event_id_fk)));
--

-- email_message
alter table email_message add column organization_id_fk integer;
alter table email_message add foreign key(organization_id_fk) references organization(id);
update email_message set organization_id_fk = (select org_id from event where event.id = event_id);
alter table email_message alter column organization_id_fk set not null;

create trigger email_message_insert_org_id_fk_trigger
    before insert on email_message
    for each row execute procedure set_organization_id_fk_from_event_id();

alter table email_message enable row level security;
alter table email_message force row level security;
create policy email_message_access_policy on email_message to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select org_id from event where event.id = event_id)));
--

-- admin_reservation_request

delete from admin_reservation_request where (select count(*) from event where event.id = event_id) = 0;

alter table admin_reservation_request add column organization_id_fk integer;
alter table admin_reservation_request add foreign key(organization_id_fk) references organization(id);
update admin_reservation_request set organization_id_fk = (select org_id from event where event.id = event_id);
alter table admin_reservation_request alter column organization_id_fk set not null;

create trigger admin_reservation_request_insert_org_id_fk_trigger
    before insert on admin_reservation_request
    for each row execute procedure set_organization_id_fk_from_event_id();

alter table admin_reservation_request enable row level security;
alter table admin_reservation_request force row level security;
create policy admin_reservation_request_access_policy on admin_reservation_request to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select org_id from event where event.id = event_id)));
--

-- waiting_queue
alter table waiting_queue add column organization_id_fk integer;
alter table waiting_queue add foreign key(organization_id_fk) references organization(id);
update waiting_queue set organization_id_fk = (select org_id from event where event.id = event_id);
alter table waiting_queue alter column organization_id_fk set not null;

create trigger waiting_queue_insert_org_id_fk_trigger
    before insert on waiting_queue
    for each row execute procedure set_organization_id_fk_from_event_id();

alter table waiting_queue enable row level security;
alter table waiting_queue force row level security;
create policy waiting_queue_access_policy on waiting_queue to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select org_id from event where event.id = event_id)));
--


-- ticket_category
alter table ticket_category add column organization_id_fk integer;
alter table ticket_category add foreign key(organization_id_fk) references organization(id);
update ticket_category set organization_id_fk = (select org_id from event where event.id = event_id);
alter table ticket_category alter column organization_id_fk set not null;


create trigger ticket_category_insert_org_id_fk_trigger
    before insert on ticket_category
    for each row execute procedure set_organization_id_fk_from_event_id();

alter table ticket_category enable row level security;
alter table ticket_category force row level security;
create policy ticket_category_access_policy on ticket_category to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select org_id from event where event.id = event_id)));
--

-- ticket_field_configuration
alter table ticket_field_configuration add column organization_id_fk integer;
alter table ticket_field_configuration add foreign key(organization_id_fk) references organization(id);
update ticket_field_configuration set organization_id_fk = (select org_id from event where event.id = event_id_fk);
alter table ticket_field_configuration alter column organization_id_fk set not null;


create trigger ticket_field_configuration_insert_org_id_fk_trigger
    before insert on ticket_field_configuration
    for each row execute procedure set_organization_id_fk_from_event_id_fk();

alter table ticket_field_configuration enable row level security;
alter table ticket_field_configuration force row level security;
create policy ticket_field_configuration_access_policy on ticket_field_configuration to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select org_id from event where event.id = event_id_fk)));
--

-- scan_audit
alter table scan_audit add column organization_id_fk integer;
alter table scan_audit add foreign key(organization_id_fk) references organization(id);
update scan_audit set organization_id_fk = (select org_id from event where event.id = event_id_fk);
alter table scan_audit alter column organization_id_fk set not null;


create trigger scan_audit_insert_org_id_fk_trigger
    before insert on scan_audit
    for each row execute procedure set_organization_id_fk_from_event_id_fk();

alter table scan_audit enable row level security;
alter table scan_audit force row level security;
create policy scan_audit_access_policy on scan_audit to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select org_id from event where event.id = event_id_fk)));
--

-- auditing: note: the event_id can be null because we want to keep the auditing log when the event is removed
alter table auditing add column organization_id_fk integer;
alter table auditing add foreign key(organization_id_fk) references organization(id);
update auditing set organization_id_fk = (select org_id from event where event.id = event_id);


create trigger auditing_insert_org_id_fk_trigger
    before insert on auditing
    for each row execute procedure set_organization_id_fk_from_event_id();

alter table auditing enable row level security;
alter table auditing force row level security;
create policy auditing_access_policy on auditing to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select org_id from event where event.id = event_id)));
--

-- event_description_text
alter table event_description_text add column organization_id_fk integer;
alter table event_description_text add foreign key(organization_id_fk) references organization(id);
update event_description_text set organization_id_fk = (select org_id from event where event.id = event_id_fk);
alter table event_description_text alter column organization_id_fk set not null;


create trigger event_description_text_insert_org_id_fk_trigger
    before insert on event_description_text
    for each row execute procedure set_organization_id_fk_from_event_id_fk();

alter table event_description_text enable row level security;
alter table event_description_text force row level security;
create policy event_description_text_access_policy on event_description_text to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select org_id from event where event.id = event_id_fk)));
--

-- additional_service
alter table additional_service add column organization_id_fk integer;
alter table additional_service add foreign key(organization_id_fk) references organization(id);
update additional_service set organization_id_fk = (select org_id from event where event.id = event_id_fk);
alter table additional_service alter column organization_id_fk set not null;


create trigger additional_service_insert_org_id_fk_trigger
    before insert on additional_service
    for each row execute procedure set_organization_id_fk_from_event_id_fk();

alter table additional_service enable row level security;
alter table additional_service force row level security;
create policy additional_service_access_policy on additional_service to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select org_id from event where event.id = event_id_fk)));
--

-- additional_service_item
alter table additional_service_item add column organization_id_fk integer;
alter table additional_service_item add foreign key(organization_id_fk) references organization(id);
update additional_service_item set organization_id_fk = (select org_id from event where event.id = event_id_fk);
alter table additional_service_item alter column organization_id_fk set not null;


create trigger additional_service_item_insert_org_id_fk_trigger
    before insert on additional_service_item
    for each row execute procedure set_organization_id_fk_from_event_id_fk();

alter table additional_service_item enable row level security;
alter table additional_service_item force row level security;
create policy additional_service_item_access_policy on additional_service_item to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select org_id from event where event.id = event_id_fk)));
--

-- group_link
alter table group_link add column organization_id_fk integer;
alter table group_link add foreign key(organization_id_fk) references organization(id);
update group_link set organization_id_fk = (select org_id from event where event.id = event_id_fk);
alter table group_link alter column organization_id_fk set not null;


create trigger group_link_insert_org_id_fk_trigger
    before insert on group_link
    for each row execute procedure set_organization_id_fk_from_event_id_fk();

alter table group_link enable row level security;
alter table group_link force row level security;
create policy group_link_access_policy on group_link to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select org_id from event where event.id = event_id_fk)));
--

-- sponsor_scan
alter table sponsor_scan add column organization_id_fk integer;
alter table sponsor_scan add foreign key(organization_id_fk) references organization(id);
update sponsor_scan set organization_id_fk = (select org_id from event where event.id = event_id);
alter table sponsor_scan alter column organization_id_fk set not null;


create trigger sponsor_scan_insert_org_id_fk_trigger
    before insert on sponsor_scan
    for each row execute procedure set_organization_id_fk_from_event_id();

alter table sponsor_scan enable row level security;
alter table sponsor_scan force row level security;
create policy sponsor_scan_access_policy on sponsor_scan to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select org_id from event where event.id = event_id)));
--


-- additional_service_description -> additional_service_id_fk
alter table additional_service_description add column organization_id_fk integer;
alter table additional_service_description add foreign key(organization_id_fk) references organization(id);
update additional_service_description set organization_id_fk = (select additional_service.organization_id_fk from additional_service where additional_service.id = additional_service_id_fk);
alter table additional_service_description alter column organization_id_fk set not null;


create or replace function set_organization_id_fk_from_additional_service_id_fk() returns trigger
as $$ begin
    if new.organization_id_fk is null then
      new.organization_id_fk = (select additional_service.organization_id_fk from additional_service where additional_service.id = new.additional_service_id_fk);
    end if;
    return new;
end;
$$ language plpgsql;


create trigger additional_service_description_insert_org_id_fk_trigger
    before insert on additional_service_description
    for each row execute procedure set_organization_id_fk_from_additional_service_id_fk();

alter table additional_service_description enable row level security;
alter table additional_service_description force row level security;
create policy additional_service_description_access_policy on additional_service_description to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select additional_service.organization_id_fk from additional_service where additional_service.id = additional_service_id_fk)));
--

-- b_transaction -> reservation_id -> tickets_reservation.id
alter table b_transaction add column organization_id_fk integer;
alter table b_transaction add foreign key(organization_id_fk) references organization(id);
update b_transaction set organization_id_fk = (select tickets_reservation.organization_id_fk from tickets_reservation where tickets_reservation.id = reservation_id);
alter table b_transaction alter column organization_id_fk set not null;


create or replace function set_organization_id_fk_from_reservation_id() returns trigger
as $$ begin
    if new.organization_id_fk is null then
      new.organization_id_fk = (select tickets_reservation.organization_id_fk from tickets_reservation where tickets_reservation.id = new.reservation_id);
    end if;
    return new;
end;
$$ language plpgsql;


create trigger b_transaction_insert_org_id_fk_trigger
    before insert on b_transaction
    for each row execute procedure set_organization_id_fk_from_reservation_id();

alter table b_transaction enable row level security;
alter table b_transaction force row level security;
create policy b_transaction_access_policy on b_transaction to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select tickets_reservation.organization_id_fk from tickets_reservation where tickets_reservation.id = reservation_id)));
--

-- group_member -> a_group_id_fk -> a_group.organization_id_fk
alter table group_member add column organization_id_fk integer;
alter table group_member add foreign key(organization_id_fk) references organization(id);
update group_member set organization_id_fk = (select a_group.organization_id_fk from a_group where a_group.id = a_group_id_fk);
alter table group_member alter column organization_id_fk set not null;


create or replace function set_organization_id_fk_from_a_group_id_fk() returns trigger
as $$ begin
    if new.organization_id_fk is null then
      new.organization_id_fk = (select a_group.organization_id_fk from a_group where a_group.id = new.a_group_id_fk);
    end if;
    return new;
end;
$$ language plpgsql;

create trigger group_member_insert_org_id_fk_trigger
    before insert on group_member
    for each row execute procedure set_organization_id_fk_from_a_group_id_fk();

alter table group_member enable row level security;
alter table group_member force row level security;
create policy group_member_access_policy on group_member to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select a_group.organization_id_fk from a_group where a_group.id = a_group_id_fk)));
--

-- ticket_field_description -> ticket_field_configuration_id_fk -> ticket_field_configuration.organization_id_fk
alter table ticket_field_description add column organization_id_fk integer;
alter table ticket_field_description add foreign key(organization_id_fk) references organization(id);
update ticket_field_description set organization_id_fk = (select ticket_field_configuration.organization_id_fk from ticket_field_configuration where ticket_field_configuration.id = ticket_field_configuration_id_fk);
alter table ticket_field_description alter column organization_id_fk set not null;

create or replace function set_organization_id_fk_from_ticket_field_configuration_id_fk() returns trigger
as $$ begin
    if new.organization_id_fk is null then
      new.organization_id_fk = (select ticket_field_configuration.organization_id_fk from ticket_field_configuration where ticket_field_configuration.id = new.ticket_field_configuration_id_fk);
    end if;
    return new;
end;
$$ language plpgsql;

create trigger ticket_field_description_insert_org_id_fk_trigger
    before insert on ticket_field_description
    for each row execute procedure set_organization_id_fk_from_ticket_field_configuration_id_fk();

alter table ticket_field_description enable row level security;
alter table ticket_field_description force row level security;
create policy ticket_field_description_access_policy on ticket_field_description to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select ticket_field_configuration.organization_id_fk from ticket_field_configuration where ticket_field_configuration.id = ticket_field_configuration_id_fk)));
--

-- ticket_field_value -> ticket_field_configuration_id_fk -> ticket_field_configuration.organization_id_fk
alter table ticket_field_value add column organization_id_fk integer;
alter table ticket_field_value add foreign key(organization_id_fk) references organization(id);
update ticket_field_value set organization_id_fk = (select ticket_field_configuration.organization_id_fk from ticket_field_configuration where ticket_field_configuration.id = ticket_field_configuration_id_fk);
alter table ticket_field_value alter column organization_id_fk set not null;

create trigger ticket_field_value_insert_org_id_fk_trigger
    before insert on ticket_field_value
    for each row execute procedure set_organization_id_fk_from_ticket_field_configuration_id_fk();

alter table ticket_field_value enable row level security;
alter table ticket_field_value force row level security;
create policy ticket_field_value_access_policy on ticket_field_value to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select ticket_field_configuration.organization_id_fk from ticket_field_configuration where ticket_field_configuration.id = ticket_field_configuration_id_fk)));
--

-- ticket_category_text -> ticket_category_id_fk ->  ticket_category.organization_id_fk
alter table ticket_category_text add column organization_id_fk integer;
alter table ticket_category_text add foreign key(organization_id_fk) references organization(id);
update ticket_category_text set organization_id_fk = (select ticket_category.organization_id_fk from ticket_category where ticket_category.id = ticket_category_id_fk);
alter table ticket_category_text alter column organization_id_fk set not null;

create or replace function set_organization_id_fk_from_ticket_category_id_fk() returns trigger
as $$ begin
    if new.organization_id_fk is null then
      new.organization_id_fk = (select ticket_category.organization_id_fk from ticket_category where ticket_category.id = new.ticket_category_id_fk);
    end if;
    return new;
end;
$$ language plpgsql;


create trigger ticket_category_text_insert_org_id_fk_trigger
    before insert on ticket_category_text
    for each row execute procedure set_organization_id_fk_from_ticket_category_id_fk();

alter table ticket_category_text enable row level security;
alter table ticket_category_text force row level security;
create policy ticket_category_text_access_policy on ticket_category_text to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select ticket_category.organization_id_fk from ticket_category where ticket_category.id = ticket_category_id_fk)));
--

-- whitelisted_ticket -> group_member_id_fk -> group_member.organization_id_fk
alter table whitelisted_ticket add column organization_id_fk integer;
alter table whitelisted_ticket add foreign key(organization_id_fk) references organization(id);
update whitelisted_ticket set organization_id_fk = (select group_member.organization_id_fk from group_member where group_member.id = group_member_id_fk);
alter table whitelisted_ticket alter column organization_id_fk set not null;


create or replace function set_organization_id_fk_from_group_member_id_fk() returns trigger
as $$ begin
    if new.organization_id_fk is null then
      new.organization_id_fk = (select group_member.organization_id_fk from group_member where group_member.id = new.group_member_id_fk);
    end if;
    return new;
end;
$$ language plpgsql;

create trigger whitelisted_ticket_insert_org_id_fk_trigger
    before insert on whitelisted_ticket
    for each row execute procedure set_organization_id_fk_from_group_member_id_fk();

alter table whitelisted_ticket enable row level security;
alter table whitelisted_ticket force row level security;
create policy whitelisted_ticket_access_policy on whitelisted_ticket to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((select group_member.organization_id_fk from group_member where group_member.id = group_member_id_fk)));
--

-- special_price -> ticket_category_id -> ticket_category.organization_id_fk
alter table special_price add column organization_id_fk integer;
alter table special_price add foreign key(organization_id_fk) references organization(id);
update special_price set organization_id_fk = (select ticket_category.organization_id_fk from ticket_category where ticket_category.id = ticket_category_id);
--alter table special_price alter column organization_id_fk set not null;

create or replace function set_organization_id_fk_from_ticket_category_id() returns trigger
as $$ begin
    if new.organization_id_fk is null then
      new.organization_id_fk = (select ticket_category.organization_id_fk from ticket_category where ticket_category.id = new.ticket_category_id);
    end if;
    return new;
end;
$$ language plpgsql;


create trigger special_price_insert_org_id_fk_trigger
    before insert on special_price
    for each row execute procedure set_organization_id_fk_from_ticket_category_id();

alter table special_price enable row level security;
alter table special_price force row level security;
create policy special_price_access_policy on special_price to public
    using (organization_id_fk is null or alfio_check_row_access(organization_id_fk))
    with check (ticket_category_id is null or alfio_check_row_access((select ticket_category.organization_id_fk from ticket_category where ticket_category.id = ticket_category_id)));
--